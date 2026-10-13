package io.github.isouplay.roundbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.ui.MenuScreen
import io.github.isouplay.roundbook.ui.PlaceScreen
import io.github.isouplay.roundbook.ui.PlacesScreen
import io.github.isouplay.roundbook.ui.PlatformBackHandler

/** The screens of package 2: places, one place, its menu. */
private sealed interface Screen {
    data object Places : Screen

    data class Place(val id: PlaceId) : Screen

    data class Menu(val placeId: PlaceId) : Screen
}

// The back stack is kept as text ("places|place:<id>|menu:<id>"), so it survives turning the phone.
private fun Screen.encode(): String = when (this) {
    Screen.Places -> "places"
    is Screen.Place -> "place:${id.value}"
    is Screen.Menu -> "menu:${placeId.value}"
}

private fun decodeScreen(text: String): Screen = when {
    text.startsWith("place:") -> Screen.Place(PlaceId(text.removePrefix("place:")))
    text.startsWith("menu:") -> Screen.Menu(PlaceId(text.removePrefix("menu:")))
    else -> Screen.Places
}

/** Root of the UI. Plain screens, no styling yet (that comes with the design round). */
@Composable
fun App(roundbook: Roundbook) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var stackText by rememberSaveable { mutableStateOf(Screen.Places.encode()) }
            val stack = stackText.split('|')
            val push: (Screen) -> Unit = { screen -> stackText = stackText + "|" + screen.encode() }
            val pop: () -> Unit = {
                if (stack.size > 1) stackText = stack.dropLast(1).joinToString("|")
            }
            PlatformBackHandler(enabled = stack.size > 1, onBack = pop)

            Box(modifier = Modifier.safeContentPadding().fillMaxSize()) {
                when (val screen = decodeScreen(stack.last())) {
                    Screen.Places -> PlacesScreen(
                        roundbook = roundbook,
                        onOpenPlace = { push(Screen.Place(it)) },
                    )
                    is Screen.Place -> PlaceScreen(
                        roundbook = roundbook,
                        placeId = screen.id,
                        onBack = pop,
                        onShowMenu = { push(Screen.Menu(screen.id)) },
                    )
                    is Screen.Menu -> MenuScreen(
                        roundbook = roundbook,
                        placeId = screen.placeId,
                        onBack = pop,
                    )
                }
            }
        }
    }
}
