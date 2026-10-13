package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.event.Event
import io.github.isouplay.roundbook.domain.menu.MenuVersion
import io.github.isouplay.roundbook.domain.menu.MenuVersionInfo
import io.github.isouplay.roundbook.domain.place.Place
import io.github.isouplay.roundbook.domain.place.PlaceResult
import kotlin.test.Test
import kotlin.test.assertEquals

/** Event by event and rebuilt from the complete log must give the same views. */
class ProjectionTest {

    private data class Snapshot(
        val places: List<Place>,
        val versions: Map<PlaceId, List<MenuVersionInfo>>,
        val current: Map<PlaceId, MenuVersion?>,
    )

    private fun snapshot(r: Roundbook): Snapshot {
        val places = r.places.all()
        return Snapshot(
            places = places,
            versions = places.associate { it.id to r.menus.versions(it.id) },
            current = places.associate { it.id to r.menus.current(it.id) },
        )
    }

    private fun Roundbook.create(name: String): Place = (places.create(name) as PlaceResult.Done).place

    @Test
    fun stepByStepEqualsRebuild() {
        val t = TestRoundbook()
        val r = t.roundbook
        val keller = r.create("Keller")
        t.tick()
        val garten = r.create("Garten")
        t.tick()
        r.places.rename(keller.id, "Erfundener Keller")
        t.tick()
        r.menus.importMenu(keller.id, krugFile("2026-10-01", 450))
        t.tick()
        r.menus.importMenu(keller.id, krugFile("2027-03-01", 480))
        t.tick()
        r.menus.importMenu(garten.id, krugFile(null, 390, label = null))
        r.menus.importMenu(garten.id, "kein JSON")

        val stepByStep = snapshot(r)
        r.eventLog.rebuildViews()
        assertEquals(stepByStep, snapshot(r))
        assertEquals(2, stepByStep.places.size)
        assertEquals(2, stepByStep.versions.getValue(keller.id).size)
    }

    @Test
    fun eventsInAnyOrderGiveTheSameResult() {
        val a = TestRoundbook(deviceName = "device-a", startTime = 1_000L)
        val b = TestRoundbook(deviceName = "device-b", startTime = 1_000L)
        val place = a.roundbook.create("Keller")
        b.roundbook.eventLog.receive(a.roundbook.eventLog.all())
        a.now = 2_000L
        a.roundbook.places.rename(place.id, "Alpha")
        b.now = 3_000L
        b.roundbook.places.rename(place.id, "Beta")
        a.now = 4_000L
        a.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450))
        b.now = 5_000L
        b.roundbook.create("Garten")

        val all: List<Event> = (a.roundbook.eventLog.all() + b.roundbook.eventLog.all()).distinctBy { it.id }
        val inOrder = TestRoundbook(deviceName = "device-c").also { it.roundbook.eventLog.receive(all.sortedBy { e -> e.position }) }
        val expected = snapshot(inOrder.roundbook)
        assertEquals("Beta", expected.places.first { it.id == place.id }.name, "the later rename wins")

        val orders = listOf(all.reversed(), all.shuffled(kotlin.random.Random(1)), all.shuffled(kotlin.random.Random(2)))
        for (order in orders) {
            val c = TestRoundbook(deviceName = "device-c")
            order.forEach { c.roundbook.eventLog.receive(listOf(it)) }
            assertEquals(expected, snapshot(c.roundbook))
            c.roundbook.eventLog.rebuildViews()
            assertEquals(expected, snapshot(c.roundbook))
        }
    }

    @Test
    fun atTheSameTimeTheGreaterDeviceIdWins() {
        val a = TestRoundbook(deviceName = "device-a", startTime = 1_000L)
        val b = TestRoundbook(deviceName = "device-b", startTime = 1_000L)
        val place = a.roundbook.create("Keller")
        b.roundbook.eventLog.receive(a.roundbook.eventLog.all())
        a.now = 2_000L
        b.now = 2_000L
        a.roundbook.places.rename(place.id, "von A")
        b.roundbook.places.rename(place.id, "von B")

        val renameOfA = a.roundbook.eventLog.all().last()
        val renameOfB = b.roundbook.eventLog.all().last()
        // A gets B's rename, B gets A's: both end up with the same name.
        a.roundbook.eventLog.receive(listOf(renameOfB))
        b.roundbook.eventLog.receive(listOf(renameOfA))
        assertEquals("von B", a.roundbook.places.get(place.id)!!.name)
        assertEquals("von B", b.roundbook.places.get(place.id)!!.name)
    }

    @Test
    fun onTheSameDeviceAtTheSameTimeTheLaterNumberWins() {
        val t = TestRoundbook()
        val place = t.roundbook.create("Keller")
        t.roundbook.places.rename(place.id, "Erst")
        t.roundbook.places.rename(place.id, "Dann")
        assertEquals("Dann", t.roundbook.places.get(place.id)!!.name)
        t.roundbook.eventLog.rebuildViews()
        assertEquals("Dann", t.roundbook.places.get(place.id)!!.name)
    }
}
