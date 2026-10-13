package io.github.isouplay.roundbook.domain.entry

import io.github.isouplay.roundbook.core.menu.CategoryId
import io.github.isouplay.roundbook.core.menu.DrinkId
import io.github.isouplay.roundbook.core.menu.MixId
import io.github.isouplay.roundbook.core.menu.MixTierId
import io.github.isouplay.roundbook.domain.MenuVersionId
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import kotlinx.serialization.Serializable

/**
 * What an entry holds for calculating later (bill, blood alcohol, statistics), as it was at the time of
 * booking. Later menu changes do not change it.
 *
 * A mix is the spirit ([drinkId], [name], [abv]) with a mixer ([mixerId], [mixerName]; null for "egal /
 * anderes"), at the size and price of a mix of the spirit's mix tier ([mixTierId], [mixId], [spiritCl]).
 * A free entry has no [drinkId].
 */
@Serializable
data class EntrySnapshot(
    /** Name of the drink; for a mix the name of the spirit. */
    val name: String,
    /** Label of the size ("0,5 l"); empty for a free entry (then [liters] says the amount). */
    val sizeLabel: String,
    val liters: Double? = null,
    /** Price in cents of the menu's currency; 0 on the house. */
    val priceCents: Long,
    /** Currency of the price ("EUR"). */
    val currency: String = "EUR",
    /** Alcohol by volume in percent; for a mix that of the spirit. */
    val abv: Double = 0.0,
    /** Spirit in a mix, in centilitres; null if it is no mix. */
    val spiritCl: Double? = null,
    val countsAsDrink: Boolean = true,
    /** Drunk in one go. */
    val ex: Boolean = false,
    val drinkId: DrinkId? = null,
    val categoryId: CategoryId? = null,
    val mixerId: DrinkId? = null,
    val mixerName: String? = null,
    val mixTierId: MixTierId? = null,
    val mixId: MixId? = null,
    /** Name of the mix of the tier ("mit Mischgetränk"); names a mix booked without mixer. */
    val mixName: String? = null,
    val menuVersionId: MenuVersionId? = null,
    /** On the house: a drink from the menu that the bar gave for free (price 0). */
    val onTheHouse: Boolean = false,
) {
    val isMix: Boolean get() = mixId != null

    val isFree: Boolean get() = drinkId == null

    /** The favourite this entry counts for: the drink, or the mix of spirit and mixer; null for a free entry. */
    val favoriteKey: FavoriteKey?
        get() = when {
            drinkId == null -> null
            isMix -> FavoriteKey.OfMix(drinkId, mixerId)
            else -> FavoriteKey.OfDrink(drinkId)
        }
}
