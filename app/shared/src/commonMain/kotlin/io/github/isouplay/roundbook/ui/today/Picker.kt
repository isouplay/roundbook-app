package io.github.isouplay.roundbook.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.core.menu.Drink
import io.github.isouplay.roundbook.core.menu.DrinkId
import io.github.isouplay.roundbook.core.menu.Menu
import io.github.isouplay.roundbook.core.menu.isMixable
import io.github.isouplay.roundbook.core.menu.searchDrinks
import io.github.isouplay.roundbook.core.menu.visibleCategories
import io.github.isouplay.roundbook.core.menu.visibleDrinksOf
import io.github.isouplay.roundbook.core.menu.combineLabelling
import io.github.isouplay.roundbook.core.menu.CombinedLabelling
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.favorite.FavoriteItem
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.category_empty
import roundbook.app.shared.generated.resources.favorites_row
import roundbook.app.shared.generated.resources.search_nothing
import roundbook.app.shared.generated.resources.search_placeholder

/** What the picker shows: the menu, today's counters per drink, the favourite row and what is pinned. */
data class PickerModel(
    val menu: Menu,
    val counts: Map<DrinkId, Int>,
    val favorites: List<FavoriteItem>,
    val pinned: Set<FavoriteKey>,
)

/** A sheet of the picker. */
sealed interface PickerSheet {
    /** Choose one of several sizes of a drink. */
    data class Sizes(val drink: Drink, val onTheHouse: Boolean) : PickerSheet

    /** A drink with mix tier: pure sizes, or a mix size and then the mixer. */
    data class Mix(val drink: Drink, val onTheHouse: Boolean) : PickerSheet

    /** A mix from the favourite row: the mixer is set, the size is chosen. */
    data class MixFavorite(val item: FavoriteItem.OfMix, val onTheHouse: Boolean) : PickerSheet

    /** Long press on a drink: allergens, on the house, favourite. */
    data class Hold(val drink: Drink) : PickerSheet

    /** Long press on a mix of the favourite row. */
    data class HoldMix(val item: FavoriteItem.OfMix) : PickerSheet
}

/** State of the picker: search text, chosen category, open sheet. */
@Stable
class PickerState(initialCategory: String?) {
    var query by mutableStateOf("")
    var categoryId by mutableStateOf(initialCategory)
    var sheet by mutableStateOf<PickerSheet?>(null)
}

@Composable
fun rememberPickerState(): PickerState = remember { PickerState(null) }

/**
 * What tapping a drink does, like the old app: a drink with mix tier opens the mix sheet, a drink with one
 * size is booked at once, several sizes open the size sheet.
 */
fun PickerState.tap(menu: Menu, drink: Drink, onTheHouse: Boolean, onChoice: (DrinkChoice, Boolean) -> Unit) {
    when {
        menu.isMixable(drink) -> sheet = PickerSheet.Mix(drink, onTheHouse)
        drink.sizes.size == 1 -> onChoice(DrinkChoice.FromMenu(drink.id, drink.sizes.single().label), onTheHouse)
        else -> sheet = PickerSheet.Sizes(drink, onTheHouse)
    }
}

/** Search field, favourite row, category row and the tiles, as items of the screen's list. */
fun LazyListScope.pickerItems(
    state: PickerState,
    model: PickerModel,
    onChoice: (DrinkChoice, Boolean) -> Unit,
) {
    val menu = model.menu
    item(key = "picker-search") {
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            placeholder = { Text(stringResource(Res.string.search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
    val searching = state.query.isNotBlank()
    if (!searching && model.favorites.isNotEmpty()) {
        item(key = "picker-favorites") {
            Column {
                Text(stringResource(Res.string.favorites_row), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(model.favorites, key = { "fav:" + it.key.code }) { item ->
                        when (item) {
                            is FavoriteItem.OfDrink -> Tile(
                                name = nameWithAsterisk(item.drink.name, item.drink.labelling),
                                price = drinkPriceText(menu, item.drink),
                                count = null,
                                pinned = item.pinned,
                                modifier = Modifier.width(150.dp),
                                onTap = { state.tap(menu, item.drink, onTheHouse = false, onChoice = onChoice) },
                                onHold = { state.sheet = PickerSheet.Hold(item.drink) },
                            )
                            is FavoriteItem.OfMix -> Tile(
                                name = nameWithAsterisk(mixName(item), mixLabelling(item).labelling),
                                price = mixPriceText(menu, item),
                                count = null,
                                pinned = item.pinned,
                                modifier = Modifier.width(150.dp),
                                onTap = { state.sheet = PickerSheet.MixFavorite(item, onTheHouse = false) },
                                onHold = { state.sheet = PickerSheet.HoldMix(item) },
                            )
                        }
                    }
                }
            }
        }
    }
    val categories = menu.visibleCategories()
    val category = categories.firstOrNull { it.id.value == state.categoryId } ?: categories.firstOrNull()
    if (!searching && categories.isNotEmpty()) {
        item(key = "picker-categories") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { "cat:" + it.id.value }) { c ->
                    FilterChip(
                        selected = c.id == category?.id,
                        onClick = { state.categoryId = c.id.value },
                        label = { Text(listOf(c.icon, c.name).filter { it.isNotEmpty() }.joinToString(" ")) },
                    )
                }
            }
        }
    }
    val drinks = when {
        searching -> menu.searchDrinks(state.query)
        category != null -> menu.visibleDrinksOf(category.id)
        else -> emptyList()
    }
    if (drinks.isEmpty()) {
        item(key = "picker-empty") {
            Text(stringResource(if (searching) Res.string.search_nothing else Res.string.category_empty))
        }
    }
    val rows = drinks.chunked(2)
    items(rows.size, key = { "tiles:" + rows[it].first().id.value }) { index ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            for (drink in rows[index]) {
                val categoryName = if (searching) menu.category(drink.categoryId)?.name else null
                Tile(
                    name = nameWithAsterisk(drink.name, drink.labelling),
                    price = listOfNotNull(drinkPriceText(menu, drink), categoryName).joinToString(" · "),
                    count = model.counts[drink.id],
                    pinned = FavoriteKey.OfDrink(drink.id) in model.pinned,
                    modifier = Modifier.weight(1f),
                    onTap = { state.tap(menu, drink, onTheHouse = false, onChoice = onChoice) },
                    onHold = { state.sheet = PickerSheet.Hold(drink) },
                )
            }
            if (rows[index].size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Allergens of a mix: spirit and mixer together. */
fun mixLabelling(item: FavoriteItem.OfMix): CombinedLabelling = mixLabelling(item.spirit, item.mixer)

fun mixLabelling(spirit: Drink, mixer: Drink?): CombinedLabelling =
    combineLabelling(listOfNotNull(spirit.name to spirit.labelling, mixer?.let { it.name to it.labelling }))

/** One tile: name (with asterisk), price line, today's counter; tap books or opens, long press opens the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(
    name: String,
    price: String,
    count: Int?,
    pinned: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .border(if (pinned) 2.dp else 1.dp, MaterialTheme.colorScheme.outline)
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(8.dp),
    ) {
        Row {
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (count != null && count > 0) Text(count.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Text(price, style = MaterialTheme.typography.bodySmall)
    }
}
