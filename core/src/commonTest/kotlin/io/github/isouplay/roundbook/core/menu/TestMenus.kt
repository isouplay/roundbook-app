package io.github.isouplay.roundbook.core.menu

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Invented menus for tests. No real menu goes into the repo.
 * "Zum Erfundenen Krug" exists nowhere; names and prices are made up.
 */
object TestMenus {

    /** An export of the old app (format roundbook-karte, format_version 1), with unknown fields sprinkled in. */
    const val KRUG_EXPORT: String = """
{
  "format": "roundbook-karte",
  "format_version": 1,
  "exported_at": "2026-09-30T18:00:00+00:00",
  "valid_from": "2026-10-01",
  "label": "Herbstkarte",
  "visibility": "private",
  "future_envelope_field": {"anything": [1, 2, 3]},
  "menu": {
    "version": 2,
    "bar": "Zum Erfundenen Krug",
    "currency": "EUR",
    "future_menu_field": true,
    "categories": [
      {"id": "bier", "name": "Bier", "icon": "🍺", "colour": "gold"},
      {"id": "schnaps", "name": "Schnaps", "icon": "🥃"},
      {"id": "alkoholfrei", "name": "Alkoholfrei", "icon": "🥤", "mixer": true},
      {"id": "snacks", "name": "Knabberzeug", "icon": "🥨", "counts_as_drink": false}
    ],
    "mix_tiers": {
      "k1": {"name": "Hausstufe", "future_tier_field": 1, "mixes": [
        {"id": "mische", "name": "mit Mischgetränk", "sizes": [
          {"label": "0,2 l", "l": 0.2, "price": 5.5, "spirit_cl": 4},
          {"label": "0,4 l", "l": 0.4, "price": 8.9, "spirit_cl": 8}
        ]},
        {"id": "energie", "name": "mit Energie", "match": "Energie", "sizes": [
          {"label": "0,2 l", "l": 0.2, "price": 6.2, "spirit_cl": 4}
        ]}
      ]}
    },
    "drinks": [
      {"id": "krug-hell", "name": "Krug Hell", "category": "bier", "abv": 4.9, "nr": 12,
       "sizes": [{"label": "0,3 l", "l": 0.3, "price": 3.2, "nr": 1}, {"label": "0,5 l", "l": 0.5, "price": 4.5}],
       "allergene": ["gluten"]},
      {"name": "Kräuter Spezial (Fass)", "category": "bier", "abv": 5.4,
       "sizes": [{"label": "0,4 l", "l": 0.4, "price": 4}]},
      {"id": "nebelgeist", "name": "Nebelgeist", "category": "schnaps", "abv": 38, "tier": "k1",
       "note": "Hausmarke", "sizes": [{"label": "2 cl", "l": 0.02, "price": 2.5}]},
      {"id": "tauchboot", "name": "Tauchboot", "category": "schnaps", "abv": 9, "ex": true,
       "sizes": [{"label": "0,2 l", "l": 0.2, "price": 4.8}]},
      {"id": "zitronenlimo", "name": "Zitronenlimo", "category": "alkoholfrei",
       "sizes": [{"label": "0,33 l", "l": 0.33, "price": 3.1}],
       "allergene": [], "zusatzstoffe": ["koffein", "farbstoff"]},
      {"id": "wasser", "name": "Wasser", "category": "alkoholfrei", "mixer": false,
       "sizes": [{"label": "0,5 l", "l": 0.5, "price": 2.0}], "allergene": [], "zusatzstoffe": []},
      {"id": "energie", "name": "Energie", "category": "alkoholfrei",
       "sizes": [{"label": "0,25 l", "l": 0.25, "price": "3.90"}]},
      {"id": "altbestand", "name": "Altbestand", "category": "bier", "hidden": true,
       "sizes": [{"label": "0,5 l", "l": 0.5, "price": 3.0}]},
      {"id": "brezel", "name": "Brezel", "category": "snacks",
       "sizes": [{"label": "Stück", "price": 1.5}]}
    ]
  }
}
"""

    /** The same menu one year later: Krug Hell costs more, the hidden drink is gone. */
    const val KRUG_EXPORT_LATER: String = """
{
  "format": "roundbook-karte",
  "format_version": 1,
  "valid_from": "2027-03-01",
  "label": "Frühjahr",
  "menu": {
    "version": 2,
    "bar": "Zum Erfundenen Krug",
    "currency": "EUR",
    "categories": [{"id": "bier", "name": "Bier", "icon": "🍺"}],
    "drinks": [
      {"id": "krug-hell", "name": "Krug Hell", "category": "bier", "abv": 4.9,
       "sizes": [{"label": "0,3 l", "l": 0.3, "price": 3.4}, {"label": "0,5 l", "l": 0.5, "price": 4.8}]}
    ]
  }
}
"""

    /** A small bare menu (flat format v2 without envelope). */
    const val BARE: String = """
{"version": 2, "currency": "EUR",
 "categories": [{"id": "bier", "name": "Bier", "icon": "🍺"}],
 "drinks": [{"id": "dunkel", "name": "Dunkles Erfunden", "category": "bier", "abv": 5.0,
             "sizes": [{"label": "0,5 l", "l": 0.5, "price": 4.5}]}]}
"""

    private val json = Json

    fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    /** A small export whose first drink, first size, envelope or menu is changed by the blocks. */
    fun bare(
        envelope: (MutableMap<String, JsonElement>) -> Unit = {},
        menu: (MutableMap<String, JsonElement>) -> Unit = {},
        drink: (MutableMap<String, JsonElement>) -> Unit = {},
        size: (MutableMap<String, JsonElement>) -> Unit = {},
    ): String {
        val m = parse(BARE).toMutableMap()
        val d = m.getValue("drinks").jsonArray[0].jsonObject.toMutableMap()
        val s = d.getValue("sizes").jsonArray[0].jsonObject.toMutableMap()
        size(s)
        d["sizes"] = JsonArray(listOf(JsonObject(s)))
        drink(d)
        m["drinks"] = JsonArray(listOf(JsonObject(d)))
        menu(m)
        val e = mutableMapOf<String, JsonElement>(
            "format" to kotlinx.serialization.json.JsonPrimitive("roundbook-karte"),
            "format_version" to kotlinx.serialization.json.JsonPrimitive(1),
            "menu" to JsonObject(m),
        )
        envelope(e)
        return JsonObject(e).toString()
    }
}
