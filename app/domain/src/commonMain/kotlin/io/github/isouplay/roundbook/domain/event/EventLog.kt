package io.github.isouplay.roundbook.domain.event

import io.github.isouplay.roundbook.domain.DeviceId
import io.github.isouplay.roundbook.domain.EventId
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import kotlinx.serialization.KSerializer

/**
 * The event log of this device. Writing an event and updating the cached views happen in one
 * transaction. Events are only ever added.
 */
class EventLog internal constructor(
    private val database: RoundbookDatabase,
    private val projection: Projection,
    val deviceId: DeviceId,
    private val clock: () -> Long,
    private val newId: () -> String,
) {
    private val queries get() = database.eventQueries

    /**
     * Records a new event of this device. The running number continues the last one of this device;
     * the time never goes back behind the last own event (a clock set back would otherwise put the
     * new event before older ones).
     */
    internal fun <T> append(type: EventType, serializer: KSerializer<T>, payload: T): Event =
        database.transactionWithResult {
            val last = queries.lastOfDevice(deviceId.value, ::eventOf).executeAsOneOrNull()
            val event = Event(
                id = EventId(newId()),
                deviceId = deviceId,
                sequence = (last?.sequence ?: 0L) + 1,
                recordedAt = maxOf(clock(), last?.recordedAt ?: Long.MIN_VALUE),
                type = type.code,
                payload = EventJson.encodeToString(serializer, payload),
                payloadVersion = PAYLOAD_VERSION,
            )
            insertAndApply(event)
            event
        }

    /**
     * Takes events from elsewhere (later: other devices of the user), in any order. Known events are
     * skipped. The result is the same as if all events had been there from the start.
     */
    fun receive(events: List<Event>) {
        database.transaction {
            for (event in events) {
                if (queries.countById(event.id.value).executeAsOne() == 0L) insertAndApply(event)
            }
        }
    }

    /** All events in the order of the log (see [EventPosition]). */
    fun all(): List<Event> = queries.selectAll(::eventOf).executeAsList()

    fun count(): Long = queries.countAll().executeAsOne()

    /** Builds all cached views anew from the complete event log. */
    fun rebuildViews() {
        database.transaction { projection.rebuild(all()) }
    }

    /**
     * Inserts the event and updates the views: step by step if it is the newest event, otherwise
     * (an older event arrives late) everything is built anew, so the order of the log decides.
     */
    private fun insertAndApply(event: Event) {
        val p = event.position
        val later = queries.countAfter(p.recordedAt, p.deviceId, p.sequence).executeAsOne()
        queries.insert(
            event.id.value,
            event.deviceId.value,
            event.sequence,
            event.recordedAt,
            event.type,
            event.payload,
            event.payloadVersion.toLong(),
        )
        if (later > 0L) projection.rebuild(all()) else projection.apply(event)
    }
}

@Suppress("LocalVariableName", "FunctionParameterNaming")
internal fun eventOf(
    id: String,
    device_id: String,
    sequence: Long,
    recorded_at: Long,
    type: String,
    payload: String,
    payload_version: Long,
): Event = Event(
    id = EventId(id),
    deviceId = DeviceId(device_id),
    sequence = sequence,
    recordedAt = recorded_at,
    type = type,
    payload = payload,
    payloadVersion = payload_version.toInt(),
)
