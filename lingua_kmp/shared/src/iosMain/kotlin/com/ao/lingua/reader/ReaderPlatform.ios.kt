package com.ao.lingua.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
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

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCsvExporter(): (CsvDocument) -> Unit = remember {
    { document ->
        val path = "${platform.Foundation.NSTemporaryDirectory()}${document.name}"
        @Suppress("CAST_NEVER_SUCCEEDS")
        val content = document.content as NSString
        content.writeToFile(
            path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
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
