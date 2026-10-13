package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.core.menu.CategoryId
import io.github.isouplay.roundbook.core.menu.MixId
import io.github.isouplay.roundbook.core.menu.MixTierId
import io.github.isouplay.roundbook.domain.entry.BookProblem
import io.github.isouplay.roundbook.domain.entry.BookResult
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.entry.Entries
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.menu.MenuImportResult
import io.github.isouplay.roundbook.domain.place.PlaceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntriesTest {

    private val start = utc(2026, 10, 13, 20)

    @Test
    fun anEntryHoldsASnapshotOfTheDrink() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(10 * MINUTE)
        val entry = t.bookHell(evening)

        val s = entry.snapshot
        assertEquals("Schenke Hell", s.name)
        assertEquals("0,5 l", s.sizeLabel)
        assertEquals(0.5, s.liters)
        assertEquals(460L, s.priceCents)
        assertEquals("EUR", s.currency)
        assertEquals(5.0, s.abv)
        assertNull(s.spiritCl)
        assertTrue(s.countsAsDrink)
        assertEquals(HELL, s.drinkId)
        assertEquals(CategoryId("bier"), s.categoryId)
        assertEquals(t.roundbook.menus.current(evening.placeId)!!.info.id, s.menuVersionId)
        assertFalse(s.onTheHouse)
        assertEquals(start + 10 * MINUTE, entry.drunkAt)
        assertEquals(evening.id, entry.eveningId)
        assertEquals(EventType.ENTRY_ADDED.code, t.roundbook.eventLog.all().last().type)
    }

    @Test
    fun theCounterOnATileCountsByDrinkId() {
        val t = TestRoundbook(startTime = start)
        val place = t.placeWithMenu()
        val evening = t.startEvening(place)
        t.bookHell(evening, "0,3 l")
        t.bookHell(evening, "0,5 l")
        t.book(evening, DrinkChoice.AsMix(NEBELGEIST, "0,2 l", LIMO))
        t.book(evening, DrinkChoice.FromMenu(DUNKEL, "0,5 l"))
        t.book(evening, DrinkChoice.Free("Schenke Hell", 0.5, 5.0, 460, true))
        // The menu renames the drink; it is still the same drink.
        t.tick()
        val renamed = schenkeFile("2026-10-02").replace("\"Schenke Hell\"", "\"Schenke Helles\"")
        assertIs<MenuImportResult.Imported>(t.roundbook.menus.importMenu(place.id, renamed))
        t.bookHell(evening, "0,5 l")

        val entries = t.roundbook.entries.ofEvening(evening.id)
        assertEquals(mapOf(HELL to 3, NEBELGEIST to 1, DUNKEL to 1), Entries.countsByDrink(entries))
        val hellHalf = Entries.groups(entries).single { it.snapshot.drinkId == HELL && it.snapshot.sizeLabel == "0,5 l" }
        assertEquals(2, hellHalf.count)
        assertEquals("Schenke Helles", hellHalf.snapshot.name, "the newest name is shown")
    }

    @Test
    fun aMixTakesSizePriceAndSpiritFromTheTier() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())

        val energie = t.book(evening, DrinkChoice.AsMix(NEBELGEIST, "0,2 l", ENERGIE)).snapshot
        assertEquals("Nebelgeist", energie.name)
        assertEquals("Blitz Energie", energie.mixerName)
        assertEquals(ENERGIE, energie.mixerId)
        assertEquals(MixTierId("k1"), energie.mixTierId)
        assertEquals(MixId("energie"), energie.mixId)
        assertEquals("mit Energie", energie.mixName)
        assertEquals("0,2 l", energie.sizeLabel)
        assertEquals(0.2, energie.liters)
        assertEquals(700L, energie.priceCents, "the mixer with search word draws its own price")
        assertEquals(4.0, energie.spiritCl)
        assertEquals(38.0, energie.abv)
        assertTrue(energie.isMix)

        val limo = t.book(evening, DrinkChoice.AsMix(NEBELGEIST, "0,4 l", LIMO)).snapshot
        assertEquals(990L, limo.priceCents, "any other mixer: the general mix")
        assertEquals(8.0, limo.spiritCl)
        assertEquals(MixId("mische"), limo.mixId)

        val any = t.book(evening, DrinkChoice.AsMix(NEBELGEIST, "0,2 l", null)).snapshot
        assertEquals(650L, any.priceCents)
        assertNull(any.mixerId)
        assertNull(any.mixerName)
        assertEquals("mit Mischgetränk", any.mixName)

        fun problem(choice: DrinkChoice) = (t.roundbook.entries.book(evening.id, choice) as BookResult.Rejected).problem
        assertEquals(BookProblem.UNKNOWN_MIXER, problem(DrinkChoice.AsMix(NEBELGEIST, "0,2 l", WASSER)), "Wasser is ticked as no mixer")
        assertEquals(BookProblem.NOT_MIXABLE, problem(DrinkChoice.AsMix(HELL, "0,2 l", LIMO)))
        assertEquals(BookProblem.UNKNOWN_SIZE, problem(DrinkChoice.AsMix(NEBELGEIST, "1 l", LIMO)))
        assertEquals(BookProblem.UNKNOWN_DRINK, problem(DrinkChoice.FromMenu(io.github.isouplay.roundbook.core.menu.DrinkId("altbestand"), "0,5 l")))
        assertEquals(BookProblem.UNKNOWN_SIZE, problem(DrinkChoice.FromMenu(HELL, "1 l")))
    }

    @Test
    fun onTheHouseCostsNothingButCountsAsDrink() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.bookHell(evening)
        val free = t.book(evening, DrinkChoice.FromMenu(HELL, "0,5 l"), onTheHouse = true).snapshot
        val mix = t.book(evening, DrinkChoice.AsMix(NEBELGEIST, "0,2 l", ENERGIE), onTheHouse = true).snapshot
        assertEquals(0L, free.priceCents)
        assertTrue(free.onTheHouse)
        assertEquals(0L, mix.priceCents)
        assertTrue(mix.onTheHouse)

        val entries = t.roundbook.entries.ofEvening(evening.id)
        val summary = Entries.summary(entries)
        assertEquals(460L, summary.totalCents)
        assertEquals(3, summary.drinkCount)
        val groups = Entries.groups(entries)
        assertEquals(3, groups.size, "on the house is its own group")
    }

    @Test
    fun aFreeEntryNeedsNoMenu() {
        val t = TestRoundbook(startTime = start)
        val place = (t.roundbook.places.create("Ohne Karte") as PlaceResult.Done).place
        val evening = t.startEvening(place)
        val entry = t.book(evening, DrinkChoice.Free("  Hausrunde  ", 0.02, 30.0, 0, countsAsDrink = true)).snapshot
        assertEquals("Hausrunde", entry.name)
        assertEquals("", entry.sizeLabel)
        assertEquals(0.02, entry.liters)
        assertEquals(30.0, entry.abv)
        assertEquals(0L, entry.priceCents)
        assertNull(entry.drinkId)
        assertTrue(entry.isFree)
        assertFalse(entry.onTheHouse, "a free entry for 0 € is no round from the house")

        val tip = t.book(evening, DrinkChoice.Free("Trinkgeld", null, 12.0, 150, countsAsDrink = false)).snapshot
        assertEquals(0.0, tip.abv, "strength only counts with an amount")
        assertEquals(1, Entries.summary(t.roundbook.entries.ofEvening(evening.id)).drinkCount)

        fun problem(choice: DrinkChoice) = (t.roundbook.entries.book(evening.id, choice) as BookResult.Rejected).problem
        assertEquals(BookProblem.NO_MENU, problem(DrinkChoice.FromMenu(HELL, "0,5 l")))
        assertEquals(BookProblem.NAME_EMPTY, problem(DrinkChoice.Free(" ", null, null, 100, true)))
        assertEquals(BookProblem.NAME_TOO_LONG, problem(DrinkChoice.Free("x".repeat(81), null, null, 100, true)))
        assertEquals(BookProblem.PRICE_OUT_OF_RANGE, problem(DrinkChoice.Free("Teuer", null, null, 1_000_000, true)))
        assertEquals(BookProblem.PRICE_OUT_OF_RANGE, problem(DrinkChoice.Free("Minus", null, null, -1, true)))
        assertEquals(BookProblem.VOLUME_OUT_OF_RANGE, problem(DrinkChoice.Free("Fass", 6.0, null, 100, true)))
        assertEquals(BookProblem.ABV_OUT_OF_RANGE, problem(DrinkChoice.Free("Sprit", 0.02, 97.0, 100, true)))
    }

    @Test
    fun theSnapshotStaysWhenTheMenuChanges() {
        val t = TestRoundbook(startTime = start)
        val place = t.placeWithMenu()
        val evening = t.startEvening(place)
        val before = t.bookHell(evening)
        t.tick()
        assertIs<MenuImportResult.Imported>(t.roundbook.menus.importMenu(place.id, schenkeFile("2026-10-13", hellCents = 520)))
        val after = t.bookHell(evening)

        assertEquals(460L, t.roundbook.entries.get(before.id)!!.snapshot.priceCents)
        assertEquals(520L, after.snapshot.priceCents)
        assertNotEquals(before.snapshot.menuVersionId, after.snapshot.menuVersionId)
        t.roundbook.eventLog.rebuildViews()
        assertEquals(460L, t.roundbook.entries.get(before.id)!!.snapshot.priceCents, "also after a rebuild")
    }

    @Test
    fun bookingNeedsARunningEvening() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.roundbook.evenings.end(evening.id)
        assertEquals(
            BookResult.Rejected(BookProblem.NOT_RUNNING),
            t.roundbook.entries.book(evening.id, DrinkChoice.FromMenu(HELL, "0,5 l")),
        )
    }

    @Test
    fun entriesAreGroupedByDrinkSizeAndPrice() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.bookHell(evening)
        t.tick()
        t.book(evening, DrinkChoice.Free("Spezi", 0.4, null, 320, true))
        t.tick()
        t.bookHell(evening)
        t.tick()
        t.bookHell(evening, "0,3 l")
        t.tick()
        t.book(evening, DrinkChoice.Free("Spezi", 0.4, null, 320, true))
        t.tick()
        t.bookHell(evening)
        t.tick()
        t.book(evening, DrinkChoice.Free("Spezi", 0.5, null, 320, true))

        val groups = Entries.groups(t.roundbook.entries.ofEvening(evening.id))
        assertEquals(
            listOf("Schenke Hell 0,5 l" to 3, "Spezi " to 2, "Schenke Hell 0,3 l" to 1, "Spezi " to 1),
            groups.map { "${it.snapshot.name} ${it.snapshot.sizeLabel}" to it.count },
            "in the order of the first booking",
        )
        assertEquals(1380L, groups[0].totalCents)
        assertEquals(640L, groups[1].totalCents)
    }

    @Test
    fun deletingRemovesTheYoungestEntryOfTheGroup() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        val first = t.bookHell(evening)
        t.tick(10 * MINUTE)
        val second = t.bookHell(evening)
        t.tick(10 * MINUTE)
        val third = t.bookHell(evening)
        // The third was drunk earlier than the second.
        assertIs<io.github.isouplay.roundbook.domain.entry.ChangeResult.Changed>(
            t.roundbook.entries.change(listOf(third.id), drunkAt = first.drunkAt + 5 * MINUTE),
        )

        val group = Entries.groups(t.roundbook.entries.ofEvening(evening.id)).single()
        assertEquals(second.id, group.youngest.id)
        assertTrue(t.roundbook.entries.delete(group.youngest.id))
        assertEquals(listOf(first.id, third.id), t.roundbook.entries.ofEvening(evening.id).map { it.id })
        assertEquals(EventType.ENTRY_DELETED.code, t.roundbook.eventLog.all().last().type)
        assertFalse(t.roundbook.entries.delete(second.id), "already gone")
    }
}
