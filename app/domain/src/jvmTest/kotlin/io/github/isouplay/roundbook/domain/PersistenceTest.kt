package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.evening.StartResult
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import io.github.isouplay.roundbook.domain.menu.MenuImportResult
import io.github.isouplay.roundbook.domain.place.PlaceResult
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Everything is still there after a restart of the app (database file closed and opened again). */
class PersistenceTest {

    private val file: File = File.createTempFile("roundbook-test", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun placesMenusAndDeviceSurviveARestart() {
        val firstDriver = createJvmSqlDriver(file.path)
        val first = Roundbook(firstDriver)
        val place = (first.places.create("Erfundener Keller") as PlaceResult.Done).place
        val imported = first.menus.importMenu(place.id, krugFile("2026-10-01", 450))
        assertIs<MenuImportResult.Imported>(imported)
        val deviceId = first.deviceId
        firstDriver.close()

        val secondDriver = createJvmSqlDriver(file.path)
        val second = Roundbook(secondDriver)
        assertEquals(deviceId, second.deviceId)
        assertEquals(listOf(place), second.places.all())
        assertEquals(imported.version, second.menus.current(place.id)!!.info)
        assertEquals(2L, second.eventLog.count())
        assertEquals(Roundbook.SCHEMA_VERSION, userVersion(secondDriver))
        secondDriver.close()
    }

    @Test
    fun anEveningWithEntriesSurvivesARestart() {
        val firstDriver = createJvmSqlDriver(file.path)
        val first = Roundbook(firstDriver)
        val place = (first.places.create("Erfundene Schenke") as PlaceResult.Done).place
        assertIs<MenuImportResult.Imported>(first.menus.importMenu(place.id, schenkeFile()))
        val evening = (first.evenings.start(place.id) as StartResult.Started).evening
        first.entries.book(evening.id, DrinkChoice.FromMenu(HELL, "0,5 l"))
        first.entries.book(evening.id, DrinkChoice.AsMix(NEBELGEIST, "0,2 l", ENERGIE), onTheHouse = true)
        first.favorites.setPinned(place.id, FavoriteKey.OfDrink(HELL), true)
        val entries = first.entries.ofEvening(evening.id)
        firstDriver.close()

        val secondDriver = createJvmSqlDriver(file.path)
        val second = Roundbook(secondDriver)
        assertEquals(evening, second.evenings.running())
        assertEquals(entries, second.entries.ofEvening(evening.id))
        assertEquals(listOf(FavoriteKey.OfDrink(HELL)), second.favorites.pinned(place.id))
        secondDriver.close()
    }
}
