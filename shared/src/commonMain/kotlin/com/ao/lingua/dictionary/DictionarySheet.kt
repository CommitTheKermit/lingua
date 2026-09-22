package com.ao.lingua.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val Blue = Color(0xFF44698F)
private val White = Color(0xFFF8F9FA)
private val Line = Color(0xFFDEE2E6)
private val Ink = Color(0xFF181B1E)
private val Body = Color(0xFF495057)
private val Muted = Color(0xFF868E96)

@Composable
fun DictionarySheet(
    state: DictionaryState,
    metadata: DictionaryMetadata,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(328.dp).height(680.dp).clip(RoundedCornerShape(5.dp)).background(White),
        ) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).border(width = 1.dp, color = Line),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.query.ifBlank { "영한 사전" },
                    color = Blue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            if (state.query.isBlank()) {
                DictionarySearch(onSearch)
            } else {
                DictionaryContents(state, metadata, Modifier.weight(1f))
            }

            Box(
                Modifier.fillMaxWidth().height(54.dp).border(width = 1.dp, color = Line).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("닫기", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ColumnScope.DictionarySearch(onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().height(48.dp).border(1.dp, Line, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 13.dp),
            decorationBox = { inner -> Box { if (query.isBlank()) Text("영어 단어", color = Muted); inner() } },
        )
        Box(
            Modifier.fillMaxWidth().height(42.dp).background(Blue, RoundedCornerShape(4.dp)).clickable(enabled = query.isNotBlank()) { onSearch(query) },
            contentAlignment = Alignment.Center,
        ) { Text("검색", color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun DictionaryContents(
    state: DictionaryState,
    metadata: DictionaryMetadata,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.entries.flatMap { entry -> entry.senses.map { sense -> entry to sense } }) { (entry, sense) ->
            DictionaryCard(entry.partOfSpeech, sense.definition)
        }
        if (state.searched && state.entries.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(5.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("사전에 없는 단어입니다.", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("한국어 위키낱말사전 · ${metadata.license}", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DictionaryCard(partOfSpeech: String, definition: String) {
    val headline = definition.substringAfter(')').substringBefore(',').trim().trimEnd('.').ifBlank { definition }
    Column(
        Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(5.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                headline,
                modifier = Modifier.weight(1f),
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.height(18.dp).background(Blue, RoundedCornerShape(50)).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(partOfSpeech, color = White, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }
        Text(definition, color = Body, fontSize = 16.sp, lineHeight = 23.sp)
    }
}
