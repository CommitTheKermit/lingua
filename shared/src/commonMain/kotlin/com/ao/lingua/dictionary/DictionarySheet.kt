package com.ao.lingua.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DictionarySheet(
    state: DictionaryState,
    metadata: DictionaryMetadata,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onTranslate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("닫기") } },
        title = { Text(state.query.ifBlank { "영한 사전" }, color = Color(0xFF466F99)) },
        text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("영한 사전", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    label = { Text("영어 단어") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onSearch(state.query) }, enabled = state.query.isNotBlank()) { Text("검색") }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.entries, key = { "${it.headword}:${it.partOfSpeech}" }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${entry.headword} - ${entry.partOfSpeech}", style = MaterialTheme.typography.titleMedium)
                            entry.senses.forEach { sense -> Text("${sense.sequence + 1}. ${sense.definition}") }
                        }
                    }
                }
                if (state.searched && state.entries.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("사전에 없는 단어입니다.")
                            Button(
                                onClick = onTranslate,
                                enabled = !state.translating && state.query.isNotBlank(),
                            ) { Text(if (state.translating) "번역 중" else "DeepL로 번역") }
                            state.remoteTranslation?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                            state.translationError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        },
        dismissButton = { Text("한국어 위키낱말사전 - CC BY-SA 4.0 ${metadata.dataVersion}", style = MaterialTheme.typography.labelSmall) },
    )
}
