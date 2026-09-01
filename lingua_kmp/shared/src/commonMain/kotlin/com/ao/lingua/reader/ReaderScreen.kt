package com.ao.lingua.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.ao.lingua.translation.QuotaStatus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun ReaderScreen(
    state: ReaderState,
    onOpenFile: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMoveTo: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onToggleViewer: () -> Unit,
    onToggleBookmarks: () -> Unit,
    onToggleBookmark: () -> Unit,
    onUserTranslationChange: (String) -> Unit,
    onToggleSettings: () -> Unit,
    onDisplayStyleChange: (DisplayTarget, DisplayStyle) -> Unit,
    onExportCsv: () -> Unit,
    onOpenDictionary: (String) -> Unit,
    quota: QuotaStatus,
    modifier: Modifier = Modifier,
) {
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Lingua", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
                NavigationDrawerItem(
                    label = { Text("사전 검색") },
                    selected = false,
                    onClick = {
                        onOpenDictionary("")
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("전체 보기") },
                    selected = state.viewerVisible,
                    onClick = {
                        onToggleViewer()
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("북마크") },
                    selected = state.bookmarksVisible,
                    onClick = {
                        onToggleBookmarks()
                        scope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("표시 설정") },
                    selected = state.settingsVisible,
                    onClick = {
                        onToggleSettings()
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        val horizontalPadding = if (maxWidth >= 600.dp) 32.dp else 16.dp

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lingua",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { drawerState.open() } }) { Text("메뉴") }
                    OutlinedButton(onClick = onOpenFile) { Text("TXT 열기") }
                    OutlinedButton(onClick = onExportCsv, enabled = state.sentences.isNotEmpty()) { Text("CSV 저장") }
                }
            }

            if (state.sentences.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("읽을 TXT 파일을 선택해 주세요.", textAlign = TextAlign.Center)
                }
                return@Column
            }

            Text(state.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                label = { Text("문서 검색") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onToggleViewer, modifier = Modifier.weight(1f)) { Text("전체 보기") }
                OutlinedButton(onClick = onToggleBookmarks, modifier = Modifier.weight(1f)) {
                    Text("북마크 ${state.bookmarks.size}")
                }
                OutlinedButton(onClick = onToggleSettings, modifier = Modifier.weight(1f)) { Text("표시 설정") }
            }

            when {
                state.viewerVisible -> SentenceIndexList(
                    indexes = state.sentences.indices.toList(),
                    state = state,
                    onMoveTo = onMoveTo,
                    modifier = Modifier.weight(1f),
                )

                state.bookmarksVisible -> SentenceIndexList(
                    indexes = state.bookmarks.sorted(),
                    state = state,
                    onMoveTo = onMoveTo,
                    emptyMessage = "저장한 북마크가 없습니다.",
                    modifier = Modifier.weight(1f),
                )

                state.settingsVisible -> DisplaySettings(
                    settings = state.displaySettings,
                    onChange = onDisplayStyleChange,
                    modifier = Modifier.weight(1f),
                )

                else -> ReaderPage(
                    state = state,
                    onMoveTo = onMoveTo,
                    onToggleBookmark = onToggleBookmark,
                    onUserTranslationChange = onUserTranslationChange,
                    onOpenDictionary = onOpenDictionary,
                    modifier = Modifier.weight(1f),
                )
            }

            QuotaLabel(quota)
            Text(
                text = state.positionLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally).semantics {
                    contentDescription = "읽기 위치 ${state.positionLabel}"
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPrevious, enabled = state.canGoPrevious, modifier = Modifier.weight(1f)) {
                    Text("이전")
                }
                Button(onClick = onNext, enabled = state.canGoNext, modifier = Modifier.weight(1f)) { Text("다음") }
            }
        }
    }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun QuotaLabel(quota: QuotaStatus) {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(quota.nextRefillAtMs) {
        while (quota.nextRefillAtMs != null) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(1_000)
        }
    }
    val refill = quota.nextRefillAtMs?.let { next ->
        val seconds = max(0, (next - now + 999) / 1_000)
        " - 다음 충전 ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }.orEmpty()
    Text(
        "번역 제한 ${quota.quotaRemaining}/${quota.quotaMax}$refill",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "번역 quota" },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ReaderPage(
    state: ReaderState,
    onMoveTo: (Int) -> Unit,
    onToggleBookmark: () -> Unit,
    onUserTranslationChange: (String) -> Unit,
    onOpenDictionary: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalStyle = state.displaySettings.getValue(DisplayTarget.ORIGINAL)
    val userStyle = state.displaySettings.getValue(DisplayTarget.USER_TRANSLATION)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = originalStyle.backgroundColor.toColor(Color.White)),
        ) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = state.currentSentence,
                    color = originalStyle.textColor.toColor(MaterialTheme.colorScheme.onSurface),
                    fontSize = originalStyle.fontSize.sp,
                    lineHeight = (originalStyle.fontSize * originalStyle.lineHeight).sp,
                    fontFamily = originalStyle.fontFamily.toFontFamily(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        OutlinedButton(onClick = onToggleBookmark, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isCurrentBookmarked) "북마크 해제" else "현재 문장 북마크")
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.currentSentence.split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
                Text(
                    text = word,
                    modifier = Modifier.clickable { onOpenDictionary(word) }.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        OutlinedTextField(
            value = state.currentUserTranslation,
            onValueChange = onUserTranslationChange,
            label = { Text("사용자 번역 - 입력 즉시 저장") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            textStyle = TextStyle(
                color = userStyle.textColor.toColor(MaterialTheme.colorScheme.onSurface),
                fontSize = userStyle.fontSize.sp,
                lineHeight = (userStyle.fontSize * userStyle.lineHeight).sp,
                fontFamily = userStyle.fontFamily.toFontFamily(),
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = userStyle.backgroundColor.toColor(Color.Transparent),
                unfocusedContainerColor = userStyle.backgroundColor.toColor(Color.Transparent),
            ),
        )
        if (state.searchResults.isNotEmpty()) {
            Text("검색 결과 ${state.searchResults.size}개", style = MaterialTheme.typography.labelLarge)
            SentenceIndexList(
                indexes = state.searchResults,
                state = state,
                onMoveTo = onMoveTo,
                modifier = Modifier.height(120.dp),
            )
        } else if (state.searchQuery.isNotBlank()) {
            Text("검색 결과가 없습니다.")
        }
    }
}

@Composable
private fun SentenceIndexList(
    indexes: List<Int>,
    state: ReaderState,
    onMoveTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "문장이 없습니다.",
) {
    if (indexes.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(emptyMessage) }
        return
    }
    val viewerStyle = state.displaySettings.getValue(DisplayTarget.VIEWER)
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(indexes, key = { it }) { sentenceIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(viewerStyle.backgroundColor.toColor(Color.Transparent))
                    .clickable { onMoveTo(sentenceIndex) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${sentenceIndex + 1}", style = MaterialTheme.typography.labelMedium)
                Text(
                    state.sentences[sentenceIndex],
                    color = viewerStyle.textColor.toColor(MaterialTheme.colorScheme.onSurface),
                    fontSize = viewerStyle.fontSize.sp,
                    lineHeight = (viewerStyle.fontSize * viewerStyle.lineHeight).sp,
                    fontFamily = viewerStyle.fontFamily.toFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun DisplaySettings(
    settings: Map<DisplayTarget, DisplayStyle>,
    onChange: (DisplayTarget, DisplayStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(DisplayTarget.entries) { target ->
            val style = settings.getValue(target)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.label(), style = MaterialTheme.typography.titleSmall)
                    SettingButtons("글자 크기 ${style.fontSize.toInt()}") { delta ->
                        onChange(target, style.copy(fontSize = (style.fontSize + delta).coerceIn(12f, 40f)))
                    }
                    SettingButtons("줄 높이 ${style.lineHeight}", step = 0.1f) { delta ->
                        onChange(target, style.copy(lineHeight = (style.lineHeight + delta).coerceIn(1f, 2.5f)))
                    }
                    OutlinedButton(
                        onClick = {
                            onChange(target, style.copy(fontFamily = if (style.fontFamily == "Default") "Serif" else "Default"))
                        },
                    ) { Text("글꼴 ${style.fontFamily}") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onChange(target, style.copy(textColor = if (style.textColor == "#FF1B1B1F") "#FFFFFFFF" else "#FF1B1B1F"))
                            },
                        ) { Text("글자색 전환") }
                        OutlinedButton(
                            onClick = {
                                onChange(target, style.copy(backgroundColor = if (style.backgroundColor == "#FFFFFFFF") "#FF1B1B1F" else "#FFFFFFFF"))
                            },
                        ) { Text("배경색 전환") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingButtons(label: String, step: Float = 1f, onDelta: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onDelta(-step) }) { Text("-") }
        OutlinedButton(onClick = { onDelta(step) }) { Text("+") }
    }
}

private fun DisplayTarget.label(): String = when (this) {
    DisplayTarget.ORIGINAL -> "원문"
    DisplayTarget.MACHINE_TRANSLATION -> "기계 번역"
    DisplayTarget.USER_TRANSLATION -> "사용자 번역"
    DisplayTarget.VIEWER -> "전체 뷰어"
}

private fun String.toFontFamily(): FontFamily = when (this) {
    "Serif" -> FontFamily.Serif
    "Monospace" -> FontFamily.Monospace
    else -> FontFamily.Default
}

private fun String.toColor(fallback: Color): Color = runCatching {
    Color(removePrefix("#").toULong(16))
}.getOrDefault(fallback)
