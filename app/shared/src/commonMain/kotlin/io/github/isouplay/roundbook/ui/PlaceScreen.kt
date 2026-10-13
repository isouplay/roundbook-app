package io.github.isouplay.roundbook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.core.menu.MenuLimits
import io.github.isouplay.roundbook.core.menu.MenuPlace
import io.github.isouplay.roundbook.core.menu.MenuProblem
import io.github.isouplay.roundbook.core.menu.MenuProblemKind
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.domain.menu.MenuImportResult
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.back
import roundbook.app.shared.generated.resources.menu_file_unreadable
import roundbook.app.shared.generated.resources.menu_imported
import roundbook.app.shared.generated.resources.menu_rejected
import roundbook.app.shared.generated.resources.menu_unchanged
import roundbook.app.shared.generated.resources.place_load_menu
import roundbook.app.shared.generated.resources.place_menu_current
import roundbook.app.shared.generated.resources.place_menu_current_named
import roundbook.app.shared.generated.resources.place_menu_none
import roundbook.app.shared.generated.resources.place_show_menu
import roundbook.app.shared.generated.resources.place_unknown

/** What happened with the last chosen file. */
private sealed interface LoadMessage {
    data class Result(val result: MenuImportResult) : LoadMessage

    data object Unreadable : LoadMessage
}

/** Screen 2: one place – name, date of the current menu, load a menu file. */
@Composable
fun PlaceScreen(roundbook: Roundbook, placeId: PlaceId, onBack: () -> Unit, onShowMenu: () -> Unit) {
    val place = remember(placeId) { roundbook.places.get(placeId) }
    var current by remember(placeId) { mutableStateOf(roundbook.menus.current(placeId)?.info) }
    var message by remember(placeId) { mutableStateOf<LoadMessage?>(null) }

    val pickFile = rememberMenuFilePicker { picked ->
        message = when (picked) {
            is PickedFile.Content -> LoadMessage.Result(roundbook.menus.importMenu(placeId, picked.text))
            PickedFile.TooLarge -> LoadMessage.Result(
                MenuImportResult.Rejected(
                    MenuProblem(
                        kind = MenuProblemKind.FILE_TOO_LARGE,
                        place = MenuPlace.File,
                        limit = MenuLimits.FILE_BYTES_MAX.toDouble(),
                    ),
                ),
            )
            PickedFile.Unreadable -> LoadMessage.Unreadable
        }
        current = roundbook.menus.current(placeId)?.info
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.back)) }
        if (place == null) {
            Text(stringResource(Res.string.place_unknown))
            return@Column
        }
        Text(place.name, style = MaterialTheme.typography.headlineMedium)

        val version = current
        Text(
            when {
                version == null -> stringResource(Res.string.place_menu_none)
                version.label.isEmpty() -> stringResource(Res.string.place_menu_current, formatDate(version.validFrom))
                else -> stringResource(Res.string.place_menu_current_named, version.label, formatDate(version.validFrom))
            },
        )

        Button(onClick = pickFile) { Text(stringResource(Res.string.place_load_menu)) }
        if (version != null) {
            Button(onClick = onShowMenu) { Text(stringResource(Res.string.place_show_menu)) }
        }

        when (val m = message) {
            null -> Unit
            LoadMessage.Unreadable -> Text(stringResource(Res.string.menu_file_unreadable))
            is LoadMessage.Result -> Text(resultText(m.result))
        }
    }
}

@Composable
private fun resultText(result: MenuImportResult): String = when (result) {
    is MenuImportResult.Imported ->
        stringResource(Res.string.menu_imported, result.drinkCount.toString(), formatDate(result.version.validFrom))
    is MenuImportResult.Unchanged -> stringResource(Res.string.menu_unchanged)
    is MenuImportResult.Rejected -> stringResource(Res.string.menu_rejected, menuProblemText(result.problem))
    MenuImportResult.UnknownPlace -> stringResource(Res.string.place_unknown)
}
