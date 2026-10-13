package io.github.isouplay.roundbook.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.domain.EntryId
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.domain.entry.ActionKind
import io.github.isouplay.roundbook.domain.entry.BookResult
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.entry.Entries
import io.github.isouplay.roundbook.domain.entry.Entry
import io.github.isouplay.roundbook.domain.evening.Evening
import io.github.isouplay.roundbook.domain.evening.StartResult
import io.github.isouplay.roundbook.domain.favorite.FavoriteItem
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import io.github.isouplay.roundbook.domain.menu.MenuVersion
import io.github.isouplay.roundbook.domain.place.Place
import io.github.isouplay.roundbook.ui.collectLoaded
import io.github.isouplay.roundbook.ui.formatClock
import io.github.isouplay.roundbook.ui.formatPrice
import io.github.isouplay.roundbook.ui.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.booked
import roundbook.app.shared.generated.resources.booked_on_the_house
import roundbook.app.shared.generated.resources.drink_count
import roundbook.app.shared.generated.resources.duration_hours
import roundbook.app.shared.generated.resources.duration_hours_minutes
import roundbook.app.shared.generated.resources.duration_minutes
import roundbook.app.shared.generated.resources.evening_already_running
import roundbook.app.shared.generated.resources.evening_choose_place
import roundbook.app.shared.generated.resources.evening_end
import roundbook.app.shared.generated.resources.evening_end_cancel
import roundbook.app.shared.generated.resources.evening_end_confirm
import roundbook.app.shared.generated.resources.evening_end_question
import roundbook.app.shared.generated.resources.evening_end_text
import roundbook.app.shared.generated.resources.evening_ended
import roundbook.app.shared.generated.resources.evening_head
import roundbook.app.shared.generated.resources.evening_no_places
import roundbook.app.shared.generated.resources.evening_start
import roundbook.app.shared.generated.resources.evening_started
import roundbook.app.shared.generated.resources.evening_sum
import roundbook.app.shared.generated.resources.favorite_pinned
import roundbook.app.shared.generated.resources.favorite_unpinned
import roundbook.app.shared.generated.resources.free_entry
import roundbook.app.shared.generated.resources.no_menu_for_place
import roundbook.app.shared.generated.resources.today_title
import roundbook.app.shared.generated.resources.undo
import roundbook.app.shared.generated.resources.undo_nothing
import roundbook.app.shared.generated.resources.undone
import roundbook.app.shared.generated.resources.undone_many

/**
 * Today: begin an evening, or – while one runs – the head (sum, number of drinks, duration), "Zurück",
 * "Abend beenden", the picker and the entries of this evening.
 */
@Composable
fun TodayScreen(roundbook: Roundbook, notify: (String) -> Unit, onEdit: (List<EntryId>) -> Unit) {
    val running by remember { roundbook.evenings.runningFlow() }.collectLoaded()
    val places by remember { roundbook.places.allFlow() }.collectLoaded()
    val evening = (running ?: return).value
    val placeList = (places ?: return).value
    if (evening == null) {
        StartEvening(roundbook, placeList, notify)
    } else {
        RunningEvening(roundbook, evening, placeList.firstOrNull { it.id == evening.placeId }, notify, onEdit)
    }
}

@Composable
private fun StartEvening(roundbook: Roundbook, places: List<Place>, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<PlaceId?>(null) }
    val startedText = stringResource(Res.string.evening_started)
    val alreadyText = stringResource(Res.string.evening_already_running)
    LaunchedEffect(places) {
        if (chosen == null || places.none { it.id == chosen }) {
            chosen = roundbook.inBackground { evenings.lastPlaceId() } ?: places.firstOrNull()?.id
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.today_title), style = MaterialTheme.typography.headlineMedium)
        if (places.isEmpty()) {
            Text(stringResource(Res.string.evening_no_places))
            return@Column
        }
        Text(stringResource(Res.string.evening_choose_place), style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(places, key = { it.id.value }) { place ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { chosen = place.id },
                ) {
                    RadioButton(selected = place.id == chosen, onClick = { chosen = place.id })
                    Text(place.name)
                }
            }
        }
        Button(
            enabled = chosen != null,
            onClick = {
                val placeId = chosen ?: return@Button
                // The screen changes as soon as the evening runs; the messages are ready before.
                scope.launch {
                    when (roundbook.inBackground { evenings.start(placeId) }) {
                        is StartResult.Started -> notify(startedText)
                        is StartResult.AlreadyRunning -> notify(alreadyText)
                        StartResult.UnknownPlace -> Unit
                    }
                }
            },
        ) {
            Text(stringResource(Res.string.evening_start))
        }
    }
}

@Composable
private fun RunningEvening(
    roundbook: Roundbook,
    evening: Evening,
    place: Place?,
    notify: (String) -> Unit,
    onEdit: (List<EntryId>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = remember { TimeZone.currentSystemDefault() }
    val entries by remember(evening.id) { roundbook.entries.ofEveningFlow(evening.id) }.collectLoaded()
    val menu by remember(evening.placeId) { roundbook.menus.currentFlow(evening.placeId) }.collectLoaded()
    val pinned by remember(evening.placeId) { roundbook.favorites.pinnedFlow(evening.placeId) }.collectAsState(emptyList())
    val order by roundbook.favorites.order.collectAsState()
    val now by produceState(nowMillis()) {
        while (true) {
            delay(30_000)
            value = nowMillis()
        }
    }
    val list = entries?.value ?: emptyList()
    val version: MenuVersion? = menu?.value
    val favoriteRow by produceState(emptyList<FavoriteItem>(), version, pinned, order) {
        value = if (version == null) emptyList() else roundbook.inBackground { favorites.row(evening.placeId, version.menu) }
    }
    val undoable by produceState<Boolean>(false, list, now) {
        value = roundbook.inBackground { this.entries.undoable(evening.id) } != null
    }
    var confirmEnd by remember { mutableStateOf(false) }
    var freeEntry by remember { mutableStateOf(false) }
    val picker = rememberPickerState()

    val book: (DrinkChoice, Boolean) -> Unit = { choice, house ->
        scope.launch {
            when (val result = roundbook.inBackground { this.entries.book(evening.id, choice, house) }) {
                is BookResult.Booked -> notify(bookedText(result.entry))
                is BookResult.Rejected -> notify(bookProblemText(result.problem))
            }
        }
    }
    val togglePin: (FavoriteKey, String) -> Unit = { key, name ->
        scope.launch {
            val pinnedNow = roundbook.inBackground { favorites.toggle(evening.placeId, key) }
            notify(getString(if (pinnedNow) Res.string.favorite_pinned else Res.string.favorite_unpinned, name))
        }
    }
    val currency = version?.menu?.currency ?: list.firstOrNull()?.snapshot?.currency ?: "EUR"
    val summary = Entries.summary(list)
    val groups = Entries.groups(list)
    val model = version?.let { PickerModel(it.menu, Entries.countsByDrink(list), favoriteRow, pinned.toSet()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "head") {
            Column {
                Head(place?.name ?: "", evening, summary.totalCents, summary.drinkCount, currency, now, zone)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    // "Zurück" is always there; grey when nothing of the last five minutes is left.
                    OutlinedButton(
                        enabled = undoable,
                        onClick = {
                            scope.launch {
                                val result = roundbook.inBackground { this.entries.undo(evening.id) }
                                notify(if (result == null) getString(Res.string.undo_nothing) else undoneText(result.kind, result.count, result.snapshot))
                            }
                        },
                    ) { Text(stringResource(Res.string.undo)) }
                    OutlinedButton(onClick = { confirmEnd = true }) { Text(stringResource(Res.string.evening_end)) }
                }
            }
        }
        if (model != null) {
            pickerItems(picker, model, book)
        } else {
            item(key = "no-menu") { Text(stringResource(Res.string.no_menu_for_place), modifier = Modifier.padding(vertical = 8.dp)) }
        }
        item(key = "free") {
            TextButton(onClick = { freeEntry = true }) { Text(stringResource(Res.string.free_entry)) }
        }
        entryItems(
            groups = groups,
            onOpen = { onEdit(it.ids) },
            onDeleteYoungest = { group ->
                scope.launch { roundbook.inBackground { this.entries.delete(group.youngest.id) } }
            },
        )
    }

    if (model != null) PickerSheets(picker, model, book, togglePin)
    if (freeEntry) {
        FreeEntryDialog(instead = false, onDismiss = { freeEntry = false }) { choice ->
            when (val result = roundbook.inBackground { this.entries.book(evening.id, choice) }) {
                is BookResult.Booked -> {
                    notify(bookedText(result.entry))
                    null
                }
                is BookResult.Rejected -> result.problem
            }
        }
    }
    if (confirmEnd) {
        val drinks = pluralStringResource(Res.plurals.drink_count, summary.drinkCount, summary.drinkCount.toString())
        val endedText = stringResource(Res.string.evening_ended, formatPrice(summary.totalCents, currency))
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(Res.string.evening_end_question)) },
            text = { Text(stringResource(Res.string.evening_end_text, formatPrice(summary.totalCents, currency), drinks)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    // The screen changes as soon as the evening has ended; the message is ready before.
                    notify(endedText)
                    scope.launch { roundbook.inBackground { evenings.end(evening.id) } }
                }) { Text(stringResource(Res.string.evening_end_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(Res.string.evening_end_cancel)) } },
        )
    }
}

/** Head: place and beginning, sum, number of drinks, duration since the beginning. */
@Composable
private fun Head(placeName: String, evening: Evening, totalCents: Long, drinkCount: Int, currency: String, now: Long, zone: TimeZone) {
    val minutes = ((maxOf(now, evening.startedAt) - evening.startedAt) / 60_000L).toInt()
    val duration = when {
        minutes < 60 -> stringResource(Res.string.duration_minutes, minutes.toString())
        minutes % 60 == 0 -> stringResource(Res.string.duration_hours, (minutes / 60).toString())
        else -> stringResource(Res.string.duration_hours_minutes, (minutes / 60).toString(), (minutes % 60).toString())
    }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            stringResource(Res.string.evening_head, placeName, formatClock(evening.startedAt, zone)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(Res.string.evening_sum, formatPrice(totalCents, currency)), style = MaterialTheme.typography.headlineMedium)
        Text(
            listOf(pluralStringResource(Res.plurals.drink_count, drinkCount, drinkCount.toString()), duration).joinToString(" · "),
        )
    }
}

/** "Schenke Hell 0,5 l · 4,60 €" or "… · aufs Haus". */
private suspend fun bookedText(entry: Entry): String {
    val s = entry.snapshot
    val what = entryWithSizeText(s)
    return if (s.onTheHouse) getString(Res.string.booked_on_the_house, what) else getString(Res.string.booked, what, formatPrice(s.priceCents, s.currency))
}

private suspend fun undoneText(kind: ActionKind, count: Int, snapshot: io.github.isouplay.roundbook.domain.entry.EntrySnapshot?): String {
    val name = snapshot?.let { entryNameText(it) } ?: ""
    return if (count > 1 && kind == ActionKind.CHANGED) getString(Res.string.undone_many, count.toString(), name) else getString(Res.string.undone, name)
}
