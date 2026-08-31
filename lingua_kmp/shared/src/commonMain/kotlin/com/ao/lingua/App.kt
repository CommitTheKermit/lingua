package com.ao.lingua

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ao.lingua.reader.ReaderScreen
import com.ao.lingua.reader.ReaderState
import com.ao.lingua.reader.SentenceSplitter
import com.ao.lingua.reader.rememberTextFilePicker

@Composable
fun App() {
    var state by remember { mutableStateOf(ReaderState()) }
    val openFile = rememberTextFilePicker { file ->
        if (file != null) {
            state = ReaderState(
                title = file.name,
                sentences = SentenceSplitter.split(file.content),
            )
        }
    }

    MaterialTheme {
        ReaderScreen(
            state = state,
            onOpenFile = openFile,
            onPrevious = { state = state.previous() },
            onNext = { state = state.next() },
        )
    }
}
