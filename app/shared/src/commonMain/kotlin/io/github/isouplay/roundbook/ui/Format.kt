package io.github.isouplay.roundbook.ui

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.floor

// Number and date formats for German display. Not user text, only the form of values.

/** "4,30 €" for EUR, otherwise the currency code ("4,30 CHF"). */
fun formatPrice(cents: Long, currency: String): String {
    val sign = if (cents < 0) "-" else ""
    val a = abs(cents)
    val symbol = if (currency == "EUR") "€" else currency
    return "$sign${a / 100},${(a % 100).toString().padStart(2, '0')} $symbol"
}

/** "12.10.2026". */
fun formatDate(date: LocalDate): String {
    val (year, month, day) = date.toString().split('-')
    return "$day.$month.$year"
}

/** A decimal number as in the old app's messages: no trailing zeros, decimal comma ("0,5", "900"). */
fun formatDecimal(value: Double): String {
    if (value == floor(value) && abs(value) < 1e15) return value.toLong().toString()
    return value.toString().replace('.', ',')
}
