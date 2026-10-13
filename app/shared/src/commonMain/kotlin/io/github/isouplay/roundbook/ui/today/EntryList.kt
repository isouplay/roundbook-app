package io.github.isouplay.roundbook.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.domain.entry.EntryGroup
import io.github.isouplay.roundbook.ui.formatPrice
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.delete_hold
import roundbook.app.shared.generated.resources.group_count
import roundbook.app.shared.generated.resources.group_each
import roundbook.app.shared.generated.resources.group_no_price
import roundbook.app.shared.generated.resources.group_on_the_house
import roundbook.app.shared.generated.resources.nothing_booked
import roundbook.app.shared.generated.resources.this_evening

/** How long the minus must be held to delete. */
const val HOLD_TO_DELETE_MILLIS: Int = 1000

/**
 * "Dieser Abend": entries grouped by drink, size and price ("3× Spezi 0,4 l"). Tapping a row opens the change
 * sheet; holding the minus for about a second deletes the youngest entry of the group. On the house is shown
 * with a different background.
 */
fun LazyListScope.entryItems(
    groups: List<EntryGroup>,
    onOpen: (EntryGroup) -> Unit,
    onDeleteYoungest: (EntryGroup) -> Unit,
) {
    item(key = "evening-title") {
        Column {
            Text(
                stringResource(Res.string.this_evening),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            HorizontalDivider()
        }
    }
    if (groups.isEmpty()) {
        item(key = "evening-empty") { Text(stringResource(Res.string.nothing_booked), modifier = Modifier.padding(vertical = 8.dp)) }
    }
    items(groups, key = { "group:" + it.entries.first().id.value }) { group ->
        Column {
            GroupRow(group, onOpen = { onOpen(group) }, onDelete = { onDeleteYoungest(group) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun GroupRow(group: EntryGroup, onOpen: () -> Unit, onDelete: () -> Unit) {
    val s = group.snapshot
    val house = s.onTheHouse
    val size = entrySizeText(s)
    val hint = if (house) stringResource(Res.string.group_on_the_house) else stringResource(Res.string.group_each, formatPrice(s.priceCents, s.currency))
    val name = entryName(s)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (house) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Text(stringResource(Res.string.group_count, group.count.toString()))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(listOf(size, hint).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        Text(if (house) stringResource(Res.string.group_no_price) else formatPrice(group.totalCents, s.currency))
        HoldToDeleteButton(contentDescription = stringResource(Res.string.delete_hold, name), onDelete = onDelete)
    }
}

/**
 * Minus that deletes when held for about a second: it fills from left to right; letting go earlier cancels and
 * the filling runs back. A short tap lets it start and fall back. No question asked – there is "Zurück".
 */
@Composable
fun HoldToDeleteButton(contentDescription: String, onDelete: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnDelete by rememberUpdatedState(onDelete)
    val fill = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .border(1.dp, outline)
            .drawBehind { drawRect(fill, size = Size(size.width * progress.value, size.height)) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val holding = scope.launch {
                            progress.animateTo(
                                1f,
                                tween(durationMillis = ((1f - progress.value) * HOLD_TO_DELETE_MILLIS).toInt(), easing = LinearEasing),
                            )
                            currentOnDelete()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (holding.isActive) {
                            holding.cancel()
                            scope.launch { progress.animateTo(0f, tween(durationMillis = 200)) }
                        }
                    },
                )
            },
    ) {
        Text("−")
    }
}
