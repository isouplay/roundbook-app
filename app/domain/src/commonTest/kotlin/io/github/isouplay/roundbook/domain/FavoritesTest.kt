package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.core.menu.DrinkId
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.favorite.FavoriteItem
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import io.github.isouplay.roundbook.domain.favorite.Favorites
import io.github.isouplay.roundbook.domain.place.Place
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FavoritesTest {

    private fun drink(id: DrinkId): FavoriteKey = FavoriteKey.OfDrink(id)

    private fun mix(spirit: DrinkId, mixer: DrinkId?): FavoriteKey = FavoriteKey.OfMix(spirit, mixer)

    private fun TestRoundbook.row(place: Place): List<FavoriteKey> =
        roundbook.favorites.row(place.id, roundbook.menus.current(place.id)!!.menu).map { it.key }

    /** One evening at [place] with these bookings, ended. */
    private fun TestRoundbook.evening(place: Place, vararg choices: DrinkChoice) {
        val evening = startEvening(place)
        for (c in choices) {
            tick()
            book(evening, c)
        }
        tick()
        roundbook.evenings.end(evening.id)
        tick()
    }

    private fun menu(id: DrinkId, size: String = "0,5 l") = DrinkChoice.FromMenu(id, size)

    @Test
    fun pinnedFirstThenRecentlyOrderedEachByCountThenName() {
        val t = TestRoundbook(startTime = utc(2026, 10, 13, 18))
        val place = t.placeWithMenu()
        t.evening(place, menu(DUNKEL), menu(DUNKEL), menu(DUNKEL), menu(HELL), menu(HELL, "0,3 l"), menu(KRAEUTERBITTER, "2 cl"))
        t.roundbook.favorites.setPinned(place.id, drink(LIMO), true)
        t.roundbook.favorites.setPinned(place.id, mix(NEBELGEIST, ENERGIE), true)
        t.roundbook.favorites.setPinned(place.id, drink(KRAEUTERBITTER), true)
        t.startEvening(place)

        assertEquals(
            listOf(
                drink(KRAEUTERBITTER), // pinned, booked once
                mix(NEBELGEIST, ENERGIE), // pinned, never booked: by name "Nebelgeist …" before "Zitronenlimo"
                drink(LIMO),
                drink(DUNKEL), // recently ordered: 3
                drink(HELL), // 2 (both sizes count for the drink)
            ),
            t.row(place),
        )
        val item = t.roundbook.favorites.row(place.id, t.roundbook.menus.current(place.id)!!.menu)[1] as FavoriteItem.OfMix
        assertEquals(listOf(700L, 1150L), item.offers.map { it.size.priceCents }, "the price of this mixer, per size")
        assertTrue(item.pinned)
    }

    @Test
    fun aMixCountsForItselfNotForItsSpirit() {
        val t = TestRoundbook(startTime = utc(2026, 10, 13, 18))
        val place = t.placeWithMenu()
        t.evening(
            place,
            DrinkChoice.AsMix(NEBELGEIST, "0,2 l", LIMO),
            DrinkChoice.AsMix(NEBELGEIST, "0,4 l", LIMO),
            DrinkChoice.AsMix(NEBELGEIST, "0,2 l", null),
            menu(NEBELGEIST, "2 cl"),
        )
        assertEquals(
            mapOf(mix(NEBELGEIST, LIMO) to 2, mix(NEBELGEIST, null) to 1, drink(NEBELGEIST) to 1),
            t.roundbook.favorites.counts(place.id),
        )
    }

    @Test
    fun onlyTheLast90DaysAtThisPlaceCount() {
        val t = TestRoundbook(startTime = utc(2026, 7, 15, 20))
        val place = t.placeWithMenu()
        val other = t.placeWithMenu("Anderswo")
        t.evening(place, menu(HELL)) // evening of 15.07.: day 91
        t.now = utc(2026, 7, 16, 3)
        t.evening(place, menu(DUNKEL)) // started 03:00 on 16.07. – still the evening of 15.07.
        t.now = utc(2026, 7, 16, 20)
        t.evening(place, menu(BREZEL, "Stück")) // evening of 16.07.: day 90
        t.evening(other, menu(HELL), menu(HELL))
        t.now = utc(2026, 10, 13, 20)
        t.today = LocalDate(2026, 10, 13)

        assertEquals(mapOf(drink(BREZEL) to 1), t.roundbook.favorites.counts(place.id))
        assertEquals(mapOf(drink(HELL) to 2), t.roundbook.favorites.counts(other.id))
    }

    @Test
    fun atMostEightTiles() {
        val t = TestRoundbook(startTime = utc(2026, 10, 13, 18))
        val place = t.placeWithMenu()
        t.evening(
            place,
            menu(HELL), menu(DUNKEL), menu(NEBELGEIST, "2 cl"), menu(KRAEUTERBITTER, "2 cl"), menu(LIMO, "0,33 l"),
            menu(ENERGIE, "0,25 l"), menu(WASSER), menu(BREZEL, "Stück"),
            DrinkChoice.AsMix(NEBELGEIST, "0,2 l", LIMO), DrinkChoice.AsMix(KRAEUTERBITTER, "0,2 l", ENERGIE),
        )
        for (k in listOf(drink(BREZEL), drink(WASSER), mix(KRAEUTERBITTER, null))) t.roundbook.favorites.setPinned(place.id, k, true)
        t.roundbook.favorites.reorder()

        val row = t.row(place)
        assertEquals(Favorites.ROW_MAX, row.size)
        assertEquals(listOf(drink(BREZEL), drink(WASSER), mix(KRAEUTERBITTER, null)), row.take(3), "pinned first, by count then name")
        assertEquals(row.size, row.toSet().size, "nothing twice")
    }

    @Test
    fun theRowDoesNotReorderAfterEachBooking() {
        val t = TestRoundbook(startTime = utc(2026, 10, 13, 18))
        val place = t.placeWithMenu()
        t.evening(place, menu(HELL))
        val evening = t.startEvening(place)
        assertEquals(listOf(drink(HELL)), t.row(place))
        val order = t.roundbook.favorites.order.value

        repeat(3) { t.book(evening, menu(DUNKEL)) }
        assertEquals(listOf(drink(HELL)), t.row(place), "the counts of the beginning of the evening stay")
        assertEquals(order, t.roundbook.favorites.order.value)

        // Back after less than an hour: nothing moves.
        t.roundbook.favorites.movedToBackground()
        t.tick(59 * MINUTE)
        t.roundbook.favorites.cameToForeground()
        assertEquals(listOf(drink(HELL)), t.row(place))

        // Back after an hour: the row orders itself anew.
        t.roundbook.favorites.movedToBackground()
        t.tick(HOUR)
        t.roundbook.favorites.cameToForeground()
        assertEquals(listOf(drink(DUNKEL), drink(HELL)), t.row(place))
        assertTrue(t.roundbook.favorites.order.value > order)

        // A new evening orders anew, too.
        repeat(5) { t.bookHell(evening) }
        t.roundbook.evenings.end(evening.id)
        t.tick()
        t.startEvening(place)
        assertEquals(listOf(drink(HELL), drink(DUNKEL)), t.row(place))
    }

    @Test
    fun pinningAndUnpinningAreEvents() {
        val t = TestRoundbook()
        val place = t.placeWithMenu()
        val events = t.roundbook.eventLog.count()
        assertTrue(t.roundbook.favorites.setPinned(place.id, drink(HELL), true))
        assertTrue(t.roundbook.favorites.setPinned(place.id, drink(HELL), true), "already pinned: no second event")
        assertEquals(events + 1, t.roundbook.eventLog.count())
        assertEquals(EventType.FAVORITE_PINNED.code, t.roundbook.eventLog.all().last().type)
        t.tick()
        assertTrue(t.roundbook.favorites.toggle(place.id, mix(NEBELGEIST, null)))
        assertEquals(listOf(drink(HELL), mix(NEBELGEIST, null)), t.roundbook.favorites.pinned(place.id))
        t.tick()
        assertFalse(t.roundbook.favorites.toggle(place.id, drink(HELL)))
        assertEquals(EventType.FAVORITE_UNPINNED.code, t.roundbook.eventLog.all().last().type)
        assertEquals(listOf(mix(NEBELGEIST, null)), t.roundbook.favorites.pinned(place.id))
        t.roundbook.eventLog.rebuildViews()
        assertEquals(listOf(mix(NEBELGEIST, null)), t.roundbook.favorites.pinned(place.id))
    }

    @Test
    fun whatIsNotOnTheMenuIsLeftOut() {
        val t = TestRoundbook()
        val place = t.placeWithMenu()
        for (k in listOf(drink(DrinkId("gibt-es-nicht")), drink(DrinkId("altbestand")), mix(NEBELGEIST, WASSER), mix(HELL, null))) {
            t.roundbook.favorites.setPinned(place.id, k, true)
        }
        assertEquals(emptyList(), t.row(place))
    }

    @Test
    fun keysAreStoredAsIds() {
        assertEquals("nebelgeist", drink(NEBELGEIST).code)
        assertEquals("mix:[\"nebelgeist\",\"blitz-energie\"]", mix(NEBELGEIST, ENERGIE).code)
        assertEquals("mix:[\"nebelgeist\",\"\"]", mix(NEBELGEIST, null).code)
        for (k in listOf(drink(NEBELGEIST), mix(NEBELGEIST, ENERGIE), mix(NEBELGEIST, null))) {
            assertEquals(k, FavoriteKey.parse(k.code))
        }
        // The old app wrote "mischung:"; with ids it is understood, with names and sizes not.
        assertEquals(mix(NEBELGEIST, LIMO), FavoriteKey.parse("mischung:[\"nebelgeist\",\"zitronenlimo\"]"))
        assertNull(FavoriteKey.parse("mischung:[\"schnaps/nebelgeist\",\"0,2 l\",\"Zitronenlimo\"]"))
        assertNull(FavoriteKey.parse("schnaps/nebelgeist"))
        assertNull(FavoriteKey.parse("mix:kaputt"))
    }
}
