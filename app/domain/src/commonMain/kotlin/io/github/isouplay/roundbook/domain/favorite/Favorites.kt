package io.github.isouplay.roundbook.domain.favorite

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.isouplay.roundbook.core.menu.Drink
import io.github.isouplay.roundbook.core.menu.GermanOrder
import io.github.isouplay.roundbook.core.menu.Menu
import io.github.isouplay.roundbook.core.menu.MixOffer
import io.github.isouplay.roundbook.core.menu.generalMix
import io.github.isouplay.roundbook.core.menu.isMixable
import io.github.isouplay.roundbook.core.menu.mixOffer
import io.github.isouplay.roundbook.core.menu.mixSizeLabels
import io.github.isouplay.roundbook.core.menu.mixer
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.event.EventLog
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.event.FavoritePinned
import io.github.isouplay.roundbook.domain.event.FavoriteUnpinned
import io.github.isouplay.roundbook.domain.event.decodeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import io.github.isouplay.roundbook.domain.evening.Evenings
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime

/** One tile of the favourite row: a drink or a mix (spirit with mixer). */
sealed interface FavoriteItem {
    val key: FavoriteKey
    val pinned: Boolean

    /** Bookings in the last 90 days at this place, as counted when the row was last ordered. */
    val count: Int

    /** What the row sorts by when counts are equal. */
    val sortName: String

    data class OfDrink(
        val drink: Drink,
        override val pinned: Boolean,
        override val count: Int,
    ) : FavoriteItem {
        override val key: FavoriteKey get() = FavoriteKey.OfDrink(drink.id)
        override val sortName: String get() = drink.name
    }

    /**
     * A mix as one tile: the size is chosen when tapping. [offers] holds one mix size per size label, with the
     * price for this mixer – the same calculation as the sheet and the booking.
     */
    data class OfMix(
        val spirit: Drink,
        /** Null for "egal / anderes". */
        val mixer: Drink?,
        /** Name of the general mix ("mit Mischgetränk"), for a mix without mixer. */
        val generalMixName: String,
        val offers: List<MixOffer>,
        override val pinned: Boolean,
        override val count: Int,
    ) : FavoriteItem {
        override val key: FavoriteKey get() = FavoriteKey.OfMix(spirit.id, mixer?.id)

        // Only for sorting, like the name the old app sorted by ("Wodka mit Apfelsaft"); not shown.
        override val sortName: String get() = spirit.name + " " + (mixer?.let { "mit " + it.name } ?: generalMixName)
    }
}

/**
 * Favourites per place: pinned drinks and mixes, and the favourite row (konzept-auswahl-und-pinnwand.md,
 * part 3 and 4). Pinning and unpinning are events.
 *
 * The row orders itself only at the beginning of an evening and when the app was in the background for at
 * least an hour – not after each booking, so that no tile moves away under the thumb. Until then the counts
 * of the last ordering stay ([reorder]).
 */
class Favorites internal constructor(
    private val database: RoundbookDatabase,
    private val eventLog: EventLog,
    private val clock: () -> Long,
    private val today: () -> LocalDate,
    private val timeZone: () -> TimeZone,
    private val readContext: CoroutineContext,
) {
    private val queries get() = database.favoriteQueries

    /** Counts per place as of the last ordering of the row. */
    private val frozenCounts = mutableMapOf<PlaceId, Map<FavoriteKey, Int>>()
    private var backgroundSince: Long? = null
    private val orderState = MutableStateFlow(0)

    /** Rises each time the row orders itself anew; the screen then asks for the row again. */
    val order: StateFlow<Int> = orderState.asStateFlow()

    /** Pinned favourites of the place, in the order they were pinned. */
    fun pinned(placeId: PlaceId): List<FavoriteKey> =
        queries.selectByPlace(placeId.value).executeAsList().mapNotNull(FavoriteKey::parse)

    fun pinnedFlow(placeId: PlaceId): Flow<List<FavoriteKey>> =
        queries.selectByPlace(placeId.value).asFlow().mapToList(readContext).map { it.mapNotNull(FavoriteKey::parse) }

    fun isPinned(placeId: PlaceId, key: FavoriteKey): Boolean = key in pinned(placeId)

    /** Pins or unpins; returns whether it is pinned afterwards. */
    fun setPinned(placeId: PlaceId, key: FavoriteKey, pinned: Boolean): Boolean {
        if (isPinned(placeId, key) == pinned) return pinned
        if (pinned) {
            eventLog.append(EventType.FAVORITE_PINNED, FavoritePinned.serializer(), FavoritePinned(placeId, key.code))
        } else {
            eventLog.append(EventType.FAVORITE_UNPINNED, FavoriteUnpinned.serializer(), FavoriteUnpinned(placeId, key.code))
        }
        return pinned
    }

    fun toggle(placeId: PlaceId, key: FavoriteKey): Boolean = setPinned(placeId, key, !isPinned(placeId, key))

    /**
     * How often each drink and each mix was booked at the place in the last [DAYS] days (evening dates:
     * today and the 89 days before). Everything from the menu counts, also what does not count as a drink;
     * free entries do not.
     */
    @OptIn(ExperimentalTime::class)
    fun counts(placeId: PlaceId): Map<FavoriteKey, Int> {
        val zone = timeZone()
        val firstDay = today().minus(DatePeriod(days = DAYS - 1))
        // The evening date is the date of the start, before the cutoff hour the day before: an evening belongs
        // to [firstDay] or later exactly if it started at or after the cutoff hour of that day.
        val since = LocalDateTime(firstDay, LocalTime(Evenings.DAY_CUTOFF_HOUR, 0)).toInstant(zone).toEpochMilliseconds()
        val counts = mutableMapOf<FavoriteKey, Int>()
        for (text in database.entryQueries.selectAtPlaceSince(placeId.value, since).executeAsList()) {
            val key = decodeSnapshot(text).favoriteKey ?: continue
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }

    /** The favourite row of the place for its current [menu], ordered by the counts of the last ordering. */
    fun row(placeId: PlaceId, menu: Menu): List<FavoriteItem> {
        val counts = frozenCounts.getOrPut(placeId) { counts(placeId) }
        return rowOf(menu, pinned(placeId), counts)
    }

    /** Orders the row anew with the current counts (beginning of an evening, back after an hour). */
    fun reorder() {
        frozenCounts.clear()
        orderState.value += 1
    }

    /** The app went into the background. */
    fun movedToBackground() {
        backgroundSince = clock()
    }

    /** The app is back; after at least [REORDER_AFTER_BACKGROUND_MILLIS] the row orders itself anew. */
    fun cameToForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (clock() - since >= REORDER_AFTER_BACKGROUND_MILLIS) reorder()
    }

    companion object {
        /** Tiles in the row, pinned and recently ordered together. */
        const val ROW_MAX: Int = 8

        /** Bookings of how many days count for the order of the row. */
        const val DAYS: Int = 90

        const val REORDER_AFTER_BACKGROUND_MILLIS: Long = 60 * 60 * 1000L

        /**
         * The row: first what is pinned (drinks and mixes), then what was ordered recently but is not pinned.
         * Within each block by count (most first), equal counts by name (German order). What is not on the
         * menu (any more) is left out; nothing appears twice; at most [ROW_MAX] tiles together.
         */
        fun rowOf(menu: Menu, pinned: List<FavoriteKey>, counts: Map<FavoriteKey, Int>): List<FavoriteItem> {
            val seen = mutableSetOf<FavoriteKey>()
            val order = compareByDescending<FavoriteItem> { it.count }.thenComparator { a, b -> GermanOrder.compare(a.sortName, b.sortName) }
            val pinnedItems = pinned.mapNotNull { resolve(menu, it, true, counts[it] ?: 0) }
                .filter { seen.add(it.key) }
                .sortedWith(order)
            val recent = counts.filter { it.value > 0 }.keys
                .mapNotNull { resolve(menu, it, false, counts[it] ?: 0) }
                .filter { seen.add(it.key) }
                .sortedWith(order)
            return (pinnedItems + recent).take(ROW_MAX)
        }

        /** The tile for a key on this menu, or null if the drink or mixer is not on it (or hidden). */
        fun resolve(menu: Menu, key: FavoriteKey, pinned: Boolean, count: Int): FavoriteItem? = when (key) {
            is FavoriteKey.OfDrink -> menu.drink(key.drinkId)?.takeIf { !it.hidden }?.let { FavoriteItem.OfDrink(it, pinned, count) }
            is FavoriteKey.OfMix -> {
                val spirit = menu.drink(key.spiritId)?.takeIf { !it.hidden && menu.isMixable(it) }
                val mixer = key.mixerId?.let { menu.mixer(it) }
                val general = spirit?.let { menu.generalMix(it) }
                if (spirit == null || general == null || (key.mixerId != null && mixer == null)) {
                    null
                } else {
                    val offers = menu.mixSizeLabels(spirit).mapNotNull { menu.mixOffer(spirit, it, mixer?.name) }
                    if (offers.isEmpty()) null else FavoriteItem.OfMix(spirit, mixer, general.name, offers, pinned, count)
                }
            }
        }
    }
}
