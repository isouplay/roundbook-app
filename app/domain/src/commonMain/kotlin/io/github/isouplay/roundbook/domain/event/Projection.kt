package io.github.isouplay.roundbook.domain.event

import io.github.isouplay.roundbook.core.menu.MenuJson
import io.github.isouplay.roundbook.domain.EntryId
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.entry.EntrySnapshot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Keeps the cached views (places, menu versions, evenings, entries, favourites) in line with the event log.
 * Two ways that must give the same result: [apply] event by event, and [rebuild] from the complete log.
 * Callers run both inside a transaction.
 *
 * Taking back an action ([ActionUndone]) builds the entries it touched anew from their own events,
 * leaving out every event that was taken back. So the view always equals the log without those events,
 * and nothing in the log is changed or deleted.
 */
internal class Projection(private val database: RoundbookDatabase) {

    fun apply(event: Event) {
        when (EventType.fromCode(event.type)) {
            EventType.PLACE_CREATED -> {
                val p = decodePayload(event, PlaceCreated.serializer()) ?: return
                // A second "created" for the same place changes nothing: the first one counts.
                database.placeQueries.insert(p.placeId.value, p.name, p.kind.name, event.recordedAt)
            }
            EventType.PLACE_RENAMED -> {
                val p = decodePayload(event, PlaceRenamed.serializer()) ?: return
                database.placeQueries.updateName(p.name, p.placeId.value)
            }
            EventType.MENU_IMPORTED -> {
                val p = decodePayload(event, MenuImported.serializer()) ?: return
                if (database.placeQueries.selectById(p.placeId.value).executeAsOneOrNull() == null) return
                database.menuVersionQueries.insert(
                    p.menuVersionId.value,
                    p.placeId.value,
                    p.validFrom,
                    p.label,
                    event.recordedAt,
                    event.deviceId.value,
                    event.sequence,
                    MenuJson.encode(p.menu),
                )
            }
            EventType.EVENING_STARTED -> {
                val p = decodePayload(event, EveningStarted.serializer()) ?: return
                if (database.placeQueries.selectById(p.placeId.value).executeAsOneOrNull() == null) return
                database.eveningQueries.insert(p.eveningId.value, p.placeId.value, p.startedAt)
            }
            EventType.EVENING_ENDED -> {
                val p = decodePayload(event, EveningEnded.serializer()) ?: return
                database.eveningQueries.updateEnded(p.endedAt, p.eveningId.value)
            }
            EventType.ENTRY_ADDED, EventType.ENTRY_CHANGED, EventType.ENTRY_DELETED -> {
                for (id in entryIdsOf(event)) database.entryQueries.linkEvent(id.value, event.id.value)
                applyToEntries(event, only = null)
            }
            EventType.ACTION_UNDONE -> {
                val p = decodePayload(event, ActionUndone.serializer()) ?: return
                val target = database.eventQueries.selectById(p.eventId.value, ::eventOf).executeAsOneOrNull() ?: return
                if (EventType.fromCode(target.type) !in UNDOABLE_TYPES) return
                database.entryQueries.markUndone(target.id.value, event.id.value)
                for (id in entryIdsOf(target)) rebuildEntry(id)
            }
            EventType.FAVORITE_PINNED -> {
                val p = decodePayload(event, FavoritePinned.serializer()) ?: return
                database.favoriteQueries.pin(p.placeId.value, p.key, event.recordedAt)
            }
            EventType.FAVORITE_UNPINNED -> {
                val p = decodePayload(event, FavoriteUnpinned.serializer()) ?: return
                database.favoriteQueries.unpin(p.placeId.value, p.key)
            }
            // An event of a newer app: it stays in the log, this app does not show it.
            null -> Unit
        }
    }

    fun rebuild(events: List<Event>) {
        database.favoriteQueries.deleteAll()
        database.entryQueries.deleteAllUndone()
        database.entryQueries.deleteAllLinks()
        database.entryQueries.deleteAll()
        database.eveningQueries.deleteAll()
        database.menuVersionQueries.deleteAll()
        database.placeQueries.deleteAll()
        events.sortedBy { it.position }.forEach(::apply)
    }

    /** Builds one entry anew from its events (in the order of the log), leaving out what was taken back. */
    private fun rebuildEntry(id: EntryId) {
        val q = database.entryQueries
        q.delete(id.value)
        for (e in q.eventsOfEntry(id.value, ::eventOf).executeAsList()) {
            if (q.countUndone(e.id.value).executeAsOne() == 0L) applyToEntries(e, only = id)
        }
    }

    /** The effect of an entry event on the entry view; with [only] just on that one entry. */
    private fun applyToEntries(event: Event, only: EntryId?) {
        val q = database.entryQueries
        when (EventType.fromCode(event.type)) {
            EventType.ENTRY_ADDED -> {
                val p = decodePayload(event, EntryAdded.serializer()) ?: return
                if (only != null && only != p.entryId) return
                if (database.eveningQueries.selectById(p.eveningId.value).executeAsOneOrNull() == null) return
                q.insert(p.entryId.value, p.eveningId.value, p.drunkAt, event.recordedAt, encodeSnapshot(p.snapshot))
            }
            EventType.ENTRY_CHANGED -> {
                val p = decodePayload(event, EntryChanged.serializer()) ?: return
                for (id in p.entryIds) {
                    if (only != null && only != id) continue
                    if (q.selectById(id.value).executeAsOneOrNull() == null) continue
                    p.snapshot?.let { q.updateSnapshot(encodeSnapshot(it), id.value) }
                    p.drunkAt?.let { q.updateDrunkAt(it, id.value) }
                }
            }
            EventType.ENTRY_DELETED -> {
                val p = decodePayload(event, EntryDeleted.serializer()) ?: return
                if (only != null && only != p.entryId) return
                q.delete(p.entryId.value)
            }
            else -> Unit
        }
    }
}

/** The entries an entry event concerns; empty for other events. */
internal fun entryIdsOf(event: Event): List<EntryId> = when (EventType.fromCode(event.type)) {
    EventType.ENTRY_ADDED -> listOfNotNull(decodePayload(event, EntryAdded.serializer())?.entryId)
    EventType.ENTRY_CHANGED -> decodePayload(event, EntryChanged.serializer())?.entryIds.orEmpty()
    EventType.ENTRY_DELETED -> listOfNotNull(decodePayload(event, EntryDeleted.serializer())?.entryId)
    else -> emptyList()
}

internal fun encodeSnapshot(snapshot: EntrySnapshot): String = EventJson.encodeToString(EntrySnapshot.serializer(), snapshot)

internal fun decodeSnapshot(text: String): EntrySnapshot = EventJson.decodeFromString(EntrySnapshot.serializer(), text)

/** Content of the event, or null if it comes from a newer schema version this app cannot read. */
internal fun <T> decodePayload(event: Event, serializer: KSerializer<T>): T? {
    if (event.payloadVersion > PAYLOAD_VERSION) return null
    return try {
        EventJson.decodeFromString(serializer, event.payload)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
