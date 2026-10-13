package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.entry.Entry
import io.github.isouplay.roundbook.domain.evening.Evening
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Evenings, entries, taken-back actions and favourites: event by event equals rebuilt from the log. */
class EveningProjectionTest {

    private data class Snapshot(
        val evenings: List<Evening?>,
        val entries: List<List<Entry>>,
        val pinned: List<FavoriteKey>,
    )

    private fun snapshot(r: Roundbook, evenings: List<EveningId>, place: PlaceId) = Snapshot(
        evenings = evenings.map { r.evenings.get(it) },
        entries = evenings.map { r.entries.ofEvening(it) },
        pinned = r.favorites.pinned(place),
    )

    @Test
    fun stepByStepEqualsRebuildAndAnyOrder() {
        val t = TestRoundbook(startTime = utc(2026, 10, 13, 19))
        val r = t.roundbook
        val place = t.placeWithMenu()
        val first = t.startEvening(place)
        t.tick(MINUTE)
        val a = t.bookHell(first)
        t.tick(MINUTE)
        val b = t.book(first, DrinkChoice.AsMix(NEBELGEIST, "0,2 l", ENERGIE))
        t.tick(MINUTE)
        t.book(first, DrinkChoice.Free("Fahrershot", 0.02, 0.0, 0, true), onTheHouse = false)
        t.tick(MINUTE)
        r.entries.change(listOf(a.id), DrinkChoice.FromMenu(DUNKEL, "0,5 l"), drunkAt = first.startedAt + 30_000)
        t.tick(MINUTE)
        r.entries.delete(b.id)
        t.tick(MINUTE)
        r.entries.undo(first.id)
        t.tick(MINUTE)
        r.favorites.setPinned(place.id, FavoriteKey.OfMix(NEBELGEIST, ENERGIE), true)
        t.tick(MINUTE)
        r.favorites.setPinned(place.id, FavoriteKey.OfDrink(HELL), true)
        t.tick(MINUTE)
        r.favorites.setPinned(place.id, FavoriteKey.OfDrink(HELL), false)
        t.tick(MINUTE)
        r.evenings.end(first.id)
        t.tick(HOUR)
        val second = t.startEvening(place)
        t.tick(MINUTE)
        val c = t.book(second, DrinkChoice.FromMenu(HELL, "0,3 l"), onTheHouse = true)
        t.tick(MINUTE)
        r.entries.undo(second.id)
        t.tick(MINUTE)
        t.bookHell(second)
        t.tick(MINUTE)
        r.entries.change(listOf(c.id), drunkAt = second.startedAt) // c is gone: unknown entry, nothing recorded

        val ids = listOf(first.id, second.id)
        val stepByStep = snapshot(r, ids, place.id)
        assertEquals(listOf(3, 1), stepByStep.entries.map { it.size })
        assertEquals("Schenke Dunkel", stepByStep.entries[0].first().snapshot.name)

        r.eventLog.rebuildViews()
        assertEquals(stepByStep, snapshot(r, ids, place.id))

        val all = r.eventLog.all()
        for (order in listOf(all.reversed(), all.shuffled(Random(3)), all.shuffled(Random(4)))) {
            val other = TestRoundbook(deviceName = "device-c")
            order.forEach { other.roundbook.eventLog.receive(listOf(it)) }
            assertEquals(stepByStep, snapshot(other.roundbook, ids, place.id))
        }
    }
}
