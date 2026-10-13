package io.github.isouplay.roundbook.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.domain.entry.BookProblem
import io.github.isouplay.roundbook.domain.entry.ChangeProblem
import io.github.isouplay.roundbook.domain.entry.ChangeResult
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.domain.entry.Entries
import io.github.isouplay.roundbook.domain.entry.Entry
import io.github.isouplay.roundbook.domain.entry.TimeResult
import io.github.isouplay.roundbook.domain.evening.Evening
import io.github.isouplay.roundbook.domain.favorite.FavoriteItem
import io.github.isouplay.roundbook.domain.favorite.FavoriteKey
import io.github.isouplay.roundbook.ui.collectLoaded
import io.github.isouplay.roundbook.ui.formatClock
import io.github.isouplay.roundbook.ui.parseClock
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.back
import roundbook.app.shared.generated.resources.edit_all
import roundbook.app.shared.generated.resources.edit_changed
import roundbook.app.shared.generated.resources.edit_changed_many
import roundbook.app.shared.generated.resources.edit_changed_time
import roundbook.app.shared.generated.resources.edit_gone
import roundbook.app.shared.generated.resources.edit_instead
import roundbook.app.shared.generated.resources.edit_one
import roundbook.app.shared.generated.resources.edit_time
import roundbook.app.shared.generated.resources.edit_time_apply
import roundbook.app.shared.generated.resources.edit_title
import roundbook.app.shared.generated.resources.edit_unchanged
import roundbook.app.shared.generated.resources.favorite_pinned
import roundbook.app.shared.generated.resources.favorite_unpinned
import roundbook.app.shared.generated.resources.free_entry_instead
import roundbook.app.shared.generated.resources.time_after_end
import roundbook.app.shared.generated.resources.time_before_start
import roundbook.app.shared.generated.resources.time_in_future
import roundbook.app.shared.generated.resources.time_invalid

/**
 * Change sheet of a row of "Dieser Abend": swap the drink and/or correct the time, for all entries of the group
 * or one. Swapping keeps time, evening and "on the house"; the same drink with another time changes only the
 * time. The time field shows the time of the entry drunk last.
 */
@Composable
fun EditEntriesScreen(roundbook: Roundbook, ids: List<EntryId>, notify: (String) -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val zone = remember { TimeZone.currentSystemDefault() }
    val loaded by produceState<Pair<List<Entry>, Evening?>?>(null, ids) {
        value = roundbook.inBackground {
            val entries = ids.mapNotNull { this.entries.get(it) }
            entries to entries.firstOrNull()?.let { evenings.get(it.eveningId) }
        }
    }
    val (entries, evening) = loaded ?: return
    if (entries.isEmpty() || evening == null) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onDone) { Text(stringResource(Res.string.back)) }
            Text(stringResource(Res.string.edit_gone))
        }
        return
    }
    val menu by remember(evening.placeId) { roundbook.menus.currentFlow(evening.placeId) }.collectLoaded()
    val pinned by remember(evening.placeId) { roundbook.favorites.pinnedFlow(evening.placeId) }.collectAsState(emptyList())
    val order by roundbook.favorites.order.collectAsState()
    val version = menu?.value
    val favoriteRow by produceState(emptyList<FavoriteItem>(), version, pinned, order) {
        value = if (version == null) emptyList() else roundbook.inBackground { favorites.row(evening.placeId, version.menu) }
    }

    // The group in the order of drinking; "Nur eine" changes the one drunk last.
    val youngest = Entries.groups(entries).flatMap { it.entries }.maxWith(compareBy<Entry>({ it.drunkAt }, { it.addedAt }))
    var all by remember { mutableStateOf(entries.size > 1) }
    val targets = if (all) entries.map { it.id } else listOf(youngest.id)
    var time by remember(youngest.id) { mutableStateOf(formatClock(youngest.drunkAt, zone)) }
    var freeEntry by remember { mutableStateOf(false) }
    val picker = rememberPickerState()
    val shown = youngest.snapshot
    val oldName = entryName(shown)
    val oldSize = entrySizeText(shown)

    /** Applies the change; the time only if the field was changed. */
    suspend fun apply(choice: DrinkChoice?, onTheHouse: Boolean?): BookProblem? {
        var drunkAt: Long? = null
        val clock = parseClock(time)
        if (clock == null) {
            notify(getString(Res.string.time_invalid))
            return null
        }
        when (val t = roundbook.inBackground { this.entries.timeFor(youngest, clock.first, clock.second, zone) }) {
            is TimeResult.At -> drunkAt = t.drunkAt
            TimeResult.Unchanged -> Unit
            is TimeResult.BeforeStart -> {
                notify(getString(Res.string.time_before_start, time.trim(), formatClock(t.startedAt, zone)))
                return null
            }
            TimeResult.InFuture -> {
                notify(getString(Res.string.time_in_future, time.trim()))
                return null
            }
            is TimeResult.AfterEnd -> {
                notify(getString(Res.string.time_after_end, time.trim(), formatClock(t.endedAt, zone)))
                return null
            }
        }
        val result = roundbook.inBackground { this.entries.change(targets, choice, drunkAt, onTheHouse) }
        when (result) {
            is ChangeResult.Changed -> {
                notify(
                    when {
                        result.count > 1 -> getString(Res.string.edit_changed_many, result.count.toString())
                        result.drunkAt != null -> getString(Res.string.edit_changed_time, formatClock(result.drunkAt!!, zone))
                        else -> getString(Res.string.edit_changed)
                    },
                )
                onDone()
            }
            ChangeResult.Unchanged -> {
                notify(getString(Res.string.edit_unchanged))
                onDone()
            }
            is ChangeResult.Rejected -> notify(
                when (result.problem) {
                    ChangeProblem.TIME_OUT_OF_EVENING -> getString(Res.string.time_in_future, time.trim())
                    ChangeProblem.UNKNOWN_ENTRY, ChangeProblem.DIFFERENT_EVENINGS -> getString(Res.string.edit_gone)
                },
            )
            is ChangeResult.RejectedDrink -> return result.problem
        }
        return null
    }

    val change: (DrinkChoice, Boolean) -> Unit = { choice, house ->
        scope.launch {
            // On the house only when chosen in the menu of the tile; otherwise it stays as it was.
            apply(choice, if (house) true else null)?.let { notify(bookProblemText(it)) }
        }
    }
    val togglePin: (FavoriteKey, String) -> Unit = { key, name ->
        scope.launch {
            val pinnedNow = roundbook.inBackground { favorites.toggle(evening.placeId, key) }
            notify(getString(if (pinnedNow) Res.string.favorite_pinned else Res.string.favorite_unpinned, name))
        }
    }
    val model = version?.let { PickerModel(it.menu, Entries.countsByDrink(entries), favoriteRow, pinned.toSet()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "edit-head") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onDone) { Text(stringResource(Res.string.back)) }
                Text(stringResource(Res.string.edit_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.edit_instead, listOf(oldName, oldSize).filter { it.isNotEmpty() }.joinToString(" · ")))
                if (entries.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = all, onClick = { all = true }, label = { Text(stringResource(Res.string.edit_all, entries.size.toString())) })
                        FilterChip(selected = !all, onClick = { all = false }, label = { Text(stringResource(Res.string.edit_one)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text(stringResource(Res.string.edit_time)) },
                        singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                    OutlinedButton(onClick = { scope.launch { apply(null, null) } }) {
                        Text(stringResource(Res.string.edit_time_apply))
                    }
                }
                OutlinedButton(onClick = { freeEntry = true }) { Text(stringResource(Res.string.free_entry_instead)) }
            }
        }
        if (model != null) pickerItems(picker, model, change)
    }
    if (model != null) PickerSheets(picker, model, change, togglePin)
    if (freeEntry) {
        FreeEntryDialog(instead = true, onDismiss = { freeEntry = false }) { choice -> apply(choice, null) }
    }
}
