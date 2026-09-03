package com.ao.lingua.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberReaderDatabase(): ReaderDatabase {
    val context = LocalContext.current.applicationContext
    return remember(context) { ReaderDatabase(context.getDatabasePath("reader.db").absolutePath) }
}

@Composable
actual fun rememberCsvExporter(): (CsvDocument) -> Unit {
    val resolver = LocalContext.current.contentResolver
    var pending by remember { mutableStateOf(CsvDocument("lingua.csv", "")) }
    val currentPending by rememberUpdatedState(pending)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            resolver.openOutputStream(it)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(currentPending.content)
            }
        }
    }
    return remember(launcher) {
        { document ->
            pending = document
            launcher.launch(document.name)
        }
    }
}
