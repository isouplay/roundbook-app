package io.github.isouplay.roundbook.domain.evening

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.isouplay.roundbook.domain.EveningId
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.db.RoundbookDatabase
import io.github.isouplay.roundbook.domain.event.EveningEnded
import io.github.isouplay.roundbook.domain.event.EveningStarted
import io.github.isouplay.roundbook.domain.event.EventLog
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.favorite.Favorites
import io.github.isouplay.roundbook.domain.place.Places
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Evening(
    val id: EveningId,
    val placeId: PlaceId,
    /** Milliseconds since 1970-01-01 UTC. */
    val startedAt: Long,
    /** Null while the evening runs. */
    val endedAt: Long?,
) {
    val isRunning: Boolean get() = endedAt == null
}

sealed interface StartResult {
    data class Started(val evening: Evening) : StartResult

    /** There is already a running evening; at most one runs. */
    data class AlreadyRunning(val evening: Evening) : StartResult

    data object UnknownPlace : StartResult
}

sealed interface EndResult {
    data class Ended(val evening: Evening) : EndResult

    /** The evening does not exist or has already ended. */
    data object NotRunning : EndResult
}

/**
 * Evenings at a place: begin, end, the running one. At most one evening runs at a time. Ending is the simple
 * form of package 3 (a button with a question); the bill with tip before ending comes in package 4.
 */
class Evenings internal constructor(
    private val database: RoundbookDatabase,
    private val eventLog: EventLog,
    private val places: Places,
    private val favorites: Favorites,
    private val newId: () -> String,
    private val readContext: CoroutineContext,
) {
    private val queries get() = database.eveningQueries

    fun running(): Evening? = queries.selectRunning(::eveningOf).executeAsOneOrNull()

    fun runningFlow(): Flow<Evening?> = queries.selectRunning(::eveningOf).asFlow().mapToOneOrNull(readContext)

    fun get(id: EveningId): Evening? = queries.selectById(id.value, ::eveningOf).executeAsOneOrNull()

    /** The place of the most recent evening, if it still exists – preselected when beginning an evening. */
    fun lastPlaceId(): PlaceId? {
        val latest = queries.selectLatest(::eveningOf).executeAsOneOrNull() ?: return null
        return latest.placeId.takeIf { places.get(it) != null }
    }

    /** Begins an evening at the place. The favourite row orders itself anew at the beginning of an evening. */
    fun start(placeId: PlaceId): StartResult {
        running()?.let { return StartResult.AlreadyRunning(it) }
        if (places.get(placeId) == null) return StartResult.UnknownPlace
        val id = EveningId(newId())
        eventLog.append(
            EventType.EVENING_STARTED,
            EveningStarted.serializer(),
            EveningStarted(eveningId = id, placeId = placeId, startedAt = eventLog.now()),
        )
        favorites.reorder()
        return StartResult.Started(get(id)!!)
    }

    fun end(id: EveningId): EndResult {
        val evening = get(id)
        if (evening == null || !evening.isRunning) return EndResult.NotRunning
        eventLog.append(EventType.EVENING_ENDED, EveningEnded.serializer(), EveningEnded(id, eventLog.now()))
        return EndResult.Ended(get(id)!!)
    }

    companion object {
        /** An evening that begins before this hour (local time) belongs to the day before, like the old app. */
        const val DAY_CUTOFF_HOUR: Int = 6

        /** The date an evening belongs to: the local date of its start, before [DAY_CUTOFF_HOUR] the day before. */
        @OptIn(ExperimentalTime::class)
        fun eveningDate(startedAt: Long, zone: TimeZone): LocalDate {
            val local = Instant.fromEpochMilliseconds(startedAt).toLocalDateTime(zone)
            return if (local.hour < DAY_CUTOFF_HOUR) local.date.minus(DatePeriod(days = 1)) else local.date
        }
    }
}

@Suppress("FunctionParameterNaming")
internal fun eveningOf(id: String, place_id: String, started_at: Long, ended_at: Long?): Evening = Evening(
    id = EveningId(id),
    placeId = PlaceId(place_id),
    startedAt = started_at,
    endedAt = ended_at,
)
