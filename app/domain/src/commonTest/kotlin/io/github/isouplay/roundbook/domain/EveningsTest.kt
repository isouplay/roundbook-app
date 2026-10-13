package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.evening.EndResult
import io.github.isouplay.roundbook.domain.evening.Evenings
import io.github.isouplay.roundbook.domain.evening.StartResult
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.place.PlaceResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EveningsTest {

    @Test
    fun beginAndEndAnEvening() {
        val t = TestRoundbook(startTime = utc(2026, 10, 13, 20))
        val place = t.placeWithMenu()
        assertNull(t.roundbook.evenings.running())

        val evening = t.startEvening(place)
        assertEquals(place.id, evening.placeId)
        assertEquals(utc(2026, 10, 13, 20), evening.startedAt)
        assertTrue(evening.isRunning)
        assertEquals(evening, t.roundbook.evenings.running())

        t.tick(3 * HOUR)
        val ended = t.roundbook.evenings.end(evening.id)
        assertIs<EndResult.Ended>(ended)
        assertEquals(utc(2026, 10, 13, 23), ended.evening.endedAt)
        assertNull(t.roundbook.evenings.running())
        assertEquals(EndResult.NotRunning, t.roundbook.evenings.end(evening.id))

        val types = t.roundbook.eventLog.all().map { it.type }
        assertEquals(listOf(EventType.EVENING_STARTED.code, EventType.EVENING_ENDED.code), types.takeLast(2))
    }

    @Test
    fun atMostOneEveningRuns() {
        val t = TestRoundbook()
        val place = t.placeWithMenu()
        val other = (t.roundbook.places.create("Anderswo") as PlaceResult.Done).place
        val evening = t.startEvening(place)
        val events = t.roundbook.eventLog.count()
        assertEquals(StartResult.AlreadyRunning(evening), t.roundbook.evenings.start(other.id))
        assertEquals(events, t.roundbook.eventLog.count(), "nothing recorded")
        assertEquals(StartResult.UnknownPlace, TestRoundbook().roundbook.evenings.start(PlaceId("nirgends")))
    }

    @Test
    fun theLastPlaceIsPreselected() {
        val t = TestRoundbook()
        val first = t.placeWithMenu("Erste")
        val second = t.placeWithMenu("Zweite")
        assertNull(t.roundbook.evenings.lastPlaceId())
        t.roundbook.evenings.end(t.startEvening(second).id)
        t.tick()
        assertEquals(second.id, t.roundbook.evenings.lastPlaceId())
        t.startEvening(first)
        assertEquals(first.id, t.roundbook.evenings.lastPlaceId())
    }

    @Test
    fun anEveningBeforeSixInTheMorningBelongsToTheDayBefore() {
        assertEquals(LocalDate(2026, 10, 13), Evenings.eveningDate(utc(2026, 10, 13, 20), TimeZone.UTC))
        assertEquals(LocalDate(2026, 10, 13), Evenings.eveningDate(utc(2026, 10, 14, 5, 59), TimeZone.UTC))
        assertEquals(LocalDate(2026, 10, 14), Evenings.eveningDate(utc(2026, 10, 14, 6), TimeZone.UTC))
    }

    @Test
    fun eveningsSurviveARebuild() {
        val t = TestRoundbook()
        val place = t.placeWithMenu()
        val evening = t.startEvening(place)
        t.tick()
        t.roundbook.evenings.end(evening.id)
        val before = t.roundbook.evenings.get(evening.id)
        t.roundbook.eventLog.rebuildViews()
        assertEquals(before, t.roundbook.evenings.get(evening.id))
    }
}
