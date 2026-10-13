package io.github.isouplay.roundbook

import io.github.isouplay.roundbook.ui.formatDate
import io.github.isouplay.roundbook.ui.formatDecimal
import io.github.isouplay.roundbook.ui.formatPrice
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
