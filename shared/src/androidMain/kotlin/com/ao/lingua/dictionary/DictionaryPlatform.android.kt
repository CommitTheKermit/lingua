package com.ao.lingua.dictionary

import android.system.Os
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberDictionaryInstaller(): suspend (ByteArray, String) -> String {
    val directory = File(LocalContext.current.applicationContext.filesDir, "dictionary")
    return remember(directory) {
        { bytes, fileName ->
            directory.mkdirs()
            val target = File(directory, fileName)
            if (!target.exists()) {
                val temporary = File(directory, "$fileName.tmp")
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                Os.rename(temporary.absolutePath, target.absolutePath)
            }
            target.absolutePath
        }
    }
}
