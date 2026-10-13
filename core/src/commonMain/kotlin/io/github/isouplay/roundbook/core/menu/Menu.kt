package io.github.isouplay.roundbook.core.menu

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// The menu model. Fields follow the menu file of the old app (flat format v2, see MenuFileReader),
// so reading old files and the later dual mode fit. Names follow GLOSSARY.md.

/** A drink is its id: unique on the menu, never reassigned, kept when the drink is renamed. */
@Serializable
@JvmInline
value class DrinkId(val value: String)

@Serializable
@JvmInline
value class CategoryId(val value: String)

/** Id of a mix tier (`mix_tiers` key in the file, e.g. "k1"). */
@Serializable
@JvmInline
value class MixTierId(val value: String)

/** Id of a mix within its tier (e.g. "redbull"). */
@Serializable
@JvmInline
value class MixId(val value: String)

/**
 * One menu, as read from a file and as stored per menu version.
 * Prices are whole cents in [currency]; hidden drinks are kept (the menu view leaves them out).
 */
@Serializable
data class Menu(
    val bar: String,
    val currency: String,
    val categories: List<Category>,
    val mixTiers: List<MixTier>,
    val drinks: List<Drink>,
) {
    fun category(id: CategoryId): Category? = categories.firstOrNull { it.id == id }

    fun drink(id: DrinkId): Drink? = drinks.firstOrNull { it.id == id }

    fun mixTier(id: MixTierId): MixTier? = mixTiers.firstOrNull { it.id == id }
}

/**
 * A category only helps to find a drink. It also gives two defaults:
 * [countsAsDrink] (false e.g. for snacks) and [mixer] (its drinks can be mixed into a mix).
 */
@Serializable
data class Category(
    val id: CategoryId,
    val name: String,
    val icon: String,
    val countsAsDrink: Boolean,
    val mixer: Boolean,
)

@Serializable
data class Drink(
    val id: DrinkId,
    val name: String,
    val categoryId: CategoryId,
    /** Alcohol by volume in percent, 0 if not given. */
    val abv: Double,
    val sizes: List<Size>,
    val note: String,
    val hidden: Boolean,
    /** Mix tier: the drink can be ordered as a mix at the prices of that tier. */
    val mixTierId: MixTierId?,
    /** "As mixer" tick on the drink; null means the category decides. */
    val mixer: Boolean?,
    /** Drunk in one go (e.g. a shot dropped into beer). */
    val ex: Boolean,
    /** Allergens and additives; null means "no information". */
    val labelling: Labelling?,
) {
    /** Whether the drink can be mixed into a mix: the tick on the drink wins over the category. */
    fun isMixer(category: Category): Boolean = mixer ?: category.mixer
}

@Serializable
data class Size(
    val label: String,
    val liters: Double?,
    val priceCents: Long,
)

/** Mix tier: prices of the mixes for all drinks that point to it. */
@Serializable
data class MixTier(
    val id: MixTierId,
    val name: String,
    /** Category name a mix is listed under; null means the default of the app. */
    val mixCategory: String?,
    val mixes: List<Mix>,
)

/** One way to mix a drink of a tier, e.g. "mit Red Bull". */
@Serializable
data class Mix(
    val id: MixId,
    val name: String,
    /** Search word: which mixer draws this price (e.g. "Red Bull"); empty for the general option. */
    val match: String,
    val category: String?,
    val sizes: List<MixSize>,
)

@Serializable
data class MixSize(
    val label: String,
    val liters: Double?,
    val priceCents: Long,
    /** Spirit in the mix, in centilitres. */
    val spiritCl: Double,
)
