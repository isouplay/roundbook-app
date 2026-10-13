package io.github.isouplay.roundbook.ui

import androidx.compose.runtime.Composable

/** A file chosen in the file picker of the system. */
sealed interface PickedFile {
    data class Content(val text: String) : PickedFile

    /** Larger than a menu file may be (MenuLimits.FILE_BYTES_MAX). */
    data object TooLarge : PickedFile

    data object Unreadable : PickedFile
}

/**
 * Opens the file picker of the system; [onPicked] gets the chosen file as text.
 * Nothing happens if the user cancels. Returns the function that opens the picker.
 */
@Composable
expect fun rememberMenuFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** The back button of the system (Android); no-op where there is none. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
