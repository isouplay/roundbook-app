package io.github.isouplay.roundbook.ui.today

import androidx.compose.runtime.Composable
import io.github.isouplay.roundbook.core.menu.Additive
import io.github.isouplay.roundbook.core.menu.Allergen
import io.github.isouplay.roundbook.core.menu.Drink
import io.github.isouplay.roundbook.core.menu.Labelling
import io.github.isouplay.roundbook.core.menu.Menu
import io.github.isouplay.roundbook.core.menu.MenuLimits
import io.github.isouplay.roundbook.core.menu.Size
import io.github.isouplay.roundbook.core.menu.isMixable
import io.github.isouplay.roundbook.core.menu.mixOffer
import io.github.isouplay.roundbook.core.menu.mixers
import io.github.isouplay.roundbook.domain.entry.BookProblem
import io.github.isouplay.roundbook.domain.entry.EntrySnapshot
import io.github.isouplay.roundbook.domain.favorite.FavoriteItem
import io.github.isouplay.roundbook.ui.amountOf
import io.github.isouplay.roundbook.ui.formatPrice
import io.github.isouplay.roundbook.ui.formatPriceRange
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.additive_antioxidant
import roundbook.app.shared.generated.resources.additive_caffeine
import roundbook.app.shared.generated.resources.additive_colouring
import roundbook.app.shared.generated.resources.additive_flavour_enhancer
import roundbook.app.shared.generated.resources.additive_phenylalanine
import roundbook.app.shared.generated.resources.additive_phosphate
import roundbook.app.shared.generated.resources.additive_preservative
import roundbook.app.shared.generated.resources.additive_quinine
import roundbook.app.shared.generated.resources.additive_sulphurated
import roundbook.app.shared.generated.resources.additive_sweetener
import roundbook.app.shared.generated.resources.allergen_celery
import roundbook.app.shared.generated.resources.allergen_crustaceans
import roundbook.app.shared.generated.resources.allergen_eggs
import roundbook.app.shared.generated.resources.allergen_fish
import roundbook.app.shared.generated.resources.allergen_gluten
import roundbook.app.shared.generated.resources.allergen_lupin
import roundbook.app.shared.generated.resources.allergen_milk
import roundbook.app.shared.generated.resources.allergen_molluscs
import roundbook.app.shared.generated.resources.allergen_mustard
import roundbook.app.shared.generated.resources.allergen_peanuts
import roundbook.app.shared.generated.resources.allergen_sesame
import roundbook.app.shared.generated.resources.allergen_soy
import roundbook.app.shared.generated.resources.allergen_sulphites
import roundbook.app.shared.generated.resources.allergen_tree_nuts
import roundbook.app.shared.generated.resources.book_abv_out_of_range
import roundbook.app.shared.generated.resources.book_name_empty
import roundbook.app.shared.generated.resources.book_name_too_long
import roundbook.app.shared.generated.resources.book_no_menu
import roundbook.app.shared.generated.resources.book_not_mixable
import roundbook.app.shared.generated.resources.book_not_running
import roundbook.app.shared.generated.resources.book_price_out_of_range
import roundbook.app.shared.generated.resources.book_unknown_drink
import roundbook.app.shared.generated.resources.book_unknown_mixer
import roundbook.app.shared.generated.resources.book_unknown_size
import roundbook.app.shared.generated.resources.book_volume_out_of_range
import roundbook.app.shared.generated.resources.entry_mix_name
import roundbook.app.shared.generated.resources.entry_name_size
import roundbook.app.shared.generated.resources.labelling_additives
import roundbook.app.shared.generated.resources.labelling_and
import roundbook.app.shared.generated.resources.labelling_contains
import roundbook.app.shared.generated.resources.labelling_missing
import roundbook.app.shared.generated.resources.labelling_none
import roundbook.app.shared.generated.resources.price_from
import roundbook.app.shared.generated.resources.unit_cl
import roundbook.app.shared.generated.resources.unit_l

// Texts of the Today screen built from data: names of entries and mixes, amounts, prices on tiles, allergens.
// Every word comes from the resources; the composable and the suspend variant (for messages) give the same.

/** The asterisk behind a name: the drink contains allergens or additives (a text character, like on the menu). */
const val ASTERISK: String = "*"

fun allergenText(a: Allergen): StringResource = when (a) {
    Allergen.GLUTEN -> Res.string.allergen_gluten
    Allergen.CRUSTACEANS -> Res.string.allergen_crustaceans
    Allergen.EGGS -> Res.string.allergen_eggs
    Allergen.FISH -> Res.string.allergen_fish
    Allergen.PEANUTS -> Res.string.allergen_peanuts
    Allergen.SOY -> Res.string.allergen_soy
    Allergen.MILK -> Res.string.allergen_milk
    Allergen.TREE_NUTS -> Res.string.allergen_tree_nuts
    Allergen.CELERY -> Res.string.allergen_celery
    Allergen.MUSTARD -> Res.string.allergen_mustard
    Allergen.SESAME -> Res.string.allergen_sesame
    Allergen.SULPHITES -> Res.string.allergen_sulphites
    Allergen.LUPIN -> Res.string.allergen_lupin
    Allergen.MOLLUSCS -> Res.string.allergen_molluscs
}

fun additiveText(a: Additive): StringResource = when (a) {
    Additive.COLOURING -> Res.string.additive_colouring
    Additive.PRESERVATIVE -> Res.string.additive_preservative
    Additive.ANTIOXIDANT -> Res.string.additive_antioxidant
    Additive.FLAVOUR_ENHANCER -> Res.string.additive_flavour_enhancer
    Additive.SULPHURATED -> Res.string.additive_sulphurated
    Additive.PHOSPHATE -> Res.string.additive_phosphate
    Additive.SWEETENER -> Res.string.additive_sweetener
    Additive.PHENYLALANINE -> Res.string.additive_phenylalanine
    Additive.CAFFEINE -> Res.string.additive_caffeine
    Additive.QUININE -> Res.string.additive_quinine
}

/** "Enthält: Milch · Schalenfrüchte · Zusatzstoffe: mit Farbstoff", or "Keine kennzeichnungspflichtigen …". */
@Composable
fun labellingText(labelling: Labelling): String {
    if (!labelling.containsSomething) return stringResource(Res.string.labelling_none)
    val allergens = labelling.allergens.map { stringResource(allergenText(it)) }
    val additives = labelling.additives.map { stringResource(additiveText(it)) }
    val parts = mutableListOf<String>()
    if (allergens.isNotEmpty()) parts += stringResource(Res.string.labelling_contains, allergens.joinToString(" · "))
    if (additives.isNotEmpty()) parts += stringResource(Res.string.labelling_additives, additives.joinToString(" · "))
    return parts.joinToString(" · ")
}

/** "Keine Angabe zu Likör und Saft". */
@Composable
fun missingLabellingText(names: List<String>): String {
    val joined = when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> stringResource(Res.string.labelling_and, names.dropLast(1).joinToString(", "), names.last())
    }
    return stringResource(Res.string.labelling_missing, joined)
}

fun nameWithAsterisk(name: String, labelling: Labelling?): String =
    if (labelling?.containsSomething == true) name + ASTERISK else name

/** Name of an entry: "Nebelgeist mit Blitz Energie", "Nebelgeist mit Mischgetränk" or the drink's name. */
@Composable
fun entryName(s: EntrySnapshot): String = when {
    s.mixerName != null -> stringResource(Res.string.entry_mix_name, s.name, s.mixerName!!)
    s.mixName != null -> s.name + " " + s.mixName
    else -> s.name
}

suspend fun entryNameText(s: EntrySnapshot): String = when {
    s.mixerName != null -> getString(Res.string.entry_mix_name, s.name, s.mixerName!!)
    s.mixName != null -> s.name + " " + s.mixName
    else -> s.name
}

/** Name of a mix tile: "Nebelgeist mit Blitz Energie" or "Nebelgeist mit Mischgetränk". */
@Composable
fun mixName(item: FavoriteItem.OfMix): String =
    item.mixer?.let { stringResource(Res.string.entry_mix_name, item.spirit.name, it.name) }
        ?: (item.spirit.name + " " + item.generalMixName)

/** "2 cl", "0,5 l". */
@Composable
fun amountText(liters: Double): String {
    val a = amountOf(liters)
    return stringResource(if (a.centilitres) Res.string.unit_cl else Res.string.unit_l, a.number)
}

suspend fun amountTextOf(liters: Double): String {
    val a = amountOf(liters)
    return getString(if (a.centilitres) Res.string.unit_cl else Res.string.unit_l, a.number)
}

/** The size of an entry: its label, for a free entry the amount. */
@Composable
fun entrySizeText(s: EntrySnapshot): String = s.sizeLabel.ifEmpty { s.liters?.let { amountText(it) } ?: "" }

/** "Schenke Hell 0,5 l" – for messages. */
suspend fun entryWithSizeText(s: EntrySnapshot): String {
    val size = s.sizeLabel.ifEmpty { s.liters?.let { amountTextOf(it) } ?: "" }
    val name = entryNameText(s)
    return if (size.isEmpty()) name else getString(Res.string.entry_name_size, name, size)
}

/** The amount of a size on a tile: from its liters, otherwise its label. */
@Composable
private fun sizeAmount(size: Size): String = size.liters?.takeIf { it > 0.0 }?.let { amountText(it) } ?: size.label

/**
 * The price line of a drink tile, like the old app: one size "0,5 l · 4,60 €"; several "3,40–4,60 €";
 * a drink with mixes the cheapest pure size "2 cl · 2,80 €" (several pure sizes: "ab 2,80 €").
 */
@Composable
fun drinkPriceText(menu: Menu, drink: Drink): String {
    val sizes = drink.sizes
    if (sizes.isEmpty()) return ""
    if (menu.isMixable(drink)) {
        val cheapest = sizes.minBy { it.priceCents }
        val price = formatPrice(cheapest.priceCents, menu.currency)
        val shown = if (sizes.size == 1) price else stringResource(Res.string.price_from, price)
        return listOf(sizeAmount(cheapest), shown).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    if (sizes.size == 1) {
        return listOf(sizes[0].label, formatPrice(sizes[0].priceCents, menu.currency)).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    return formatPriceRange(sizes.minOf { it.priceCents }, sizes.maxOf { it.priceCents }, menu.currency)
}

/** The price line of a mix tile: the prices with this mixer, the same calculation as the sheet. */
@Composable
fun mixPriceText(menu: Menu, item: FavoriteItem.OfMix): String {
    val offers = item.offers
    if (offers.size == 1) {
        val o = offers.single()
        val amount = o.size.liters?.let { amountText(it) } ?: o.size.label
        return listOf(amount, formatPrice(o.size.priceCents, menu.currency)).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    return formatPriceRange(offers.minOf { it.size.priceCents }, offers.maxOf { it.size.priceCents }, menu.currency)
}

/** Price of a mix size label over all mixers: one price, or "ab" the lowest. */
@Composable
fun mixSizePriceText(menu: Menu, drink: Drink, label: String): String {
    val prices = (listOf<String?>(null) + menu.mixers().map { it.name })
        .mapNotNull { menu.mixOffer(drink, label, it)?.size?.priceCents }
        .distinct()
    if (prices.isEmpty()) return ""
    val min = prices.min()
    val price = formatPrice(min, menu.currency)
    return if (prices.size == 1) price else stringResource(Res.string.price_from, price)
}

suspend fun bookProblemText(problem: BookProblem): String = when (problem) {
    BookProblem.NOT_RUNNING -> getString(Res.string.book_not_running)
    BookProblem.NO_MENU -> getString(Res.string.book_no_menu)
    BookProblem.UNKNOWN_DRINK -> getString(Res.string.book_unknown_drink)
    BookProblem.UNKNOWN_SIZE -> getString(Res.string.book_unknown_size)
    BookProblem.NOT_MIXABLE -> getString(Res.string.book_not_mixable)
    BookProblem.UNKNOWN_MIXER -> getString(Res.string.book_unknown_mixer)
    BookProblem.NAME_EMPTY -> getString(Res.string.book_name_empty)
    BookProblem.NAME_TOO_LONG -> getString(Res.string.book_name_too_long, MenuLimits.NAME_MAX.toString())
    BookProblem.PRICE_OUT_OF_RANGE -> getString(Res.string.book_price_out_of_range)
    BookProblem.VOLUME_OUT_OF_RANGE -> getString(Res.string.book_volume_out_of_range)
    BookProblem.ABV_OUT_OF_RANGE -> getString(Res.string.book_abv_out_of_range)
}
