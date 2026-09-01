package com.ao.lingua.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberReaderDatabase(): ReaderDatabase {
    val directory = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return remember(directory) { ReaderDatabase("$directory/reader.db") }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberCsvExporter(): (CsvDocument) -> Unit = remember {
    { document ->
        val path = "${platform.Foundation.NSTemporaryDirectory()}${document.name}"
        val bytes = document.content.encodeToByteArray()
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                .writeToFile(path, atomically = true)
        }
        val presenter = UIApplication.sharedApplication.windows
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.keyWindow }
            ?.rootViewController
        presenter?.presentViewController(
            UIDocumentPickerViewController(
                forExportingURLs = listOf(NSURL.fileURLWithPath(path)),
                asCopy = true,
            ),
            animated = true,
            completion = null,
        )
    }
}
