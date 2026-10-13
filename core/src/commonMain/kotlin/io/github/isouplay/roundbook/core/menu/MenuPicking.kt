package io.github.isouplay.roundbook.core.menu

// Picking a drink from the menu, like the old app (menu.js): tiles per category, search, sizes, and the
// price of a mix from the mix tier of the drink. One place for these rules, so that the tile, the sheet and
// the booking always show and charge the same.

/** Visible drinks of a category, alphabetically. */
fun Menu.visibleDrinksOf(categoryId: CategoryId): List<Drink> =
    drinks.filter { it.categoryId == categoryId && !it.hidden }.sortedByName { it.name }

/** Visible drinks whose name or category name contains [query] (case and accents ignored), alphabetically. */
fun Menu.searchDrinks(query: String): List<Drink> {
    val q = foldForSearch(query.trim())
    if (q.isEmpty()) return emptyList()
    return drinks.filter { d ->
        !d.hidden && foldForSearch(d.name + " " + (category(d.categoryId)?.name ?: "")).contains(q)
    }.sortedByName { it.name }
}

/** Categories that have at least one visible drink, in the order of the menu. */
fun Menu.visibleCategories(): List<Category> = categories.filter { c -> drinks.any { it.categoryId == c.id && !it.hidden } }

/** Whether the drink can be ordered as a mix (it points to a mix tier with at least one mix). */
fun Menu.isMixable(drink: Drink): Boolean = mixesOf(drink).isNotEmpty()

/**
 * The mixers of the menu: visible drinks that count as mixer (tick on the drink, otherwise its category).
 * The same name only once (the first on the menu), in the order of the menu.
 */
fun Menu.mixers(): List<Drink> {
    val seen = mutableSetOf<String>()
    return drinks.filter { d ->
        val category = category(d.categoryId) ?: return@filter false
        !d.hidden && d.isMixer(category) && seen.add(d.name)
    }
}

/** A mixer of the menu by its id, or null if it is not on the menu (or hidden, or no mixer). */
fun Menu.mixer(id: DrinkId): Drink? = mixers().firstOrNull { it.id == id }

/** The sizes of a drink ordered by liters (sizes without liters last). */
fun Drink.sizesByLiters(): List<Size> = sizes.sortedByLiters { it.liters }

/** The mix sizes of a drink by label ("0,2 l", "0,4 l" …), ordered by liters; empty if it has no mix tier. */
fun Menu.mixSizeLabels(drink: Drink): List<String> {
    val all = mixesOf(drink).flatMap { it.sizes }.sortedByLiters { it.liters }
    return all.map { it.label }.distinct()
}

/** One mix size with the mix (option of the tier) it belongs to. */
data class MixOffer(val mix: Mix, val size: MixSize)

/**
 * Which mix – and with it which price – a mixer gets at this size: the mix whose search word ([Mix.match])
 * is contained in the mixer's name ("Red Bull"), otherwise the general mix. Never guessed: a mix without
 * search word is only taken as the general one, never by similar names.
 *
 * The general mix is the first mix without search word among those that have this size; if there is
 * none, the first one stands in (the menu check says so). [mixerName] null or empty means "egal / anderes".
 * Null if no mix of the tier has this size.
 */
fun Menu.mixOffer(drink: Drink, sizeLabel: String, mixerName: String?): MixOffer? {
    val offers = mixesOf(drink).mapNotNull { mix -> mix.sizes.firstOrNull { it.label == sizeLabel }?.let { MixOffer(mix, it) } }
    if (offers.isEmpty()) return null
    val general = offers.firstOrNull { it.mix.match.isEmpty() } ?: offers.first()
    val name = mixerName?.lowercase().orEmpty()
    if (name.isEmpty()) return general
    return offers.firstOrNull { it.mix.match.isNotEmpty() && name.contains(it.mix.match.lowercase()) } ?: general
}

/**
 * The general mix of the drink ("mit Mischgetränk"): the first mix of its tier without search word,
 * otherwise the first. Its name names a mix booked without mixer.
 */
fun Menu.generalMix(drink: Drink): Mix? {
    val mixes = mixesOf(drink)
    return mixes.firstOrNull { it.match.isEmpty() } ?: mixes.firstOrNull()
}
