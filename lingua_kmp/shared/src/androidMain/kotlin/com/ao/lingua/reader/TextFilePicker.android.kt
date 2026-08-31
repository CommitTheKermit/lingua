package com.ao.lingua.reader

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTextFilePicker(
    onPicked: (PickedTextFile?) -> Unit,
): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        currentOnPicked(uri?.let { resolver.readTextFile(it) })
    }

    return remember(launcher) {
        { launcher.launch(arrayOf("text/plain")) }
    }
}

private fun ContentResolver.readTextFile(uri: Uri): PickedTextFile? = runCatching {
    val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.txt"
    val content = openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: return null
    PickedTextFile(name, content)
}.getOrNull()
