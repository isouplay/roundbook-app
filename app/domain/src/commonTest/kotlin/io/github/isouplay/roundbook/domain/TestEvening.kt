package io.github.isouplay.roundbook.domain

import io.github.isouplay.roundbook.core.menu.DrinkId
import io.github.isouplay.roundbook.domain.entry.BookResult
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.entry.Entry
import io.github.isouplay.roundbook.domain.evening.Evening
import io.github.isouplay.roundbook.domain.evening.StartResult
import io.github.isouplay.roundbook.domain.menu.MenuImportResult
import io.github.isouplay.roundbook.domain.place.Place
import io.github.isouplay.roundbook.domain.place.PlaceResult
import kotlinx.datetime.toInstant

/**
 * An invented menu with sizes, a mix tier and mixers (format roundbook-karte, format_version 1).
 * "Zur Erfundenen Schenke" exists nowhere; names and prices are made up.
 */
fun schenkeFile(validFrom: String = "2026-10-01", hellCents: Int = 460): String {
    val hell = "${hellCents / 100}.${(hellCents % 100).toString().padStart(2, '0')}"
    return """
        {
          "format": "roundbook-karte",
          "format_version": 1,
          "valid_from": "$validFrom",
          "menu": {
            "bar": "Zur Erfundenen Schenke",
            "currency": "EUR",
            "categories": [
              {"id": "bier", "name": "Bier", "icon": "🍺"},
              {"id": "schnaps", "name": "Schnaps", "icon": "🥃"},
              {"id": "alkoholfrei", "name": "Alkoholfrei", "icon": "🥤", "mixer": true},
              {"id": "snacks", "name": "Knabberzeug", "icon": "🥨", "counts_as_drink": false}
            ],
            "mix_tiers": {
              "k1": {"name": "Hausstufe", "mixes": [
                {"id": "mische", "name": "mit Mischgetränk", "sizes": [
                  {"label": "0,2 l", "l": 0.2, "price": 6.5, "spirit_cl": 4},
                  {"label": "0,4 l", "l": 0.4, "price": 9.9, "spirit_cl": 8}
                ]},
                {"id": "energie", "name": "mit Energie", "match": "Energie", "sizes": [
                  {"label": "0,2 l", "l": 0.2, "price": 7.0, "spirit_cl": 4},
                  {"label": "0,4 l", "l": 0.4, "price": 11.5, "spirit_cl": 8}
                ]}
              ]}
            },
            "drinks": [
              {"id": "schenke-hell", "name": "Schenke Hell", "category": "bier", "abv": 5.0,
               "sizes": [{"label": "0,3 l", "l": 0.3, "price": 3.4}, {"label": "0,5 l", "l": 0.5, "price": $hell}],
               "allergene": ["gluten"]},
              {"id": "schenke-dunkel", "name": "Schenke Dunkel", "category": "bier", "abv": 5.2,
               "sizes": [{"label": "0,5 l", "l": 0.5, "price": 4.8}]},
              {"id": "nebelgeist", "name": "Nebelgeist", "category": "schnaps", "abv": 38, "tier": "k1",
               "sizes": [{"label": "2 cl", "l": 0.02, "price": 2.8}]},
              {"id": "kraeuterbitter", "name": "Kräuterbitter", "category": "schnaps", "abv": 32, "tier": "k1",
               "sizes": [{"label": "2 cl", "l": 0.02, "price": 3.0}]},
              {"id": "zitronenlimo", "name": "Zitronenlimo", "category": "alkoholfrei",
               "sizes": [{"label": "0,33 l", "l": 0.33, "price": 3.1}], "zusatzstoffe": ["koffein"]},
              {"id": "blitz-energie", "name": "Blitz Energie", "category": "alkoholfrei",
               "sizes": [{"label": "0,25 l", "l": 0.25, "price": 3.9}]},
              {"id": "wasser", "name": "Wasser", "category": "alkoholfrei", "mixer": false,
               "sizes": [{"label": "0,5 l", "l": 0.5, "price": 2.5}]},
              {"id": "brezel", "name": "Brezel", "category": "snacks",
               "sizes": [{"label": "Stück", "price": 1.5}]},
              {"id": "altbestand", "name": "Altbestand", "category": "bier", "hidden": true,
               "sizes": [{"label": "0,5 l", "l": 0.5, "price": 3.0}]}
            ]
          }
        }
    """.trimIndent()
}

val HELL = DrinkId("schenke-hell")
val DUNKEL = DrinkId("schenke-dunkel")
val NEBELGEIST = DrinkId("nebelgeist")
val KRAEUTERBITTER = DrinkId("kraeuterbitter")
val LIMO = DrinkId("zitronenlimo")
val ENERGIE = DrinkId("blitz-energie")
val WASSER = DrinkId("wasser")
val BREZEL = DrinkId("brezel")

/** A place with the invented menu. */
fun TestRoundbook.placeWithMenu(name: String = "Erfundene Schenke"): Place {
    val place = (roundbook.places.create(name) as PlaceResult.Done).place
    check(roundbook.menus.importMenu(place.id, schenkeFile()) is MenuImportResult.Imported)
    return place
}

fun TestRoundbook.startEvening(place: Place): Evening =
    (roundbook.evenings.start(place.id) as StartResult.Started).evening

fun TestRoundbook.book(evening: Evening, choice: DrinkChoice, onTheHouse: Boolean = false): Entry {
    val result = roundbook.entries.book(evening.id, choice, onTheHouse)
    check(result is BookResult.Booked) { "not booked: $result" }
    return result.entry
}

fun TestRoundbook.bookHell(evening: Evening, size: String = "0,5 l"): Entry = book(evening, DrinkChoice.FromMenu(HELL, size))

/** Milliseconds of a UTC date and time (the tests run in UTC). */
@OptIn(kotlin.time.ExperimentalTime::class)
fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
    kotlinx.datetime.LocalDateTime(year, month, day, hour, minute)
        .toInstant(kotlinx.datetime.TimeZone.UTC)
        .toEpochMilliseconds()

const val MINUTE: Long = 60_000L
const val HOUR: Long = 60 * MINUTE
const val DAY: Long = 24 * HOUR
