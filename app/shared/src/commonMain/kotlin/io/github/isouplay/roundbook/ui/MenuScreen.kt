package io.github.isouplay.roundbook.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.core.menu.Drink
import io.github.isouplay.roundbook.core.menu.Menu
import io.github.isouplay.roundbook.core.menu.mixesOf
import io.github.isouplay.roundbook.core.menu.sections
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.Roundbook
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.back
import roundbook.app.shared.generated.resources.menu_empty
import roundbook.app.shared.generated.resources.menu_title
import roundbook.app.shared.generated.resources.menu_valid_from
import roundbook.app.shared.generated.resources.place_menu_none

/** Screen 3: the current menu of a place – categories, drinks, sizes and prices. Only to look at. */
@Composable
fun MenuScreen(roundbook: Roundbook, placeId: PlaceId, onBack: () -> Unit) {
    val place = remember(placeId) { roundbook.places.get(placeId) }
    val version = remember(placeId) { roundbook.menus.current(placeId) }
    val sections = remember(version) { version?.menu?.sections().orEmpty() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.back)) }
        Text(
            stringResource(Res.string.menu_title, place?.name ?: ""),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (version == null) {
            Text(stringResource(Res.string.place_menu_none))
            return@Column
        }
        Text(stringResource(Res.string.menu_valid_from, formatDate(version.info.validFrom)))
        if (sections.isEmpty()) {
            Text(stringResource(Res.string.menu_empty))
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            sections.forEach { section ->
                item(key = "category:${section.category.id.value}") {
                    Text(
                        text = listOf(section.category.icon, section.category.name).filter { it.isNotEmpty() }.joinToString(" "),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    HorizontalDivider()
                }
                items(section.drinks, key = { "drink:${it.id.value}" }) { drink ->
                    DrinkRow(version.menu, drink)
                }
            }
        }
    }
}

@Composable
private fun DrinkRow(menu: Menu, drink: Drink) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(drink.name, style = MaterialTheme.typography.titleMedium)
        for (size in drink.sizes) {
            Text(listOf(size.label, formatPrice(size.priceCents, menu.currency)).filter { it.isNotEmpty() }.joinToString(" · "))
        }
        for (mix in menu.mixesOf(drink)) {
            for (size in mix.sizes) {
                Text(
                    listOf(mix.name, size.label, formatPrice(size.priceCents, menu.currency))
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                )
            }
        }
    }
}
