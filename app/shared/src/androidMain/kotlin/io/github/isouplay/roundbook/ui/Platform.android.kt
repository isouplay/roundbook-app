package io.github.isouplay.roundbook.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.isouplay.roundbook.core.menu.MenuLimits
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

@Composable
actual fun rememberMenuFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) currentOnPicked(readMenuFile(context, uri))
    }
    // Any file: JSON files often come without the right type (e.g. from downloads or messengers).
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

private fun readMenuFile(context: Context, uri: Uri): PickedFile = try {
    context.contentResolver.openInputStream(uri)?.use(::readLimited) ?: PickedFile.Unreadable
} catch (e: IOException) {
    PickedFile.Unreadable
} catch (e: SecurityException) {
    PickedFile.Unreadable
}

/** Reads at most MenuLimits.FILE_BYTES_MAX bytes; a larger file is not read to the end. */
private fun readLimited(stream: InputStream): PickedFile {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = stream.read(chunk)
        if (n < 0) break
        total += n
        if (total > MenuLimits.FILE_BYTES_MAX) return PickedFile.TooLarge
        out.write(chunk, 0, n)
    }
    return PickedFile.Content(out.toByteArray().decodeToString())
}
