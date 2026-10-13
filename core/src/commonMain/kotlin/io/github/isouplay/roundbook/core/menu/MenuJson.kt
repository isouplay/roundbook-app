package io.github.isouplay.roundbook.core.menu

import kotlinx.serialization.json.Json

/**
 * Storage form of a [Menu] (own model, not the file format of the old app). Unknown keys are ignored
 * so that a later version of the model can still be read by an older app.
 */
object MenuJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(menu: Menu): String = json.encodeToString(Menu.serializer(), menu)

    fun decode(text: String): Menu = json.decodeFromString(Menu.serializer(), text)
}

/** A category with its visible drinks, in menu order. */
data class MenuSection(val category: Category, val drinks: List<Drink>)

/** Categories with their visible (not hidden) drinks, in the order of the menu; empty categories left out. */
fun Menu.sections(): List<MenuSection> = categories.mapNotNull { category ->
    val visible = drinks.filter { it.categoryId == category.id && !it.hidden }
    if (visible.isEmpty()) null else MenuSection(category, visible)
}

/** The mixes a drink can be ordered as (from its mix tier); empty if it has none. */
fun Menu.mixesOf(drink: Drink): List<Mix> = drink.mixTierId?.let { mixTier(it) }?.mixes.orEmpty()
