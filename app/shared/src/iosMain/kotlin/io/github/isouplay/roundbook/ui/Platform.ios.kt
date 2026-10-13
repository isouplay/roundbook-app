@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.isouplay.roundbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.isouplay.roundbook.core.menu.MenuLimits
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

@Composable
actual fun rememberMenuFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val viewController = LocalUIViewController.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    // The picker holds its delegate weakly; remember keeps it alive.
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                currentOnPicked(readMenuFile(url))
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
        }
    }
    return {
        // asCopy: the file is copied into the app, so no security-scoped access is needed.
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeJSON, UTTypeData),
            asCopy = true,
        )
        picker.delegate = delegate
        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no back button; the screens have their own "Zurück".
}

private fun readMenuFile(url: NSURL): PickedFile {
    val data = NSData.dataWithContentsOfURL(url) ?: return PickedFile.Unreadable
    if (data.length.toLong() > MenuLimits.FILE_BYTES_MAX) return PickedFile.TooLarge
    val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        ?: return PickedFile.Unreadable
    return PickedFile.Content(text)
}
