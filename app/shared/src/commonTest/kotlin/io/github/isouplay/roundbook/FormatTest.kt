package io.github.isouplay.roundbook

import io.github.isouplay.roundbook.ui.Amount
import io.github.isouplay.roundbook.ui.amountOf
import io.github.isouplay.roundbook.ui.formatClock
import io.github.isouplay.roundbook.ui.formatDate
import io.github.isouplay.roundbook.ui.formatDecimal
import io.github.isouplay.roundbook.ui.formatPrice
import io.github.isouplay.roundbook.ui.formatPriceRange
import io.github.isouplay.roundbook.ui.formatRounded
import io.github.isouplay.roundbook.ui.parseClock
import io.github.isouplay.roundbook.ui.parsePriceCents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {

    @Test
    fun pricesInEuroWithDecimalComma() {
        assertEquals("4,30 €", formatPrice(430, "EUR"))
        assertEquals("0,05 €", formatPrice(5, "EUR"))
        assertEquals("9999,99 €", formatPrice(999_999, "EUR"))
        assertEquals("2,00 CHF", formatPrice(200, "CHF"))
    }

    @Test
    fun datesInGermanOrder() {
        assertEquals("01.10.2026", formatDate(LocalDate(2026, 10, 1)))
    }

    @Test
    fun decimalsWithoutTrailingZeros() {
        assertEquals("900", formatDecimal(900.0))
        assertEquals("0,5", formatDecimal(0.5))
        assertEquals("9999,99", formatDecimal(9999.99))
    }

    @Test
    fun priceRanges() {
        assertEquals("4,50 €", formatPriceRange(450, 450, "EUR"))
        assertEquals("3,40–4,60 €", formatPriceRange(340, 460, "EUR"))
    }

    @Test
    fun amountsBelowATenthOfALiterInCentilitres() {
        assertEquals(Amount("2", true), amountOf(0.02))
        assertEquals(Amount("4", true), amountOf(0.04))
        assertEquals(Amount("0,33", false), amountOf(0.33))
        assertEquals(Amount("2,5", false), amountOf(2.5))
        assertEquals("0,125", formatRounded(0.125, 3))
        assertEquals("1", formatRounded(0.9999, 2))
    }

    @Test
    fun typedPricesAndTimes() {
        assertEquals(450L, parsePriceCents("4,50"))
        assertEquals(450L, parsePriceCents(" 4.5 "))
        assertEquals(0L, parsePriceCents("0"))
        assertNull(parsePriceCents("vier"))
        assertNull(parsePriceCents(""))
        assertEquals(21 to 30, parseClock("21:30"))
        assertEquals(9 to 5, parseClock("9.05"))
        assertNull(parseClock("24:00"))
        assertNull(parseClock("21:3"))
    }

    @Test
    fun clockTimes() {
        assertEquals("20:07", formatClock(1_791_921_000_000L - 1_791_921_000_000L % 86_400_000L + (20 * 60 + 7) * 60_000L, TimeZone.UTC))
    }
}
