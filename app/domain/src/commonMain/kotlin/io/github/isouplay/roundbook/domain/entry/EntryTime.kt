package io.github.isouplay.roundbook.domain.entry

import io.github.isouplay.roundbook.domain.evening.Evening
import io.github.isouplay.roundbook.domain.evening.Evenings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Times of entries: from hour and minute to a point in time within the evening. */
internal object EntryTime {

    fun floorToMinute(millis: Long): Long = millis - millis.mod(60_000L)

    /**
     * See [Entries.timeFor]. Candidates are the evening's date and the day after; the rule takes the evening's
     * date from the cutoff hour on and the day after before it. If the rule does not fit but the other day
     * does (an evening past the cutoff), the one that fits the evening is taken.
     */
    @OptIn(ExperimentalTime::class)
    fun timeFor(evening: Evening, hour: Int, minute: Int, now: Long, zone: TimeZone): TimeResult {
        val day = Evenings.eveningDate(evening.startedAt, zone)
        val time = LocalTime(hour, minute)
        val candidates = listOf(day, day.plus(DatePeriod(days = 1)))
            .map { LocalDateTime(it, time).toInstant(zone).toEpochMilliseconds() }
        val rule = candidates[if (hour >= Evenings.DAY_CUTOFF_HOUR) 0 else 1]
        val start = floorToMinute(evening.startedAt)
        val end = evening.endedAt ?: maxOf(now, evening.startedAt)
        val fitting = candidates.filter { it in start..end }
        if (fitting.isEmpty()) {
            return when {
                rule < start -> TimeResult.BeforeStart(start)
                evening.isRunning -> TimeResult.InFuture
                else -> TimeResult.AfterEnd(end)
            }
        }
        return TimeResult.At(if (rule in fitting) rule else fitting.first())
    }
}

/** Hour and minute of a point in time in [zone]. */
@OptIn(ExperimentalTime::class)
internal fun localHourMinute(millis: Long, zone: TimeZone): Pair<Int, Int> {
    val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return local.hour to local.minute
}
