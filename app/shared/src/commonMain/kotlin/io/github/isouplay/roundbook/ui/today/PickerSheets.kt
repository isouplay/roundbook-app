package io.github.isouplay.roundbook.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.core.menu.Drink
import io.github.isouplay.roundbook.core.menu.Menu
import io.github.isouplay.roundbook.core.menu.mixOffer
import io.github.isouplay.roundbook.core.menu.mixSizeLabels
import io.github.isouplay.roundbook.core.menu.mixers
import io.github.isouplay.roundbook.core.menu.sizesByLiters
import io.github.isouplay.roundbook.core.menu.sortedByName
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.favorite.FavoriteItem
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import io.github.isouplay.roundbook.ui.formatPrice
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.cancel
import roundbook.app.shared.generated.resources.choose_size
import roundbook.app.shared.generated.resources.favorite_pin
import roundbook.app.shared.generated.resources.favorite_unpin
import roundbook.app.shared.generated.resources.hold_on_the_house
import roundbook.app.shared.generated.resources.hold_on_the_house_hint
import roundbook.app.shared.generated.resources.hold_pin
import roundbook.app.shared.generated.resources.hold_unpin
import roundbook.app.shared.generated.resources.mix_any
import roundbook.app.shared.generated.resources.mix_first_size
import roundbook.app.shared.generated.resources.mix_pure
import roundbook.app.shared.generated.resources.mix_second_mixer
import roundbook.app.shared.generated.resources.on_the_house_choose_size
import roundbook.app.shared.generated.resources.size_standard

/**
 * The sheets of the picker (sizes, mix, long press). [onChoice] books (or, when changing an entry, takes
 * the new drink); [onTogglePin] pins or unpins a favourite, with its name for the message.
 */
@Composable
fun PickerSheets(
    state: PickerState,
    model: PickerModel,
    onChoice: (DrinkChoice, Boolean) -> Unit,
    onTogglePin: (FavoriteKey, String) -> Unit,
) {
    val close = { state.sheet = null }
    val choose: (DrinkChoice, Boolean) -> Unit = { choice, house ->
        state.sheet = null
        onChoice(choice, house)
    }
    when (val sheet = state.sheet) {
        null -> Unit
        is PickerSheet.Sizes -> SizesSheet(model, sheet.drink, sheet.onTheHouse, close, choose, onTogglePin)
        is PickerSheet.Mix -> MixSheet(model, sheet.drink, sheet.onTheHouse, close, choose, onTogglePin)
        is PickerSheet.MixFavorite -> MixFavoriteSheet(model.menu, sheet.item, sheet.onTheHouse, close, choose)
        is PickerSheet.Hold -> HoldSheet(model, sheet.drink, close, onTogglePin) {
            state.sheet = null
            state.tap(model.menu, sheet.drink, onTheHouse = true, onChoice = onChoice)
        }
        is PickerSheet.HoldMix -> HoldMixSheet(model, sheet.item, close, onTogglePin) {
            state.sheet = PickerSheet.MixFavorite(sheet.item, onTheHouse = true)
        }
    }
}

@Composable
private fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
    )
}

@Composable
private fun ChoiceButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
private fun PinButton(pinned: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(if (pinned) Res.string.favorite_unpin else Res.string.favorite_pin))
    }
}

@Composable
private fun SizesSheet(
    model: PickerModel,
    drink: Drink,
    onTheHouse: Boolean,
    close: () -> Unit,
    choose: (DrinkChoice, Boolean) -> Unit,
    onTogglePin: (FavoriteKey, String) -> Unit,
) {
    val menu = model.menu
    val key = FavoriteKey.OfDrink(drink.id)
    Sheet(nameWithAsterisk(drink.name, drink.labelling), close) {
        Text(
            when {
                onTheHouse -> stringResource(Res.string.on_the_house_choose_size)
                drink.note.isNotEmpty() -> drink.note
                else -> stringResource(Res.string.choose_size)
            },
        )
        for (size in drink.sizesByLiters()) {
            val label = size.label.ifEmpty { stringResource(Res.string.size_standard) }
            ChoiceButton("$label · ${formatPrice(size.priceCents, menu.currency)}") {
                choose(DrinkChoice.FromMenu(drink.id, size.label), onTheHouse)
            }
        }
        PinButton(key in model.pinned) {
            close()
            onTogglePin(key, drink.name)
        }
    }
}

@Composable
private fun MixSheet(
    model: PickerModel,
    drink: Drink,
    onTheHouse: Boolean,
    close: () -> Unit,
    choose: (DrinkChoice, Boolean) -> Unit,
    onTogglePin: (FavoriteKey, String) -> Unit,
) {
    val menu = model.menu
    var label by remember(drink.id) { mutableStateOf<String?>(null) }
    val key = FavoriteKey.OfDrink(drink.id)
    Sheet(nameWithAsterisk(drink.name, drink.labelling), close) {
        when {
            onTheHouse -> Text(stringResource(Res.string.on_the_house_choose_size))
            drink.note.isNotEmpty() -> Text(drink.note)
        }
        Text(stringResource(Res.string.mix_pure), style = MaterialTheme.typography.titleSmall)
        for (size in drink.sizesByLiters()) {
            val text = size.label.ifEmpty { stringResource(Res.string.size_standard) }
            ChoiceButton("$text · ${formatPrice(size.priceCents, menu.currency)}") {
                choose(DrinkChoice.FromMenu(drink.id, size.label), onTheHouse)
            }
        }
        Text(stringResource(Res.string.mix_first_size), style = MaterialTheme.typography.titleSmall)
        for (l in menu.mixSizeLabels(drink)) {
            val marker = if (l == label) "▸ " else ""
            ChoiceButton("$marker$l · ${mixSizePriceText(menu, drink, l)}") { label = l }
        }
        val chosen = label
        if (chosen != null) MixerCells(model, drink, chosen, onTheHouse, choose, onTogglePin)
        PinButton(key in model.pinned) {
            close()
            onTogglePin(key, drink.name)
        }
    }
}

/**
 * The mixers for a mix size, a calm list like package 23 of the old app: "egal / anderes" first, then the
 * mixers with their own price (Red Bull), then all others – each alphabetically. Long press pins the mix.
 */
@Composable
private fun MixerCells(
    model: PickerModel,
    drink: Drink,
    label: String,
    onTheHouse: Boolean,
    choose: (DrinkChoice, Boolean) -> Unit,
    onTogglePin: (FavoriteKey, String) -> Unit,
) {
    val menu = model.menu
    val general = menu.mixOffer(drink, label, null) ?: return
    val all = menu.mixers().sortedByName { it.name }
    val special = all.filter { menu.mixOffer(drink, label, it.name)?.mix?.id != general.mix.id }
    val cells: List<Drink?> = listOf<Drink?>(null) + special + all.filter { it !in special }
    Text(
        stringResource(Res.string.mix_second_mixer, label, formatPrice(general.size.priceCents, menu.currency)),
        style = MaterialTheme.typography.titleSmall,
    )
    for (mixer in cells) {
        val offer = menu.mixOffer(drink, label, mixer?.name) ?: continue
        val name = mixer?.let { nameWithAsterisk(it.name, it.labelling) } ?: stringResource(Res.string.mix_any)
        val ownPrice = offer.mix.id != general.mix.id
        val key = FavoriteKey.OfMix(drink.id, mixer?.id)
        val mixTitle = mixer?.name ?: stringResource(Res.string.mix_any)
        MixerCell(
            text = if (ownPrice) "$name · ${formatPrice(offer.size.priceCents, menu.currency)}" else name,
            pinned = key in model.pinned,
            onTap = { choose(DrinkChoice.AsMix(drink.id, label, mixer?.id), onTheHouse) },
            onHold = { onTogglePin(key, "${drink.name} · $mixTitle") },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MixerCell(text: String, pinned: Boolean, onTap: () -> Unit, onHold: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (pinned) 2.dp else 1.dp, MaterialTheme.colorScheme.outline)
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(12.dp),
    )
}

@Composable
private fun MixFavoriteSheet(
    menu: Menu,
    item: FavoriteItem.OfMix,
    onTheHouse: Boolean,
    close: () -> Unit,
    choose: (DrinkChoice, Boolean) -> Unit,
) {
    Sheet(nameWithAsterisk(mixName(item), mixLabelling(item).labelling), close) {
        if (onTheHouse) Text(stringResource(Res.string.on_the_house_choose_size))
        for (offer in item.offers) {
            val amount = offer.size.liters?.let { amountText(it) } ?: offer.size.label
            ChoiceButton("$amount · ${formatPrice(offer.size.priceCents, menu.currency)}") {
                choose(DrinkChoice.AsMix(item.spirit.id, offer.size.label, item.mixer?.id), onTheHouse)
            }
        }
    }
}

@Composable
private fun HoldSheet(
    model: PickerModel,
    drink: Drink,
    close: () -> Unit,
    onTogglePin: (FavoriteKey, String) -> Unit,
    onOnTheHouse: () -> Unit,
) {
    val key = FavoriteKey.OfDrink(drink.id)
    Sheet(nameWithAsterisk(drink.name, drink.labelling), close) {
        drink.labelling?.let { Text(labellingText(it), style = MaterialTheme.typography.bodyMedium) }
        HoldActions(pinned = key in model.pinned, onOnTheHouse = onOnTheHouse) {
            close()
            onTogglePin(key, drink.name)
        }
    }
}

@Composable
private fun HoldMixSheet(
    model: PickerModel,
    item: FavoriteItem.OfMix,
    close: () -> Unit,
    onTogglePin: (FavoriteKey, String) -> Unit,
    onOnTheHouse: () -> Unit,
) {
    val combined = mixLabelling(item)
    val name = mixName(item)
    Sheet(nameWithAsterisk(name, combined.labelling), close) {
        combined.labelling?.let { Text(labellingText(it), style = MaterialTheme.typography.bodyMedium) }
        if (combined.missing.isNotEmpty()) Text(missingLabellingText(combined.missing), style = MaterialTheme.typography.bodySmall)
        HoldActions(pinned = item.key in model.pinned, onOnTheHouse = onOnTheHouse) {
            close()
            onTogglePin(item.key, name)
        }
    }
}

@Composable
private fun HoldActions(pinned: Boolean, onOnTheHouse: () -> Unit, onPin: () -> Unit) {
    OutlinedButton(onClick = onOnTheHouse, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(Res.string.hold_on_the_house))
            Text(stringResource(Res.string.hold_on_the_house_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    OutlinedButton(onClick = onPin, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(stringResource(if (pinned) Res.string.hold_unpin else Res.string.hold_pin))
    }
}
