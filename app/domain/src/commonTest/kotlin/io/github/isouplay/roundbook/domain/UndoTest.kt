package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.entry.ActionKind
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.entry.Entries
import io.github.isouplay.roundbook.domain.event.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoTest {

    private val start = utc(2026, 10, 13, 20)

    @Test
    fun takingBackABookingWithinFiveMinutes() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        val entry = t.bookHell(evening)
        val booked = t.roundbook.eventLog.all().last()
        t.tick(5 * MINUTE)

        val result = t.roundbook.entries.undo(evening.id)!!
        assertEquals(ActionKind.BOOKED, result.kind)
        assertEquals(1, result.count)
        assertEquals("Schenke Hell", result.snapshot!!.name)
        assertNull(t.roundbook.entries.get(entry.id))
        // Taking back is an event itself; the booking stays in the log.
        val events = t.roundbook.eventLog.all()
        assertEquals(EventType.ACTION_UNDONE.code, events.last().type)
        assertTrue(booked in events)
        assertNull(t.roundbook.entries.undoable(evening.id), "nothing left to take back")
    }

    @Test
    fun afterFiveMinutesThereIsNothingToTakeBack() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        val entry = t.bookHell(evening)
        t.tick(5 * MINUTE + 1)
        assertNull(t.roundbook.entries.undoable(evening.id))
        assertNull(t.roundbook.entries.undo(evening.id))
        assertNotNull(t.roundbook.entries.get(entry.id))
    }

    @Test
    fun eachTapTakesBackTheNewestActionLikeAStack() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        val hell = t.bookHell(evening)
        t.tick(MINUTE)
        val dunkel = t.book(evening, DrinkChoice.FromMenu(DUNKEL, "0,5 l"))
        t.tick(MINUTE)

        assertEquals("Schenke Dunkel", t.roundbook.entries.undo(evening.id)!!.snapshot!!.name)
        assertNull(t.roundbook.entries.get(dunkel.id))
        assertNotNull(t.roundbook.entries.get(hell.id))
        assertEquals("Schenke Hell", t.roundbook.entries.undo(evening.id)!!.snapshot!!.name)
        assertTrue(t.roundbook.entries.ofEvening(evening.id).isEmpty())
        assertNull(t.roundbook.entries.undo(evening.id))
    }

    @Test
    fun takingBackADeletionBringsTheEntryBackAsItWas() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(HOUR)
        val entry = t.bookHell(evening)
        t.tick(10 * MINUTE)
        t.roundbook.entries.delete(entry.id)
        assertNull(t.roundbook.entries.get(entry.id))

        val result = t.roundbook.entries.undo(evening.id)!!
        assertEquals(ActionKind.DELETED, result.kind)
        assertEquals("Schenke Hell", result.snapshot!!.name)
        assertEquals(entry, t.roundbook.entries.get(entry.id), "same time, same drink")
        // The booking itself is older than five minutes and stays.
        assertNull(t.roundbook.entries.undoable(evening.id))
    }

    @Test
    fun takingBackAChange() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        t.tick(HOUR)
        val a = t.bookHell(evening)
        t.tick(MINUTE)
        val b = t.bookHell(evening)
        t.tick(10 * MINUTE)
        t.roundbook.entries.change(listOf(a.id, b.id), DrinkChoice.AsMix(NEBELGEIST, "0,2 l", LIMO))
        t.tick(MINUTE)
        t.roundbook.entries.change(listOf(a.id), drunkAt = start + 15 * MINUTE)

        val time = t.roundbook.entries.undo(evening.id)!!
        assertEquals(ActionKind.CHANGED, time.kind)
        assertEquals(a.drunkAt, t.roundbook.entries.get(a.id)!!.drunkAt)
        assertEquals("Nebelgeist", t.roundbook.entries.get(a.id)!!.snapshot.name, "the earlier change stays")

        val drink = t.roundbook.entries.undo(evening.id)!!
        assertEquals(2, drink.count)
        assertEquals(listOf(a, b), t.roundbook.entries.ofEvening(evening.id))
    }

    @Test
    fun onlyActionsOfTheRunningEveningCount() {
        val t = TestRoundbook(startTime = start)
        val place = t.placeWithMenu()
        val first = t.startEvening(place)
        t.bookHell(first)
        t.tick(MINUTE)
        t.roundbook.evenings.end(first.id)
        t.tick(MINUTE)
        val second = t.startEvening(place)
        assertNull(t.roundbook.entries.undoable(second.id))
        assertNotNull(t.roundbook.entries.undoable(first.id))
    }

    @Test
    fun takenBackActionsStayTakenBackAfterARebuild() {
        val t = TestRoundbook(startTime = start)
        val evening = t.startEvening(t.placeWithMenu())
        val a = t.bookHell(evening)
        t.tick(MINUTE)
        t.book(evening, DrinkChoice.FromMenu(DUNKEL, "0,5 l"))
        t.tick(MINUTE)
        t.roundbook.entries.delete(a.id)
        t.tick(MINUTE)
        t.roundbook.entries.undo(evening.id) // the deletion
        t.tick(MINUTE)
        t.roundbook.entries.undo(evening.id) // the Dunkel
        val before = t.roundbook.entries.ofEvening(evening.id)
        assertEquals(listOf(a), before)

        t.roundbook.eventLog.rebuildViews()
        assertEquals(before, t.roundbook.entries.ofEvening(evening.id))
        assertEquals(460L, Entries.summary(before).totalCents)
        // What is taken back cannot be taken back twice: next is the booking of a.
        assertEquals(ActionKind.BOOKED, t.roundbook.entries.undoable(evening.id)!!.kind)
    }
}
