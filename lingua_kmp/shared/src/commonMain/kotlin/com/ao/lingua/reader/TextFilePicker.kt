package com.ao.lingua.reader

import androidx.compose.runtime.Composable

data class PickedTextFile(
    val name: String,
    val content: String,
)

@Composable
expect fun rememberTextFilePicker(
    onPicked: (PickedTextFile?) -> Unit,
): () -> Unit
