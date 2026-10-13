package io.github.isouplay.roundbook.domain.event

import io.github.isouplay.roundbook.core.menu.MenuJson
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Keeps the cached views (places, menu versions) in line with the event log. Two ways that must
 * give the same result: [apply] event by event, and [rebuild] from the complete log.
 * Callers run both inside a transaction.
 */
internal class Projection(private val database: RoundbookDatabase) {

    fun apply(event: Event) {
        when (EventType.fromCode(event.type)) {
            EventType.PLACE_CREATED -> {
                val p = decode(event, PlaceCreated.serializer()) ?: return
                // A second "created" for the same place changes nothing: the first one counts.
                database.placeQueries.insert(p.placeId.value, p.name, p.kind.name, event.recordedAt)
            }
            EventType.PLACE_RENAMED -> {
                val p = decode(event, PlaceRenamed.serializer()) ?: return
                database.placeQueries.updateName(p.name, p.placeId.value)
            }
            EventType.MENU_IMPORTED -> {
                val p = decode(event, MenuImported.serializer()) ?: return
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
            // An event of a newer app: it stays in the log, this app does not show it.
            null -> Unit
        }
    }

    fun rebuild(events: List<Event>) {
        database.menuVersionQueries.deleteAll()
        database.placeQueries.deleteAll()
        events.sortedBy { it.position }.forEach(::apply)
    }

    /** Content of the event, or null if it comes from a newer schema version this app cannot read. */
    private fun <T> decode(event: Event, serializer: KSerializer<T>): T? {
        if (event.payloadVersion > PAYLOAD_VERSION) return null
        return try {
            EventJson.decodeFromString(serializer, event.payload)
        } catch (e: SerializationException) {
            null
        }
    }
}
