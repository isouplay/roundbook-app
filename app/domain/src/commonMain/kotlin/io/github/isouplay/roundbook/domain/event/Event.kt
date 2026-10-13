package io.github.isouplay.roundbook.domain.event

import io.github.isouplay.roundbook.domain.DeviceId
import io.github.isouplay.roundbook.domain.EntryId
import io.github.isouplay.roundbook.domain.EveningId
import io.github.isouplay.roundbook.domain.EventId
import io.github.isouplay.roundbook.domain.MenuVersionId
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.entry.EntrySnapshot
import io.github.isouplay.roundbook.domain.place.PlaceKind
import io.github.isouplay.roundbook.core.menu.Menu
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One immutable entry of the event log. Every change is an event; events are never changed or
 * deleted (deleting something is an event itself). What the app shows follows from the events.
 */
data class Event(
    val id: EventId,
    val deviceId: DeviceId,
    /** Running number per device, starting at 1. */
    val sequence: Long,
    /** Time of recording, milliseconds since 1970-01-01 UTC. */
    val recordedAt: Long,
    /** [EventType.code]; kept as text so that events of a newer app survive in the log. */
    val type: String,
    /** Content as JSON. */
    val payload: String,
    /** Schema version of [payload]. */
    val payloadVersion: Int,
) {
    val position: EventPosition get() = EventPosition(recordedAt, deviceId.value, sequence)
}

/**
 * Position of an event in the event log, and with it the conflict rule: if two events concern the
 * same thing, the later one wins – later by time of recording; at the same time the greater device
 * id; on the same device the greater running number.
 */
data class EventPosition(
    val recordedAt: Long,
    val deviceId: String,
    val sequence: Long,
) : Comparable<EventPosition> {
    override fun compareTo(other: EventPosition): Int = compareValuesBy(
        this, other,
        { it.recordedAt },
        { it.deviceId },
        { it.sequence },
    )
}

/** Kinds of events. Later packages add more. */
enum class EventType(val code: String) {
    PLACE_CREATED("place.created"),
    PLACE_RENAMED("place.renamed"),
    MENU_IMPORTED("menu.imported"),
    EVENING_STARTED("evening.started"),
    EVENING_ENDED("evening.ended"),
    ENTRY_ADDED("entry.added"),
    ENTRY_CHANGED("entry.changed"),
    ENTRY_DELETED("entry.deleted"),
    ACTION_UNDONE("action.undone"),
    FAVORITE_PINNED("favorite.pinned"),
    FAVORITE_UNPINNED("favorite.unpinned"),
    ;

    companion object {
        fun fromCode(code: String): EventType? = entries.firstOrNull { it.code == code }
    }
}

/** Current schema version of all payloads. Raise per type when its payload changes. */
const val PAYLOAD_VERSION: Int = 1

@Serializable
data class PlaceCreated(
    val placeId: PlaceId,
    val name: String,
    val kind: PlaceKind,
)

@Serializable
data class PlaceRenamed(
    val placeId: PlaceId,
    val name: String,
)

/** A menu file was read: a new menu version of the place, valid from [validFrom] (ISO date). */
@Serializable
data class MenuImported(
    val placeId: PlaceId,
    val menuVersionId: MenuVersionId,
    val validFrom: String,
    val label: String,
    val menu: Menu,
)

/** An evening began at a place. */
@Serializable
data class EveningStarted(
    val eveningId: EveningId,
    val placeId: PlaceId,
    /** Milliseconds since 1970-01-01 UTC. */
    val startedAt: Long,
)

@Serializable
data class EveningEnded(
    val eveningId: EveningId,
    /** Milliseconds since 1970-01-01 UTC. */
    val endedAt: Long,
)

/** A drink was booked: what is needed for calculating later, as it was at the time of booking. */
@Serializable
data class EntryAdded(
    val entryId: EntryId,
    val eveningId: EveningId,
    /** When it was drunk, milliseconds since 1970-01-01 UTC. */
    val drunkAt: Long,
    val snapshot: EntrySnapshot,
)

/**
 * One or more entries were changed: another drink ([snapshot]) and/or another time ([drunkAt]), for all of
 * [entryIds]. What is null stays as it was.
 */
@Serializable
data class EntryChanged(
    val entryIds: List<EntryId>,
    val eveningId: EveningId,
    val snapshot: EntrySnapshot? = null,
    val drunkAt: Long? = null,
)

@Serializable
data class EntryDeleted(
    val entryId: EntryId,
    val eveningId: EveningId,
)

/** An own action (booking, deleting, changing) was taken back. The event [eventId] stays in the log. */
@Serializable
data class ActionUndone(
    val eventId: EventId,
)

/** [key] is a FavoriteKey.code: a drink id or "mix:[spirit id, mixer id]". */
@Serializable
data class FavoritePinned(
    val placeId: PlaceId,
    val key: String,
)

@Serializable
data class FavoriteUnpinned(
    val placeId: PlaceId,
    val key: String,
)

/** Kinds of events that are own actions on entries and can be taken back. */
internal val UNDOABLE_TYPES: List<EventType> = listOf(EventType.ENTRY_ADDED, EventType.ENTRY_CHANGED, EventType.ENTRY_DELETED)

/** JSON for payloads. Unknown keys are ignored (payloads written by a newer app). */
internal val EventJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
