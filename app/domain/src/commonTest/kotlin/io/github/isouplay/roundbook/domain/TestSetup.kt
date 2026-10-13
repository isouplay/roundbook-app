package io.github.isouplay.roundbook.domain

import kotlinx.datetime.LocalDate

/** A Roundbook in memory with a clock and ids the test controls. */
class TestRoundbook(
    val driver: app.cash.sqldelight.db.SqlDriver = createTestDriver(),
    deviceName: String = "device-a",
    startTime: Long = 1_000_000L,
    var today: LocalDate = LocalDate(2026, 10, 13),
) {
    var now: Long = startTime
    private var counter = 0
    private var first = true
    private val deviceName = deviceName

    val roundbook: Roundbook = Roundbook(
        driver = driver,
        clock = { now },
        today = { today },
        newId = {
            // The first id is the device id; the rest are numbered.
            if (first) {
                first = false
                deviceName
            } else {
                counter++
                "$deviceName-id-$counter"
            }
        },
    )

    /** Moves the clock on by [millis]. */
    fun tick(millis: Long = 1000L) {
        now += millis
    }
}

/**
 * An invented menu file in the format of the old app (roundbook-karte, format_version 1).
 * "Zum Erfundenen Krug" exists nowhere; prices are made up.
 */
fun krugFile(validFrom: String?, hellCents: Int = 450, label: String? = "Testkarte"): String {
    val price = "${hellCents / 100}.${(hellCents % 100).toString().padStart(2, '0')}"
    val date = if (validFrom == null) "" else "\"valid_from\": \"$validFrom\","
    val name = if (label == null) "" else "\"label\": \"$label\","
    return """
        {
          "format": "roundbook-karte",
          "format_version": 1,
          $date
          $name
          "menu": {
            "bar": "Zum Erfundenen Krug",
            "currency": "EUR",
            "categories": [{"id": "bier", "name": "Bier", "icon": "🍺"}],
            "drinks": [
              {"id": "krug-hell", "name": "Krug Hell", "category": "bier", "abv": 4.9,
               "sizes": [{"label": "0,5 l", "l": 0.5, "price": $price}]},
              {"id": "altbestand", "name": "Altbestand", "category": "bier", "hidden": true,
               "sizes": [{"label": "0,5 l", "l": 0.5, "price": 3.0}]}
            ]
          }
        }
    """.trimIndent()
}
