package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.domain.place.PlaceKind
import io.github.isouplay.roundbook.domain.place.PlaceNameProblem
import io.github.isouplay.roundbook.domain.place.PlaceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlacesTest {

    @Test
    fun createsLocalPlaceWithTrimmedName() {
        val t = TestRoundbook()
        val result = t.roundbook.places.create("  Erfundener Keller ")
        assertIs<PlaceResult.Done>(result)
        assertEquals("Erfundener Keller", result.place.name)
        assertEquals(PlaceKind.LOCAL, result.place.kind)
        assertEquals(1_000_000L, result.place.createdAt)
        assertEquals(result.place, t.roundbook.places.get(result.place.id))
    }

    @Test
    fun listsPlacesByName() {
        val t = TestRoundbook()
        t.roundbook.places.create("zelt")
        t.roundbook.places.create("Bühne")
        t.roundbook.places.create("Alm")
        assertEquals(listOf("Alm", "Bühne", "zelt"), t.roundbook.places.all().map { it.name })
    }

    @Test
    fun rejectsEmptyOrTooLongNameAndRecordsNothing() {
        val t = TestRoundbook()
        assertEquals(PlaceResult.Rejected(PlaceNameProblem.EMPTY), t.roundbook.places.create("   "))
        assertEquals(PlaceResult.Rejected(PlaceNameProblem.TOO_LONG), t.roundbook.places.create("x".repeat(81)))
        assertIs<PlaceResult.Done>(t.roundbook.places.create("x".repeat(80)))
        assertEquals(1L, t.roundbook.eventLog.count())
    }

    @Test
    fun renamesPlace() {
        val t = TestRoundbook()
        val place = (t.roundbook.places.create("Alt") as PlaceResult.Done).place
        val renamed = t.roundbook.places.rename(place.id, "Neu")
        assertIs<PlaceResult.Done>(renamed)
        assertEquals("Neu", renamed.place.name)
        assertEquals(place.createdAt, renamed.place.createdAt)
        assertEquals(PlaceResult.Rejected(PlaceNameProblem.EMPTY), t.roundbook.places.rename(place.id, ""))
        assertEquals(PlaceResult.UnknownPlace, t.roundbook.places.rename(PlaceId("gibt-es-nicht"), "Name"))
    }
}
