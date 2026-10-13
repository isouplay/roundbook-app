package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.entry.BookProblem
import io.github.isouplay.roundbook.domain.entry.ChangeProblem
import io.github.isouplay.roundbook.domain.entry.ChangeResult
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.entry.Entries
import io.github.isouplay.roundbook.domain.entry.TimeResult
import io.github.isouplay.roundbook.domain.event.EventType
import io.github.isouplay.roundbook.domain.menu.MenuImportResult
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntryChangeTest {

    private val start = utc(2026, 10, 13, 20, 7)
    private val utcZone = TimeZone.UTC

    @Test
    fun aTimeFromHourAndMinuteLiesWithinTheEvening() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(2 * HOUR)
        val entry = t.bookHell(evening) // 22:07
        val entries = t.roundbook.entries

        assertEquals(TimeResult.At(utc(2026, 10, 13, 21, 30)), entries.timeFor(entry, 21, 30, utcZone))
        assertEquals(TimeResult.At(utc(2026, 10, 13, 20, 7)), entries.timeFor(entry, 20, 7, utcZone), "the minute of the beginning")
        assertEquals(TimeResult.Unchanged, entries.timeFor(entry, 22, 7, utcZone))
        assertEquals(TimeResult.BeforeStart(utc(2026, 10, 13, 20, 7)), entries.timeFor(entry, 20, 6, utcZone))
        assertEquals(TimeResult.InFuture, entries.timeFor(entry, 23, 0, utcZone))
        // 01:30 belongs to the day after – still in the future at 22:07.
        assertEquals(TimeResult.InFuture, entries.timeFor(entry, 1, 30, utcZone))
    }

    @Test
    fun afterMidnightTheTimeBelongsToTheNextDay() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.now = utc(2026, 10, 14, 1, 45)
        val entry = t.bookHell(evening)
        assertEquals(TimeResult.At(utc(2026, 10, 14, 1, 0)), t.roundbook.entries.timeFor(entry, 1, 0, utcZone))
        assertEquals(TimeResult.At(utc(2026, 10, 13, 23, 50)), t.roundbook.entries.timeFor(entry, 23, 50, utcZone))
    }

    @Test
    fun anEndedEveningEndsTheRange() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(HOUR)
        val entry = t.bookHell(evening) // 21:07
        t.tick(HOUR)
        t.roundbook.evenings.end(evening.id) // 22:07
        t.tick(5 * HOUR)
        assertEquals(TimeResult.AfterEnd(utc(2026, 10, 13, 22, 7)), t.roundbook.entries.timeFor(entry, 23, 0, utcZone))
        assertEquals(TimeResult.At(utc(2026, 10, 13, 22, 0)), t.roundbook.entries.timeFor(entry, 22, 0, utcZone))
    }

    @Test
    fun changingTheTimeOfOneEntry() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(HOUR)
        val first = t.bookHell(evening)
        t.tick(HOUR)
        val second = t.bookHell(evening)
        val entries = t.roundbook.entries

        val earlier = utc(2026, 10, 13, 20, 30)
        assertEquals(ChangeResult.Changed(1, drinkChanged = false, drunkAt = earlier), entries.change(listOf(second.id), drunkAt = earlier))
        assertEquals(earlier, entries.get(second.id)!!.drunkAt)
        assertEquals(listOf(second.id, first.id), entries.ofEvening(evening.id).map { it.id }, "ordered by the time of drinking")
        assertEquals(EventType.ENTRY_CHANGED.code, t.roundbook.eventLog.all().last().type)

        assertEquals(
            ChangeResult.Rejected(ChangeProblem.TIME_OUT_OF_EVENING),
            entries.change(listOf(first.id), drunkAt = start - HOUR),
        )
        assertEquals(ChangeResult.Rejected(ChangeProblem.TIME_OUT_OF_EVENING), entries.change(listOf(first.id), drunkAt = t.now + MINUTE))
        assertEquals(ChangeResult.Unchanged, entries.change(listOf(first.id), drunkAt = first.drunkAt))
    }

    @Test
    fun changingTheTimeOfAWholeGroup() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(HOUR)
        val first = t.bookHell(evening)
        t.tick(10 * MINUTE)
        val second = t.bookHell(evening)
        val entries = t.roundbook.entries

        val earlier = utc(2026, 10, 13, 20, 45)
        assertEquals(ChangeResult.Changed(2, drinkChanged = false, drunkAt = earlier), entries.change(listOf(first.id, second.id), drunkAt = earlier))
        assertEquals(listOf(earlier, earlier), entries.ofEvening(evening.id).map { it.drunkAt })
        assertEquals(2, Entries.groups(entries.ofEvening(evening.id)).single().count)
        assertEquals(ChangeResult.Unchanged, entries.change(listOf(first.id, second.id), drunkAt = earlier))
    }

    @Test
    fun changingTheDrinkOfAGroupKeepsTimeAndOnTheHouse() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        val a = t.book(evening, DrinkChoice.FromMenu(HELL, "0,5 l"), onTheHouse = true)
        t.tick(MINUTE)
        val b = t.book(evening, DrinkChoice.FromMenu(HELL, "0,5 l"), onTheHouse = true)
        val entries = t.roundbook.entries

        val result = entries.change(listOf(a.id, b.id), DrinkChoice.FromMenu(DUNKEL, "0,5 l"))
        assertEquals(ChangeResult.Changed(2, drinkChanged = true, drunkAt = null), result)
        val after = entries.ofEvening(evening.id)
        assertEquals(listOf(a.drunkAt, b.drunkAt), after.map { it.drunkAt })
        assertTrue(after.all { it.snapshot.drinkId == DUNKEL && it.snapshot.onTheHouse && it.snapshot.priceCents == 0L })

        // On the house only if asked: a free entry instead is never on the house.
        entries.change(listOf(a.id), DrinkChoice.Free("Spezi", 0.4, null, 320, true))
        assertEquals("Spezi", entries.get(a.id)!!.snapshot.name)
        assertEquals(320L, entries.get(a.id)!!.snapshot.priceCents)

        assertEquals(ChangeResult.RejectedDrink(BookProblem.UNKNOWN_MIXER), entries.change(listOf(b.id), DrinkChoice.AsMix(NEBELGEIST, "0,2 l", WASSER)))
        assertEquals(ChangeResult.Unchanged, entries.change(listOf(b.id), DrinkChoice.FromMenu(DUNKEL, "0,5 l")))
    }

    @Test
    fun theSameDrinkWithAnotherTimeChangesOnlyTheTime() {
        val t = TestRoundbook(startTime = start)
        val place = t.placeWithMenu()
        val evening = t.startEvening(place)
        t.tick(HOUR)
        val entry = t.bookHell(evening)
        t.tick()
        assertIs<MenuImportResult.Imported>(t.roundbook.menus.importMenu(place.id, schenkeFile("2026-10-13", hellCents = 520)))

        val earlier = utc(2026, 10, 13, 20, 30)
        val result = t.roundbook.entries.change(listOf(entry.id), DrinkChoice.FromMenu(HELL, "0,5 l"), drunkAt = earlier)
        assertEquals(ChangeResult.Changed(1, drinkChanged = false, drunkAt = earlier), result)
        assertEquals(460L, t.roundbook.entries.get(entry.id)!!.snapshot.priceCents, "the price stays as it was")
    }

    @Test
    fun changesSurviveARebuild() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(HOUR)
        val entry = t.bookHell(evening)
        t.tick()
        t.roundbook.entries.change(listOf(entry.id), DrinkChoice.AsMix(NEBELGEIST, "0,4 l", ENERGIE), drunkAt = start + 30 * MINUTE)
        val before = t.roundbook.entries.ofEvening(evening.id)
        t.roundbook.eventLog.rebuildViews()
        assertEquals(before, t.roundbook.entries.ofEvening(evening.id))
        assertEquals(1150L, Entries.summary(before).totalCents)
    }
}
