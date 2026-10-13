package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.event.PAYLOAD_VERSION
import io.github.isouplay.roundbook.domain.place.PlaceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventLogTest {

    @Test
    fun eventsGetDeviceRunningNumberTimeAndType() {
        val t = TestRoundbook()
        t.roundbook.places.create("Erfundener Keller")
        t.tick(500)
        val place = (t.roundbook.places.create("Zweiter Keller") as PlaceResult.Done).place

        val events = t.roundbook.eventLog.all()
        assertEquals(2, events.size)
        assertEquals(listOf(1L, 2L), events.map { it.sequence })
        assertEquals(listOf(DeviceId("device-a"), DeviceId("device-a")), events.map { it.deviceId })
        assertEquals(listOf(1_000_000L, 1_000_500L), events.map { it.recordedAt })
        assertEquals(EventType.PLACE_CREATED.code, events[1].type)
        assertEquals(PAYLOAD_VERSION, events[1].payloadVersion)
        assertEquals(true, place.id.value in events[1].payload, "the payload names the place")
        assertEquals(2L, t.roundbook.eventLog.count())
    }

    @Test
    fun timeNeverGoesBackBehindTheLastOwnEvent() {
        val t = TestRoundbook()
        t.roundbook.places.create("Eins")
        t.now -= 60_000 // clock set back
        t.roundbook.places.create("Zwei")
        val (first, second) = t.roundbook.eventLog.all()
        assertEquals(first.recordedAt, second.recordedAt)
        assertEquals(2L, second.sequence)
    }

    @Test
    fun deviceIdIsKeptAfterRestart() {
        val first = TestRoundbook(deviceName = "device-a")
        val restarted = Roundbook(first.driver, newId = { "device-b" })
        assertEquals(DeviceId("device-a"), restarted.deviceId)
    }

    @Test
    fun eventsAreOnlyAdded() {
        val t = TestRoundbook()
        val place = (t.roundbook.places.create("Alt") as PlaceResult.Done).place
        val created = t.roundbook.eventLog.all().single()
        t.tick()
        t.roundbook.places.rename(place.id, "Neu")

        val events = t.roundbook.eventLog.all()
        assertEquals(2, events.size)
        assertEquals(created, events[0], "the first event is unchanged")
        assertEquals(EventType.PLACE_RENAMED.code, events[1].type)
        assertEquals("Neu", t.roundbook.places.get(place.id)!!.name)
    }

    @Test
    fun knownEventsAreNotTakenTwice() {
        val t = TestRoundbook()
        t.roundbook.places.create("Eins")
        t.roundbook.eventLog.receive(t.roundbook.eventLog.all())
        assertEquals(1L, t.roundbook.eventLog.count())
        assertEquals(1, t.roundbook.places.all().size)
    }

    @Test
    fun eventsOfANewerAppAreKeptButNotShown() {
        val t = TestRoundbook()
        val foreign = io.github.isouplay.roundbook.domain.event.Event(
            id = EventId("future-1"),
            deviceId = DeviceId("device-z"),
            sequence = 1,
            recordedAt = 2_000_000L,
            type = "crew.joined",
            payload = """{"anything": 1}""",
            payloadVersion = 1,
        )
        t.roundbook.eventLog.receive(listOf(foreign))
        assertEquals(1L, t.roundbook.eventLog.count())
        assertEquals(0, t.roundbook.places.all().size)
        t.roundbook.eventLog.rebuildViews()
        assertEquals(0, t.roundbook.places.all().size)
    }
}
