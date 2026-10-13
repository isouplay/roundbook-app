package io.github.isouplay.roundbook.domain.entry

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.isouplay.roundbook.core.menu.DrinkId
import io.github.isouplay.roundbook.core.menu.MenuLimits
import io.github.isouplay.roundbook.core.menu.codePointLength
import io.github.isouplay.roundbook.core.menu.generalMix
import io.github.isouplay.roundbook.core.menu.isMixable
import io.github.isouplay.roundbook.core.menu.mixOffer
import io.github.isouplay.roundbook.core.menu.mixer
import io.github.isouplay.roundbook.domain.EntryId
import io.github.isouplay.roundbook.domain.EveningId
import io.github.isouplay.roundbook.domain.EventId
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.evening.Evening
import io.github.isouplay.roundbook.domain.evening.Evenings
import io.github.isouplay.roundbook.domain.event.ActionUndone
import io.github.isouplay.roundbook.domain.event.EntryAdded
import io.github.isouplay.roundbook.domain.event.EntryChanged
import io.github.isouplay.roundbook.domain.event.EntryDeleted
import io.github.isouplay.roundbook.domain.event.Event
import io.github.isouplay.roundbook.domain.event.EventLog
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.event.UNDOABLE_TYPES
import io.github.isouplay.roundbook.domain.event.decodePayload
import io.github.isouplay.roundbook.domain.event.decodeSnapshot
import io.github.isouplay.roundbook.domain.event.entryIdsOf
import io.github.isouplay.roundbook.domain.event.eventOf
import io.github.isouplay.roundbook.domain.menu.MenuVersion
import io.github.isouplay.roundbook.domain.menu.MenuVersions
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

/** One entry (booking) of an evening. */
data class Entry(
    val id: EntryId,
    val eveningId: EveningId,
    /** When it was drunk (can be corrected), milliseconds since 1970-01-01 UTC. */
    val drunkAt: Long,
    /** When it was booked. */
    val addedAt: Long,
    val snapshot: EntrySnapshot,
)

/** What is booked. */
sealed interface DrinkChoice {
    /** A drink of the menu in one of its sizes (by label). */
    data class FromMenu(val drinkId: DrinkId, val sizeLabel: String) : DrinkChoice

    /** A spirit as a mix of its tier, at a mix size, with a mixer of the menu (null: "egal / anderes"). */
    data class AsMix(val spiritId: DrinkId, val sizeLabel: String, val mixerId: DrinkId?) : DrinkChoice

    /** A free entry without menu: name, amount, strength and price by hand. */
    data class Free(
        val name: String,
        val liters: Double?,
        val abv: Double?,
        val priceCents: Long,
        val countsAsDrink: Boolean,
    ) : DrinkChoice
}

enum class BookProblem {
    /** The evening does not exist or has ended. */
    NOT_RUNNING,
    NO_MENU,
    UNKNOWN_DRINK,
    UNKNOWN_SIZE,
    NOT_MIXABLE,
    UNKNOWN_MIXER,
    NAME_EMPTY,
    NAME_TOO_LONG,
    PRICE_OUT_OF_RANGE,
    VOLUME_OUT_OF_RANGE,
    ABV_OUT_OF_RANGE,
}

sealed interface BookResult {
    data class Booked(val entry: Entry) : BookResult

    data class Rejected(val problem: BookProblem) : BookResult
}

/** A time for an entry from hour and minute, see [Entries.timeFor]. */
sealed interface TimeResult {
    data class At(val drunkAt: Long) : TimeResult

    /** The same hour and minute as before: nothing to change. */
    data object Unchanged : TimeResult

    data class BeforeStart(val startedAt: Long) : TimeResult

    data object InFuture : TimeResult

    data class AfterEnd(val endedAt: Long) : TimeResult
}

enum class ChangeProblem {
    UNKNOWN_ENTRY,
    /** The entries belong to different evenings. */
    DIFFERENT_EVENINGS,
    TIME_OUT_OF_EVENING,
}

sealed interface ChangeResult {
    data class Changed(val count: Int, val drinkChanged: Boolean, val drunkAt: Long?) : ChangeResult

    data object Unchanged : ChangeResult

    data class Rejected(val problem: ChangeProblem) : ChangeResult

    data class RejectedDrink(val problem: BookProblem) : ChangeResult
}

enum class ActionKind { BOOKED, CHANGED, DELETED }

/** The newest own action that can still be taken back. */
data class UndoInfo(
    val eventId: EventId,
    val kind: ActionKind,
    val recordedAt: Long,
)

/** What was taken back: the kind of action, how many entries it touched and one of them (for the name). */
data class UndoResult(
    val kind: ActionKind,
    val count: Int,
    val snapshot: EntrySnapshot?,
)

/** Entries of one evening with the same drink, size and price ("3× Spezi 0,4 l"). */
data class EntryGroup(
    /** The entries, earliest drunk first. */
    val entries: List<Entry>,
) {
    val count: Int get() = entries.size

    /** The entry booked last; its name is shown (the newest name, if the menu renamed the drink). */
    val newest: Entry get() = entries.maxWith(compareBy<Entry>({ it.addedAt }, { it.id.value }))

    /** The entry drunk last; deleting from the group removes this one. */
    val youngest: Entry get() = entries.maxWith(compareBy<Entry>({ it.drunkAt }, { it.addedAt }, { it.id.value }))

    val snapshot: EntrySnapshot get() = newest.snapshot

    val totalCents: Long get() = entries.sumOf { it.snapshot.priceCents }

    val ids: List<EntryId> get() = entries.map { it.id }
}

/** Head of the screen: sum of the evening and number of drinks (only what counts as a drink). */
data class EveningSummary(
    val totalCents: Long,
    val drinkCount: Int,
    val entryCount: Int,
)

/**
 * The entries of the evenings: book (from the menu, as a mix, on the house, free), change (drink, time),
 * delete, take back. Every change is an event; taking back is an event too.
 */
class Entries internal constructor(
    private val database: RoundbookDatabase,
    private val eventLog: EventLog,
    private val evenings: Evenings,
    private val menus: MenuVersions,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val readContext: CoroutineContext,
) {
    private val queries get() = database.entryQueries

    fun get(id: EntryId): Entry? = queries.selectById(id.value, ::entryOf).executeAsOneOrNull()

    /** The entries of the evening, earliest drunk first. */
    fun ofEvening(id: EveningId): List<Entry> = queries.selectByEvening(id.value, ::entryOf).executeAsList()

    fun ofEveningFlow(id: EveningId): Flow<List<Entry>> =
        queries.selectByEvening(id.value, ::entryOf).asFlow().mapToList(readContext)

    /**
     * Books a drink into a running evening. Name, size, price, strength and the rest are taken from the
     * current menu of the place as a snapshot. [onTheHouse]: a drink of the menu that costs nothing.
     */
    fun book(eveningId: EveningId, choice: DrinkChoice, onTheHouse: Boolean = false): BookResult {
        val evening = evenings.get(eveningId)
        if (evening == null || !evening.isRunning) return BookResult.Rejected(BookProblem.NOT_RUNNING)
        val snapshot = when (val s = snapshotOf(evening, choice, onTheHouse)) {
            is SnapshotResult.Ok -> s.snapshot
            is SnapshotResult.Problem -> return BookResult.Rejected(s.problem)
        }
        val id = EntryId(newId())
        eventLog.append(
            EventType.ENTRY_ADDED,
            EntryAdded.serializer(),
            EntryAdded(entryId = id, eveningId = eveningId, drunkAt = eventLog.now(), snapshot = snapshot),
        )
        return BookResult.Booked(get(id)!!)
    }

    /** Deletes one entry (no question asked – there is "Zurück"). */
    fun delete(id: EntryId): Boolean {
        val entry = get(id) ?: return false
        eventLog.append(EventType.ENTRY_DELETED, EntryDeleted.serializer(), EntryDeleted(id, entry.eveningId))
        return true
    }

    /**
     * Changes entries: another drink ([choice]) and/or another time ([drunkAt]), for all of [ids] – a whole
     * group or one entry. The same drink with another time changes only the time and keeps the price. On the
     * house stays on the house unless [onTheHouse] says otherwise; a free entry is never on the house.
     */
    fun change(ids: List<EntryId>, choice: DrinkChoice? = null, drunkAt: Long? = null, onTheHouse: Boolean? = null): ChangeResult {
        val targets = ids.distinct().map { get(it) ?: return ChangeResult.Rejected(ChangeProblem.UNKNOWN_ENTRY) }
        if (targets.isEmpty()) return ChangeResult.Rejected(ChangeProblem.UNKNOWN_ENTRY)
        val eveningId = targets.first().eveningId
        if (targets.any { it.eveningId != eveningId }) return ChangeResult.Rejected(ChangeProblem.DIFFERENT_EVENINGS)
        val evening = evenings.get(eveningId) ?: return ChangeResult.Rejected(ChangeProblem.UNKNOWN_ENTRY)
        val first = targets.first()

        val newTime = drunkAt?.takeIf { time -> targets.any { it.drunkAt != time } }
        if (newTime != null && !fitsEvening(evening, newTime)) return ChangeResult.Rejected(ChangeProblem.TIME_OUT_OF_EVENING)

        var snapshot: EntrySnapshot? = null
        if (choice != null) {
            val house = if (choice is DrinkChoice.Free) false else onTheHouse ?: first.snapshot.onTheHouse
            snapshot = when (val s = snapshotOf(evening, choice, house)) {
                is SnapshotResult.Ok -> s.snapshot
                is SnapshotResult.Problem -> return ChangeResult.RejectedDrink(s.problem)
            }
            // The same drink with another time: only the time changes, the price stays as it was.
            if (newTime != null && onTheHouse == null && sameDrink(snapshot, first.snapshot)) snapshot = null
            if (snapshot != null && targets.all { it.snapshot == snapshot }) snapshot = null
        }
        if (snapshot == null && newTime == null) return ChangeResult.Unchanged
        eventLog.append(
            EventType.ENTRY_CHANGED,
            EntryChanged.serializer(),
            EntryChanged(entryIds = targets.map { it.id }, eveningId = eveningId, snapshot = snapshot, drunkAt = newTime),
        )
        return ChangeResult.Changed(targets.size, drinkChanged = snapshot != null, drunkAt = newTime)
    }

    /**
     * The time for an entry from [hour] and [minute] in [zone], like the old app: the date comes from the
     * evening – an hour before the day cutoff (6 o'clock) belongs to the day after the evening's date. It must
     * lie between the beginning of the evening (to the minute) and now (running evening) or its end. Outside
     * it is rejected, not moved: whoever types 23:00 and gets 22:00 does not notice.
     */
    fun timeFor(entry: Entry, hour: Int, minute: Int, zone: kotlinx.datetime.TimeZone): TimeResult {
        val evening = evenings.get(entry.eveningId) ?: return TimeResult.InFuture
        val old = localHourMinute(entry.drunkAt, zone)
        if (old == hour to minute) return TimeResult.Unchanged
        return EntryTime.timeFor(evening, hour, minute, now = clock(), zone = zone)
    }

    /** The newest own action of this evening that is at most [UNDO_WINDOW_MILLIS] old and not taken back yet. */
    fun undoable(eveningId: EveningId): UndoInfo? {
        val since = clock() - UNDO_WINDOW_MILLIS
        val candidates = database.eventQueries.undoCandidates(
            deviceId = eventLog.deviceId.value,
            types = UNDOABLE_TYPES.map { it.code },
            since = since,
            mapper = ::eventOf,
        ).executeAsList()
        val event = candidates.firstOrNull { eveningOf(it) == eveningId } ?: return null
        return UndoInfo(event.id, kindOf(event), event.recordedAt)
    }

    /** Takes back the newest own action of this evening (see [undoable]); null if there is none. */
    fun undo(eveningId: EveningId): UndoResult? {
        val info = undoable(eveningId) ?: return null
        val target = eventLog.get(info.eventId) ?: return null
        val ids = entryIdsOf(target)
        val before = ids.firstNotNullOfOrNull { get(it) }?.snapshot
        eventLog.append(EventType.ACTION_UNDONE, ActionUndone.serializer(), ActionUndone(info.eventId))
        val after = ids.firstNotNullOfOrNull { get(it) }?.snapshot
        // A deleted entry has its name only after it is back; the others before.
        val snapshot = if (info.kind == ActionKind.DELETED) after ?: before else before ?: after
        return UndoResult(info.kind, ids.size, snapshot)
    }

    private fun kindOf(event: Event): ActionKind = when (EventType.fromCode(event.type)) {
        EventType.ENTRY_ADDED -> ActionKind.BOOKED
        EventType.ENTRY_DELETED -> ActionKind.DELETED
        else -> ActionKind.CHANGED
    }

    private fun eveningOf(event: Event): EveningId? = when (EventType.fromCode(event.type)) {
        EventType.ENTRY_ADDED -> decodePayload(event, EntryAdded.serializer())?.eveningId
        EventType.ENTRY_CHANGED -> decodePayload(event, EntryChanged.serializer())?.eveningId
        EventType.ENTRY_DELETED -> decodePayload(event, EntryDeleted.serializer())?.eveningId
        else -> null
    }

    private fun fitsEvening(evening: Evening, time: Long): Boolean {
        val start = EntryTime.floorToMinute(evening.startedAt)
        val end = evening.endedAt ?: maxOf(clock(), evening.startedAt)
        return time in start..end
    }

    private sealed interface SnapshotResult {
        data class Ok(val snapshot: EntrySnapshot) : SnapshotResult

        data class Problem(val problem: BookProblem) : SnapshotResult
    }

    private fun snapshotOf(evening: Evening, choice: DrinkChoice, onTheHouse: Boolean): SnapshotResult {
        if (choice is DrinkChoice.Free) return freeSnapshot(choice)
        val version = menus.current(evening.placeId) ?: return SnapshotResult.Problem(BookProblem.NO_MENU)
        return when (choice) {
            is DrinkChoice.FromMenu -> menuSnapshot(version, choice, onTheHouse)
            is DrinkChoice.AsMix -> mixSnapshot(version, choice, onTheHouse)
            is DrinkChoice.Free -> freeSnapshot(choice)
        }
    }

    private fun menuSnapshot(version: MenuVersion, choice: DrinkChoice.FromMenu, onTheHouse: Boolean): SnapshotResult {
        val menu = version.menu
        val drink = menu.drink(choice.drinkId)?.takeIf { !it.hidden }
            ?: return SnapshotResult.Problem(BookProblem.UNKNOWN_DRINK)
        val category = menu.category(drink.categoryId) ?: return SnapshotResult.Problem(BookProblem.UNKNOWN_DRINK)
        val size = drink.sizes.firstOrNull { it.label == choice.sizeLabel }
            ?: return SnapshotResult.Problem(BookProblem.UNKNOWN_SIZE)
        return SnapshotResult.Ok(
            EntrySnapshot(
                name = drink.name,
                sizeLabel = size.label,
                liters = size.liters,
                priceCents = if (onTheHouse) 0L else size.priceCents,
                currency = menu.currency,
                abv = drink.abv,
                countsAsDrink = category.countsAsDrink,
                ex = drink.ex,
                drinkId = drink.id,
                categoryId = drink.categoryId,
                menuVersionId = version.info.id,
                onTheHouse = onTheHouse,
            ),
        )
    }

    private fun mixSnapshot(version: MenuVersion, choice: DrinkChoice.AsMix, onTheHouse: Boolean): SnapshotResult {
        val menu = version.menu
        val spirit = menu.drink(choice.spiritId)?.takeIf { !it.hidden }
            ?: return SnapshotResult.Problem(BookProblem.UNKNOWN_DRINK)
        val category = menu.category(spirit.categoryId) ?: return SnapshotResult.Problem(BookProblem.UNKNOWN_DRINK)
        if (!menu.isMixable(spirit)) return SnapshotResult.Problem(BookProblem.NOT_MIXABLE)
        val mixer = choice.mixerId?.let { menu.mixer(it) ?: return SnapshotResult.Problem(BookProblem.UNKNOWN_MIXER) }
        val offer = menu.mixOffer(spirit, choice.sizeLabel, mixer?.name)
            ?: return SnapshotResult.Problem(BookProblem.UNKNOWN_SIZE)
        val tierId = spirit.mixTierId ?: return SnapshotResult.Problem(BookProblem.NOT_MIXABLE)
        return SnapshotResult.Ok(
            EntrySnapshot(
                name = spirit.name,
                sizeLabel = offer.size.label,
                liters = offer.size.liters,
                priceCents = if (onTheHouse) 0L else offer.size.priceCents,
                currency = menu.currency,
                abv = spirit.abv,
                spiritCl = offer.size.spiritCl,
                countsAsDrink = category.countsAsDrink,
                ex = false,
                drinkId = spirit.id,
                categoryId = spirit.categoryId,
                mixerId = mixer?.id,
                mixerName = mixer?.name,
                mixTierId = tierId,
                mixId = offer.mix.id,
                // Without mixer the entry is named after the general mix ("mit Mischgetränk"), like the old app.
                mixName = if (mixer == null) (menu.generalMix(spirit) ?: offer.mix).name else offer.mix.name,
                menuVersionId = version.info.id,
                onTheHouse = onTheHouse,
            ),
        )
    }

    private fun freeSnapshot(choice: DrinkChoice.Free): SnapshotResult {
        val name = choice.name.trim()
        val problem = when {
            name.isEmpty() -> BookProblem.NAME_EMPTY
            name.codePointLength() > MenuLimits.NAME_MAX -> BookProblem.NAME_TOO_LONG
            choice.priceCents < 0 || choice.priceCents > FREE_PRICE_MAX_CENTS -> BookProblem.PRICE_OUT_OF_RANGE
            choice.liters != null && (choice.liters < 0.0 || choice.liters > FREE_LITERS_MAX) -> BookProblem.VOLUME_OUT_OF_RANGE
            choice.abv != null && (choice.abv < 0.0 || choice.abv > MenuLimits.ABV_MAX) -> BookProblem.ABV_OUT_OF_RANGE
            else -> null
        }
        if (problem != null) return SnapshotResult.Problem(problem)
        val liters = choice.liters?.takeIf { it > 0.0 }
        return SnapshotResult.Ok(
            EntrySnapshot(
                name = name,
                sizeLabel = "",
                liters = liters,
                priceCents = choice.priceCents,
                // Strength only counts with an amount, like the old app.
                abv = if (liters != null) choice.abv ?: 0.0 else 0.0,
                countsAsDrink = choice.countsAsDrink,
            ),
        )
    }

    companion object {
        /** How long an own action can be taken back with "Zurück". */
        const val UNDO_WINDOW_MILLIS: Long = 5 * 60 * 1000L

        /** Highest price of a free entry, like a price on the menu (MenuLimits.PRICE_MAX). */
        const val FREE_PRICE_MAX_CENTS: Long = 999_999L

        /** Largest amount of a free entry: 5 l, like the old app. */
        const val FREE_LITERS_MAX: Double = 5.0

        /** Entries of one evening grouped by drink, size and price, in the order of the first booking. */
        fun groups(entries: List<Entry>): List<EntryGroup> {
            val byKey = LinkedHashMap<GroupKey, MutableList<Entry>>()
            for (e in entries.sortedWith(compareBy<Entry>({ it.drunkAt }, { it.addedAt }, { it.id.value }))) {
                byKey.getOrPut(groupKeyOf(e.snapshot)) { mutableListOf() }.add(e)
            }
            return byKey.values.map { EntryGroup(it) }
        }

        fun summary(entries: List<Entry>): EveningSummary = EveningSummary(
            totalCents = entries.sumOf { it.snapshot.priceCents },
            drinkCount = entries.count { it.snapshot.countsAsDrink },
            entryCount = entries.size,
        )

        /** How often each drink was booked ("heute schon x-mal"), by its id; mixes count for their spirit. */
        fun countsByDrink(entries: List<Entry>): Map<DrinkId, Int> =
            entries.mapNotNull { it.snapshot.drinkId }.groupingBy { it }.eachCount()

        private fun groupKeyOf(s: EntrySnapshot): GroupKey = GroupKey(
            drink = s.drinkId?.let { "id:${it.value}" } ?: "name:${s.name}",
            mixerId = s.mixerId?.value,
            mixId = s.mixId?.value,
            sizeLabel = s.sizeLabel,
            liters = if (s.isFree) s.liters else null,
            priceCents = s.priceCents,
            onTheHouse = s.onTheHouse,
        )

        private fun sameDrink(a: EntrySnapshot, b: EntrySnapshot): Boolean =
            a.drinkId != null && a.drinkId == b.drinkId && a.mixerId == b.mixerId && a.mixId == b.mixId &&
                a.sizeLabel == b.sizeLabel && a.onTheHouse == b.onTheHouse
    }
}

private data class GroupKey(
    val drink: String,
    val mixerId: String?,
    val mixId: String?,
    val sizeLabel: String,
    val liters: Double?,
    val priceCents: Long,
    val onTheHouse: Boolean,
)

@Suppress("FunctionParameterNaming")
internal fun entryOf(id: String, evening_id: String, drunk_at: Long, added_at: Long, snapshot: String): Entry = Entry(
    id = EntryId(id),
    eveningId = EveningId(evening_id),
    drunkAt = drunk_at,
    addedAt = added_at,
    snapshot = decodeSnapshot(snapshot),
)
