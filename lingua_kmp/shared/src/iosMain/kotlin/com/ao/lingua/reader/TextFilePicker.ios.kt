package com.ao.lingua.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTextFilePicker(
    onPicked: (PickedTextFile?) -> Unit,
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val delegate = remember { TextFilePickerDelegate { currentOnPicked(it) } }

    return remember(delegate) {
        {
            val presenter = UIApplication.sharedApplication.windows
                .filterIsInstance<UIWindow>()
                .firstOrNull { it.keyWindow }
                ?.rootViewController
            if (presenter == null) {
                currentOnPicked(null)
            } else {
                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(UTTypePlainText),
                    asCopy = true,
                ).apply {
                    allowsMultipleSelection = false
                    this.delegate = delegate
                }
                presenter.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class TextFilePickerDelegate(
    private val onPicked: (PickedTextFile?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onPicked((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.readTextFile())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSURL.readTextFile(): PickedTextFile? {
    val hasAccess = startAccessingSecurityScopedResource()
    return try {
        NSString.stringWithContentsOfURL(this, NSUTF8StringEncoding, null)?.let { content ->
            PickedTextFile(lastPathComponent ?: "document.txt", content)
        }
    } finally {
        if (hasAccess) stopAccessingSecurityScopedResource()
    }
}
