package io.github.isouplay.roundbook.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

/** "4,30" – a price without currency, for ranges ("4,30–6,50 €"). */
fun formatPriceNumber(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val a = abs(cents)
    return "$sign${a / 100},${(a % 100).toString().padStart(2, '0')}"
}

/** "4,30 €", or "4,30–6,50 €" if [minCents] and [maxCents] differ. */
fun formatPriceRange(minCents: Long, maxCents: Long, currency: String): String =
    if (minCents == maxCents) formatPrice(minCents, currency) else "${formatPriceNumber(minCents)}–${formatPrice(maxCents, currency)}"

/** [value] rounded to at most [decimals] places, without trailing zeros, with decimal comma ("0,33", "2"). */
fun formatRounded(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToLong()
    val whole = abs(rounded) / factor.toLong()
    val fraction = (abs(rounded) % factor.toLong()).toString().padStart(decimals, '0').trimEnd('0')
    val sign = if (rounded < 0) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole,$fraction"
}

/** An amount in liters as shown on tiles: below 0,1 l in centilitres. The number and whether it is cl. */
data class Amount(val number: String, val centilitres: Boolean)

fun amountOf(liters: Double): Amount =
    if (liters < 0.1) Amount(formatRounded(liters * 100, 1), true) else Amount(formatRounded(liters, 3), false)

/** "21:05" in [zone]. */
@OptIn(ExperimentalTime::class)
fun formatClock(millis: Long, zone: TimeZone): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/** A decimal number typed by hand: "4,5", "4.5", " 4 ". Null if it is none. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** A price typed by hand in whole cents ("4,50" → 450). Null if it is no number. */
fun parsePriceCents(text: String): Long? = parseDecimal(text)?.let { (it * 100).roundToLong() }

/** A time typed by hand: "21:30", "9:05", "21.30". Null if it is none. */
fun parseClock(text: String): Pair<Int, Int>? {
    val match = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").find(text) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}
