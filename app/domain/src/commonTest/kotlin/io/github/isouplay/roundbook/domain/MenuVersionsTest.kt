package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.core.menu.DrinkId
import io.github.isouplay.roundbook.core.menu.MenuProblemKind
import io.github.isouplay.roundbook.domain.menu.MenuImportResult
import io.github.isouplay.roundbook.domain.menu.MenuVersion
import io.github.isouplay.roundbook.domain.place.Place
import io.github.isouplay.roundbook.domain.place.PlaceResult
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MenuVersionsTest {

    private fun TestRoundbook.place(name: String = "Erfundener Keller"): Place =
        (roundbook.places.create(name) as PlaceResult.Done).place

    private fun MenuVersion.hellCents(): Long = menu.drink(DrinkId("krug-hell"))!!.sizes.single().priceCents

    @Test
    fun importsMenuAsNewVersionOfThePlace() {
        val t = TestRoundbook()
        val place = t.place()
        assertNull(t.roundbook.menus.current(place.id))

        val result = t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450))
        assertIs<MenuImportResult.Imported>(result)
        assertEquals(1, result.drinkCount, "hidden drinks do not count")
        assertEquals(LocalDate(2026, 10, 1), result.version.validFrom)
        assertEquals("Testkarte", result.version.label)
        assertEquals(place.id, result.version.placeId)

        val current = t.roundbook.menus.current(place.id)!!
        assertEquals(result.version, current.info)
        assertEquals(450L, current.hellCents())
        assertEquals("Zum Erfundenen Krug", current.menu.bar)
    }

    @Test
    fun withoutDateTheMenuIsValidFromToday() {
        val t = TestRoundbook(today = LocalDate(2026, 10, 13))
        val place = t.place()
        val result = t.roundbook.menus.importMenu(place.id, krugFile(null, label = null))
        assertIs<MenuImportResult.Imported>(result)
        assertEquals(LocalDate(2026, 10, 13), result.version.validFrom)
        assertEquals("", result.version.label)
    }

    @Test
    fun priceHistoryKeepsAllVersionsAndTheNewestIsCurrent() {
        val t = TestRoundbook()
        val place = t.place()
        t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450))
        t.tick()
        t.roundbook.menus.importMenu(place.id, krugFile("2027-03-01", 480))

        val versions = t.roundbook.menus.versions(place.id)
        assertEquals(listOf(LocalDate(2027, 3, 1), LocalDate(2026, 10, 1)), versions.map { it.validFrom })
        assertEquals(480L, t.roundbook.menus.current(place.id)!!.hellCents())
        assertEquals(450L, t.roundbook.menus.get(versions[1].id)!!.hellCents(), "the old price stays")
    }

    @Test
    fun anOlderVersionReadLaterDoesNotBecomeCurrent() {
        val t = TestRoundbook()
        val place = t.place()
        t.roundbook.menus.importMenu(place.id, krugFile("2027-03-01", 480))
        t.tick()
        t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450))
        assertEquals(LocalDate(2027, 3, 1), t.roundbook.menus.current(place.id)!!.info.validFrom)
        assertEquals(2, t.roundbook.menus.versions(place.id).size)
    }

    @Test
    fun atTheSameDateTheVersionReadLastIsCurrent() {
        val t = TestRoundbook()
        val place = t.place()
        t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450))
        t.tick()
        t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 470))
        assertEquals(470L, t.roundbook.menus.current(place.id)!!.hellCents())
        assertEquals(2, t.roundbook.menus.versions(place.id).size)
    }

    @Test
    fun theSameFileTwiceGivesNoNewVersion() {
        val t = TestRoundbook()
        val place = t.place()
        val first = t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450))
        t.tick()
        val second = t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450, label = "anders"))
        assertIs<MenuImportResult.Imported>(first)
        assertEquals(MenuImportResult.Unchanged(first.version), second)
        assertEquals(1, t.roundbook.menus.versions(place.id).size)
        assertEquals(2L, t.roundbook.eventLog.count())
    }

    @Test
    fun aRejectedFileRecordsNothing() {
        val t = TestRoundbook()
        val place = t.place()
        val result = t.roundbook.menus.importMenu(place.id, krugFile("2026-10-01", 450).replace("4.9", "900"))
        assertIs<MenuImportResult.Rejected>(result)
        assertEquals(MenuProblemKind.NUMBER_OUT_OF_RANGE, result.problem.kind)
        assertEquals(1L, t.roundbook.eventLog.count())
        assertNull(t.roundbook.menus.current(place.id))
    }

    @Test
    fun unknownPlace() {
        val t = TestRoundbook()
        assertEquals(MenuImportResult.UnknownPlace, t.roundbook.menus.importMenu(PlaceId("nirgends"), krugFile("2026-10-01")))
    }

    @Test
    fun priceHistoryStaysPerPlace() {
        val t = TestRoundbook()
        val keller = t.place("Keller")
        val garten = t.place("Garten")
        t.roundbook.menus.importMenu(keller.id, krugFile("2026-10-01", 450))
        t.roundbook.menus.importMenu(garten.id, krugFile("2026-10-01", 390))
        t.roundbook.menus.importMenu(garten.id, krugFile("2027-01-01", 410))
        assertEquals(1, t.roundbook.menus.versions(keller.id).size)
        assertEquals(2, t.roundbook.menus.versions(garten.id).size)
        assertEquals(450L, t.roundbook.menus.current(keller.id)!!.hellCents())
        assertEquals(410L, t.roundbook.menus.current(garten.id)!!.hellCents())
    }

    @Test
    fun storageHasSchemaVersionTwo() {
        // 1: package 2; 2: package 3 (evenings, entries, taken-back actions, favourites).
        assertEquals(2L, Roundbook.SCHEMA_VERSION)
    }
}
