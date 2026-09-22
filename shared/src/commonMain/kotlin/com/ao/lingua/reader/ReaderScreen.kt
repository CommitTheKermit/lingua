package com.ao.lingua.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Blue = Color(0xFF466F99)
private val Pale = Color(0xFFF7F8FA)
private val Line = Color(0xFFE9ECF0)

@Composable fun ReaderScreen(
    state: ReaderState, onOpenFile: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onMoveTo: (Int) -> Unit, onSearch: (String) -> Unit, onToggleViewer: () -> Unit,
    onToggleBookmarks: () -> Unit, onToggleBookmark: () -> Unit, onUserTranslationChange: (String) -> Unit,
    onToggleSettings: () -> Unit, onDisplayStyleChange: (DisplayTarget, DisplayStyle) -> Unit,
    onExportCsv: () -> Unit, onOpenDictionary: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf(false) }; var jump by remember { mutableStateOf(false) }
    var translationVisible by remember { mutableStateOf(true) }
    var timerVisible by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(timerVisible) {
        while (timerVisible) {
            delay(1_000)
            elapsedSeconds++
        }
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.width(258.dp), drawerContainerColor = Color.White) {
            Row(Modifier.fillMaxWidth().height(48.dp).background(Blue).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✒ Lingua", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f)); IconButton({ scope.launch { drawer.close() } }) { Icon(Icons.Filled.Close, "닫기", tint = Color.White) }
            }
            DrawerItem("파일 열기", Icons.Filled.FileOpen) { onOpenFile(); scope.launch { drawer.close() } }
            DrawerItem("전체 보기", Icons.Filled.Article) { onToggleViewer(); scope.launch { drawer.close() } }
            DrawerItem("책갈피", Icons.Filled.BookmarkBorder) { onToggleBookmarks(); scope.launch { drawer.close() } }
            DrawerItem("읽기 옵션", Icons.Filled.Settings) { onToggleSettings(); scope.launch { drawer.close() } }
            DrawerItem("줄 이동", Icons.Filled.FindInPage) { jump = true; scope.launch { drawer.close() } }
            DrawerItem(if (timerVisible) "타이머 숨기기" else "타이머 보기", Icons.Filled.Timer) { timerVisible = !timerVisible; scope.launch { drawer.close() } }
            DrawerItem("사전 검색", Icons.Filled.Search) { onOpenDictionary(""); scope.launch { drawer.close() } }
        }
    }) {
        Surface(modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = Pale) {
            when {
                state.settingsVisible -> Settings(state.displaySettings, onToggleSettings, onDisplayStyleChange)
                state.viewerVisible -> ListScreen("읽기 모드", state.sentences.indices.toList(), state, onMoveTo, onToggleViewer)
                state.bookmarksVisible -> ListScreen("책갈피", state.bookmarks.sorted(), state, onMoveTo, onToggleBookmarks)
                else -> Home(state, onOpenFile, onPrevious, onNext, onToggleBookmark, onUserTranslationChange, onOpenDictionary, onExportCsv, { scope.launch { drawer.open() } }, { search = true }, { jump = true }, translationVisible, { translationVisible = !translationVisible }, timerVisible, elapsedSeconds, { timerVisible = !timerVisible })
            }
        }
    }
    if (search) SearchDialog(state, onSearch, onMoveTo) { search = false }
    if (jump) JumpDialog(state, onMoveTo) { jump = false }
}

@Composable private fun Home(
    state: ReaderState, onOpenFile: () -> Unit, previous: () -> Unit, next: () -> Unit,
    bookmark: () -> Unit, input: (String) -> Unit, dictionary: (String) -> Unit, export: () -> Unit,
    menu: () -> Unit, search: () -> Unit, jump: () -> Unit, translationVisible: Boolean,
    toggleTranslation: () -> Unit, timerVisible: Boolean, elapsedSeconds: Int, toggleTimer: () -> Unit,
) = Column(Modifier.fillMaxSize()) {
    TopBar(state.title.ifBlank { "파일을 선택해 주세요." }, menu, search, jump, state.isCurrentBookmarked, bookmark, translationVisible, toggleTranslation, timerVisible, elapsedSeconds, toggleTimer)
    if (state.sentences.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onOpenFile, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("파일 열기") } }
    else {
        val original = state.displaySettings.getValue(DisplayTarget.ORIGINAL); val machine = state.displaySettings.getValue(DisplayTarget.MACHINE_TRANSLATION); val user = state.displaySettings.getValue(DisplayTarget.USER_TRANSLATION)
        Column(Modifier.weight(1f)) {
            Label("원문", state.positionLabel); TextPanel(state.currentSentence, original, Modifier.weight(if (translationVisible) 1f else 1.7f))
            if (translationVisible) {
                Label("사전 이용 안내"); TextPanel("사전에 없는 단어에서만 번역을 요청할 수 있습니다.", machine, Modifier.weight(1f))
                Label("번역문 입력")
                PlainField(state.currentUserTranslation, input, "직접 번역을 입력하세요.", Modifier.fillMaxWidth().weight(.7f).padding(12.dp, 4.dp), style(user))
            }
            key(state.documentId, state.index) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.currentSentence.split(Regex("\\s+")).filter(String::isNotBlank)) { word ->
                        Text(word, Modifier.background(Color(0xFFE7EEF5)).clickable { dictionary(word) }.padding(8.dp), color = Blue, fontSize = 13.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavButton("이전 줄", Icons.AutoMirrored.Filled.ArrowBack, previous, state.canGoPrevious, Modifier.weight(1f)); NavButton("다음 줄", Icons.AutoMirrored.Filled.ArrowForward, next, state.canGoNext, Modifier.weight(1f)); NavButton("CSV 저장", Icons.Filled.FileDownload, export, state.userTranslations.isNotEmpty(), Modifier.weight(1f))
        }
    }
}

@Composable private fun TopBar(title: String, menu: () -> Unit, search: () -> Unit, jump: () -> Unit, marked: Boolean, bookmark: () -> Unit, translationVisible: Boolean, toggleTranslation: () -> Unit, timerVisible: Boolean, elapsedSeconds: Int, toggleTimer: () -> Unit) = Row(Modifier.fillMaxWidth().height(48.dp).background(Color.White).border(1.dp, Line).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(menu) { Icon(Icons.AutoMirrored.Filled.MenuOpen, "메뉴", tint = Blue) }; Text(title, Modifier.weight(1f), color = Blue, maxLines = 1, fontSize = 17.sp)
    if (timerVisible) Text(formatElapsed(elapsedSeconds), color = Blue, fontSize = 12.sp)
    IconButton(toggleTimer) { Icon(if (timerVisible) Icons.Filled.TimerOff else Icons.Filled.Timer, "타이머", tint = Blue) }
    IconButton(toggleTranslation) { Icon(if (translationVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "번역 영역", tint = Blue) }
    IconButton(bookmark) { Icon(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "책갈피", tint = Blue) }; IconButton(jump) { Icon(Icons.Filled.FindInPage, "줄 이동", tint = Blue) }; IconButton(search) { Icon(Icons.Filled.Search, "검색", tint = Blue) }
}
private fun formatElapsed(seconds: Int): String {
    fun padded(value: Int) = value.toString().padStart(2, '0')
    return "${padded(seconds / 60)}:${padded(seconds % 60)}"
}
@Composable private fun Label(name: String, tail: String = "") = Row(Modifier.fillMaxWidth().background(Color(0xFFF4F6F8)).padding(16.dp, 7.dp)) { Text(name, color = Color.Gray, fontSize = 14.sp); Spacer(Modifier.weight(1f)); Text(tail, color = Color(0xFF123D67)) }
@Composable private fun TextPanel(text: String, setting: DisplayStyle, modifier: Modifier) = Box(modifier.fillMaxWidth().background(setting.backgroundColor.color()).verticalScroll(rememberScrollState()).padding(16.dp, 10.dp)) { Text(text, style = style(setting)) }
private fun style(setting: DisplayStyle) = TextStyle(color = setting.textColor.color(Color.Black), fontSize = setting.fontSize.sp, lineHeight = (setting.fontSize * setting.lineHeight).sp, fontFamily = if (setting.fontFamily == "Serif") FontFamily.Serif else FontFamily.Default)
@Composable private fun NavButton(label: String, icon: ImageVector, action: () -> Unit, enabled: Boolean, modifier: Modifier) = Button(action, modifier.height(38.dp), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Icon(icon, null, Modifier.width(15.dp)); Spacer(Modifier.width(3.dp)); Text(label, fontSize = 12.sp) }
@Composable private fun DrawerItem(label: String, icon: ImageVector, action: () -> Unit) = Row(Modifier.fillMaxWidth().clickable { action() }.padding(16.dp, 13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(10.dp)); Text(label, fontSize = 18.sp) }

@Composable private fun ListScreen(title: String, indexes: List<Int>, state: ReaderState, move: (Int) -> Unit, close: () -> Unit) = Column(Modifier.fillMaxSize()) {
    TitleBar(title, close)
    if (indexes.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (title == "책갈피") "저장한 책갈피가 없습니다." else "문장이 없습니다.") }
    else { val setting = state.displaySettings.getValue(DisplayTarget.VIEWER); LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(indexes, key = { it }) { index -> Text(state.sentences[index], Modifier.fillMaxWidth().clickable { move(index); close() }.padding(vertical = 4.dp), style = style(setting)) } } }
}
@Composable private fun TitleBar(title: String, back: () -> Unit) = Row(Modifier.fillMaxWidth().height(48.dp).background(Color.White).border(1.dp, Line), verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }; Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp); Spacer(Modifier.width(48.dp)) }

@Composable private fun Settings(settings: Map<DisplayTarget, DisplayStyle>, close: () -> Unit, change: (DisplayTarget, DisplayStyle) -> Unit) {
    var target by remember { mutableStateOf(DisplayTarget.ORIGINAL) }; val value = settings.getValue(target)
    Column(Modifier.fillMaxSize()) {
        TitleBar("읽기 옵션", close)
        Row(Modifier.fillMaxWidth()) { listOf(DisplayTarget.ORIGINAL, DisplayTarget.MACHINE_TRANSLATION, DisplayTarget.USER_TRANSLATION, DisplayTarget.VIEWER).forEach { item -> TextButton({ target = item }, Modifier.weight(1f).height(52.dp)) { Text(item.tab(), Modifier.fillMaxSize().background(if (item == target) Blue else Color.Transparent).padding(top = 13.dp), textAlign = TextAlign.Center, color = if (item == target) Color.White else Color.DarkGray) } } }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("설정 미리보기", color = Color.Gray, fontSize = 14.sp); Text("적용 예시입니다.\n각 칸별 설정이 가능합니다.", style = style(value)); HorizontalDivider(color = Line); Text("폰트 설정", color = Color.Gray, fontSize = 14.sp)
            Setting("폰트 선택") { TextButton({ change(target, value.copy(fontFamily = if (value.fontFamily == "Default") "Serif" else "Default")) }) { Text(if (value.fontFamily == "Default") "Noto Sans⌄" else "Serif⌄") } }
            Palette("배경색", value.backgroundColor) { change(target, value.copy(backgroundColor = it)) }; Palette("폰트색", value.textColor) { change(target, value.copy(textColor = it)) }; HorizontalDivider(color = Line); Text("본문 설정", color = Color.Gray, fontSize = 14.sp)
            Number("글자 크기", value.fontSize, 12f, 40f) { change(target, value.copy(fontSize = it)) }; Number("줄 간격", value.lineHeight, 1f, 2.5f, .1f) { change(target, value.copy(lineHeight = it)) }
        }
    }
}
@Composable private fun Setting(label: String, content: @Composable () -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontSize = 17.sp); content() }
@Composable private fun Palette(label: String, selected: String, pick: (String) -> Unit) = Setting(label) { listOf("#FFFFFFFF", "#FF4B4B4B", "#FF252525", "#FFECE9E1").forEach { hex -> Box(Modifier.padding(start = 7.dp).width(32.dp).height(28.dp).background(hex.color()).border(if (hex == selected) 2.dp else 0.dp, Blue).clickable { pick(hex) }) } }
@Composable private fun Number(label: String, value: Float, min: Float, max: Float, step: Float = 1f, set: (Float) -> Unit) = Setting(label) { TextButton({ set((value - step).coerceAtLeast(min)) }) { Text("−", fontSize = 21.sp) }; Text(if (step == 1f) value.toInt().toString() else decimal(value), Modifier.width(38.dp), textAlign = TextAlign.Center); TextButton({ set((value + step).coerceAtMost(max)) }) { Text("+", fontSize = 21.sp) } }
private fun decimal(value: Float) = "${value.toInt()}.${((value * 10).toInt() % 10)}"

@Composable private fun SearchDialog(state: ReaderState, search: (String) -> Unit, move: (Int) -> Unit, close: () -> Unit) = AlertDialog(onDismissRequest = close, title = { Text("문서 검색", color = Blue) }, confirmButton = { TextButton(close) { Text("닫기") } }, text = { Column { PlainField(state.searchQuery, search, "영단어를 입력해 주세요.", Modifier.fillMaxWidth(), TextStyle(fontSize = 16.sp), true); state.searchResults.forEach { index -> Text("${index + 1}. ${state.sentences[index]}", Modifier.fillMaxWidth().clickable { move(index); close() }.padding(vertical = 9.dp)) }; if (state.searchQuery.isNotBlank() && state.searchResults.isEmpty()) Text("검색 결과가 없습니다.") } })
@Composable private fun JumpDialog(state: ReaderState, move: (Int) -> Unit, close: () -> Unit) { var input by remember { mutableStateOf((state.index + 1).toString()) }; AlertDialog(onDismissRequest = close, title = { Text("${state.index + 1}번째 줄", color = Blue) }, dismissButton = { TextButton(close) { Text("닫기") } }, confirmButton = { TextButton({ input.toIntOrNull()?.minus(1)?.let(move); close() }) { Text("이동") } }, text = { Column { Text(state.currentSentence); PlainField(input, { input = it.filter(Char::isDigit) }, "줄 번호 / ${state.sentences.size}", Modifier.padding(top = 16.dp), TextStyle(fontSize = 16.sp), true) } }) }
@Composable private fun PlainField(value: String, change: (String) -> Unit, placeholder: String, modifier: Modifier, textStyle: TextStyle, singleLine: Boolean = false) = BasicTextField(value, change, modifier.border(1.dp, Line).background(Color.White).padding(12.dp), textStyle = textStyle, singleLine = singleLine, decorationBox = { inner -> Box { if (value.isBlank()) Text(placeholder, color = Color.Gray); inner() } })
private fun DisplayTarget.tab() = when (this) { DisplayTarget.ORIGINAL -> "상단"; DisplayTarget.MACHINE_TRANSLATION -> "중단"; DisplayTarget.USER_TRANSLATION -> "하단"; DisplayTarget.VIEWER -> "전체" }
private fun String.color(fallback: Color = Color.White) = runCatching { Color(removePrefix("#").toULong(16)) }.getOrDefault(fallback)
