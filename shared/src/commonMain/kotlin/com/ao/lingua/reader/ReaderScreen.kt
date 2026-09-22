package com.ao.lingua.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.ao.lingua.ui.linguaFontFamily
import lingua.shared.generated.resources.Res
import lingua.shared.generated.resources.reader_arrow_left
import lingua.shared.generated.resources.reader_arrow_right
import lingua.shared.generated.resources.reader_edit
import lingua.shared.generated.resources.reader_menu
import lingua.shared.generated.resources.reader_menu_book
import lingua.shared.generated.resources.reader_menu_file
import lingua.shared.generated.resources.reader_menu_logo
import lingua.shared.generated.resources.reader_menu_move
import lingua.shared.generated.resources.reader_menu_read
import lingua.shared.generated.resources.reader_menu_settings
import lingua.shared.generated.resources.reader_search
import lingua.shared.generated.resources.reader_timer_off
import lingua.shared.generated.resources.reader_timer_on
import lingua.shared.generated.resources.reader_translate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Blue = Color(0xFF44698F)
private val DarkBlue = Color(0xFF1F4A76)
private val Pale = Color(0xFFF1F3F5)
private val White = Color(0xFFF8F9FA)
private val Line = Color(0xFFDEE2E6)
private val Muted = Color(0xFF868E96)
private val Ink = Color(0xFF181B1E)

@Composable fun ReaderScreen(
    state: ReaderState, onOpenFile: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onMoveTo: (Int) -> Unit, onToggleViewer: () -> Unit,
    onToggleBookmarks: () -> Unit, onToggleBookmark: () -> Unit, onUserTranslationChange: (String) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onToggleSettings: () -> Unit, onDisplayStyleChange: (DisplayTarget, DisplayStyle) -> Unit,
    onExportCsv: () -> Unit, onOpenDictionary: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
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
        ModalDrawerSheet(Modifier.width(257.dp), drawerContainerColor = White) {
            Row(Modifier.fillMaxWidth().height(45.dp).background(Blue).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.reader_menu_logo), null, Modifier.size(30.dp, 20.dp))
                Text("L i n g u a", Modifier.weight(1f).padding(start = 2.dp), color = White, fontFamily = linguaFontFamily(), fontSize = 12.sp)
                IconButton({ scope.launch { drawer.close() } }, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "닫기", tint = White, modifier = Modifier.size(18.dp)) }
            }
            DrawerItem("파일 열기", Res.drawable.reader_menu_file) { onOpenFile(); scope.launch { drawer.close() } }
            DrawerItem("읽기 모드", Res.drawable.reader_menu_read) { onToggleViewer(); scope.launch { drawer.close() } }
            DrawerItem("읽기 옵션", Res.drawable.reader_menu_settings) { onToggleSettings(); scope.launch { drawer.close() } }
            DrawerItem("줄 이동", Res.drawable.reader_menu_move) { jump = true; scope.launch { drawer.close() } }
            DrawerItem("단어장", Res.drawable.reader_menu_book) { onToggleBookmarks(); scope.launch { drawer.close() } }
        }
    }) {
        Surface(modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = Pale) {
            when {
                state.settingsVisible -> Settings(state.displaySettings, onToggleSettings, onDisplayStyleChange)
                state.viewerVisible -> ViewerScreen(state, onToggleViewer, onToggleBookmarks, onToggleBookmark, onToggleSettings)
                else -> Home(state, onOpenFile, onPrevious, onNext, onUserTranslationChange, onOpenDictionary, onExportCsv, { scope.launch { drawer.open() } }, { onOpenDictionary("") }, translationVisible, { translationVisible = !translationVisible }, timerVisible, elapsedSeconds, { timerVisible = !timerVisible })
            }
        }
    }
    if (jump) JumpDialog(state, onMoveTo) { jump = false }
    if (state.bookmarksVisible) BookmarkDialog(state, onMoveTo, onRemoveBookmark, onToggleBookmarks)
}

@Composable private fun Home(
    state: ReaderState, onOpenFile: () -> Unit, previous: () -> Unit, next: () -> Unit,
    input: (String) -> Unit, dictionary: (String) -> Unit, export: () -> Unit,
    menu: () -> Unit, search: () -> Unit, translationVisible: Boolean,
    toggleTranslation: () -> Unit, timerVisible: Boolean, elapsedSeconds: Int, toggleTimer: () -> Unit,
) = Column(Modifier.fillMaxSize()) {
    val inputFocusRequester = remember { FocusRequester() }
    TopBar(state.title.ifBlank { "파일을 선택해 주세요." }, menu, search, { inputFocusRequester.requestFocus() }, toggleTranslation)
    Spacer(Modifier.height(4.dp))
    val original = state.displaySettings.getValue(DisplayTarget.ORIGINAL)
    val machine = state.displaySettings.getValue(DisplayTarget.MACHINE_TRANSLATION)
    val user = state.displaySettings.getValue(DisplayTarget.USER_TRANSLATION)
    ReaderPanel("원문", state.positionLabel, Modifier.height(204.dp)) {
        if (state.sentences.isEmpty()) {
            Box(Modifier.fillMaxSize().clickable(onClick = onOpenFile))
        } else TextPanel(state.currentSentence, original, Modifier.fillMaxSize())
    }
    Spacer(Modifier.height(4.dp))
    ReaderPanel("번역", modifier = Modifier.height(204.dp)) {
        if (translationVisible && state.sentences.isNotEmpty()) {
            TextPanel("사전에 없는 단어에서만 번역을 요청할 수 있습니다.", machine, Modifier.fillMaxSize())
        }
    }
    Spacer(Modifier.height(4.dp))
    ReaderPanel("번역문 입력", modifier = Modifier.height(107.dp)) {
        if (state.sentences.isNotEmpty()) {
            BasicTextField(
                value = state.currentUserTranslation,
                onValueChange = input,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).focusRequester(inputFocusRequester),
                textStyle = style(user),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    WordStrip(state.currentSentence, dictionary)
    Spacer(Modifier.height(4.dp))
    StatusStrip(timerVisible, elapsedSeconds, toggleTimer)
    Spacer(Modifier.weight(1f))
    BottomControls(previous, next, export, state.canGoPrevious, state.canGoNext, state.userTranslations.isNotEmpty())
}

@Composable private fun TopBar(title: String, menu: () -> Unit, search: () -> Unit, edit: () -> Unit, translate: () -> Unit) = Row(
    Modifier.fillMaxWidth().height(48.dp).background(White).border(width = 1.dp, color = Line).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    FigmaIcon(Res.drawable.reader_menu, "메뉴", menu)
    Spacer(Modifier.width(13.dp))
    Text(title, Modifier.weight(1f), color = Blue, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = linguaFontFamily(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    FigmaIcon(Res.drawable.reader_edit, "편집", edit)
    Spacer(Modifier.width(16.dp))
    FigmaIcon(Res.drawable.reader_translate, "번역", translate)
    Spacer(Modifier.width(16.dp))
    FigmaIcon(Res.drawable.reader_search, "검색", search)
}

@Composable private fun FigmaIcon(resource: DrawableResource, description: String, action: () -> Unit) = Image(
    painter = painterResource(resource),
    contentDescription = description,
    modifier = Modifier.size(24.dp).clickable(onClick = action),
)

@Composable private fun ReaderPanel(label: String, tail: String = "", modifier: Modifier, content: @Composable BoxScope.() -> Unit) = Box(modifier.fillMaxWidth().background(White)) {
    Text(label, Modifier.padding(start = 16.dp, top = 8.dp), color = Muted, fontFamily = linguaFontFamily(), fontSize = 14.sp)
    if (tail.isNotBlank()) Text(tail, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 8.dp), color = DarkBlue, fontFamily = linguaFontFamily(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Box(Modifier.fillMaxSize().padding(top = 36.dp), content = content)
}

@Composable private fun WordStrip(sentence: String, dictionary: (String) -> Unit) = LazyRow(
    modifier = Modifier.fillMaxWidth().height(48.dp).background(White),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(sentence.split(Regex("\\s+")).filter(String::isNotBlank)) { word ->
        Text(
            word,
            Modifier.height(32.dp).background(DarkBlue, RoundedCornerShape(4.dp)).clickable { dictionary(word) }.padding(horizontal = 16.dp, vertical = 6.dp),
            color = White,
            fontFamily = linguaFontFamily(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable private fun StatusStrip(timerVisible: Boolean, elapsedSeconds: Int, toggleTimer: () -> Unit) {
    if (timerVisible) {
        Row(Modifier.fillMaxWidth().height(36.dp).background(White).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(Res.drawable.reader_timer_on), null, Modifier.size(24.dp).clickable(onClick = toggleTimer))
            Spacer(Modifier.width(3.dp))
            Text("번역 제한 시간", color = DarkBlue, fontFamily = linguaFontFamily(), fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(formatElapsed(elapsedSeconds), color = DarkBlue, fontFamily = linguaFontFamily(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    } else {
        Row(Modifier.fillMaxWidth().height(36.dp).background(Pale), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(296.dp).fillMaxHeight().background(White).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("기기 번역 콜 제한", color = Muted, fontFamily = linguaFontFamily(), fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("0/200", color = Ink, fontFamily = linguaFontFamily(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(60.dp).fillMaxHeight().background(White).clickable(onClick = toggleTimer), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.reader_timer_off), "타이머", Modifier.size(24.dp))
            }
        }
    }
}

@Composable private fun BottomControls(previous: () -> Unit, next: () -> Unit, input: () -> Unit, canPrevious: Boolean, canNext: Boolean, canInput: Boolean) = Row(
    Modifier.fillMaxWidth().height(57.dp).background(White).padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    FigmaNavButton("이전 줄", Res.drawable.reader_arrow_left, previous, canPrevious, Modifier.weight(1f))
    FigmaNavButton("다음 줄", Res.drawable.reader_arrow_right, next, canNext, Modifier.weight(1f), iconAfter = true)
    FigmaNavButton("입력", null, input, canInput, Modifier.weight(1f))
}

@Composable private fun FigmaNavButton(label: String, icon: DrawableResource?, action: () -> Unit, enabled: Boolean, modifier: Modifier, iconAfter: Boolean = false) = Box(
    modifier.height(34.dp).background(if (enabled) Blue else Line, RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = action),
    contentAlignment = Alignment.Center,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null && !iconAfter) Image(painterResource(icon), null, Modifier.size(14.dp))
        Text(label, color = if (enabled) White else Muted, fontFamily = linguaFontFamily(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (icon != null && iconAfter) Image(painterResource(icon), null, Modifier.size(14.dp))
    }
}
private fun formatElapsed(seconds: Int): String {
    fun padded(value: Int) = value.toString().padStart(2, '0')
    return "${padded(seconds / 60)}:${padded(seconds % 60)}"
}
@Composable private fun TextPanel(text: String, setting: DisplayStyle, modifier: Modifier) = Box(modifier.fillMaxWidth().background(setting.backgroundColor.color()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) { Text(text, style = style(setting)) }
@Composable private fun style(setting: DisplayStyle) = TextStyle(color = setting.textColor.color(Color.Black), fontSize = setting.fontSize.sp, lineHeight = (setting.fontSize * setting.lineHeight).sp, fontFamily = if (setting.fontFamily == "Serif") FontFamily.Serif else linguaFontFamily(), fontWeight = FontWeight.Medium)
@Composable private fun DrawerItem(label: String, icon: DrawableResource, action: () -> Unit) = Row(Modifier.fillMaxWidth().height(56.dp).clickable { action() }.padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) { Image(painterResource(icon), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, color = Ink, fontFamily = linguaFontFamily(), fontSize = 18.sp) }

@Composable private fun ViewerScreen(
    state: ReaderState,
    close: () -> Unit,
    openBookmarks: () -> Unit,
    toggleBookmark: () -> Unit,
    openSettings: () -> Unit,
) = Column(Modifier.fillMaxSize().background(White)) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).border(width = 1.dp, color = Line).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", Modifier.size(22.dp).clickable(onClick = close), tint = Blue)
        Text(state.title, Modifier.weight(1f), color = DarkBlue, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(if (state.isCurrentBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "책갈피", Modifier.size(22.dp).clickable(onClick = toggleBookmark), tint = Blue)
        Spacer(Modifier.width(18.dp))
        Icon(Icons.Filled.Timer, "현재 위치", Modifier.size(22.dp), tint = Blue)
        Spacer(Modifier.width(18.dp))
        Icon(Icons.Filled.Settings, "읽기 옵션", Modifier.size(22.dp).clickable(onClick = openSettings), tint = Blue)
    }
    Text(
        state.sentences.joinToString("\n"),
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        style = style(state.displaySettings.getValue(DisplayTarget.VIEWER)),
    )
    Column(Modifier.fillMaxWidth().height(96.dp).background(White).border(width = 1.dp, color = Line).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BookmarkBorder, null, Modifier.size(20.dp), tint = Blue)
            Spacer(Modifier.width(6.dp))
            Text("현재 문서내 책갈피 ${state.bookmarks.size}개", Modifier.weight(1f), color = DarkBlue, fontSize = 14.sp)
            Text("책갈피 목록 ›", Modifier.clickable(onClick = openBookmarks), color = DarkBlue, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☰", color = Muted, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(7.dp).background(Line, RoundedCornerShape(4.dp))) {
                val fraction = if (state.sentences.isEmpty()) 0f else (state.index + 1f) / state.sentences.size
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Blue, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.width(14.dp))
            Text(state.positionLabel, color = DarkBlue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable private fun BookmarkDialog(
    state: ReaderState,
    move: (Int) -> Unit,
    remove: (Int) -> Unit,
    close: () -> Unit,
) = Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Column(Modifier.width(328.dp).height(680.dp).clip(RoundedCornerShape(5.dp)).background(White)) {
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BookmarkBorder, null, Modifier.size(24.dp), tint = Blue)
            Spacer(Modifier.width(6.dp))
            Text("책갈피 목록", color = Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.bookmarks.isEmpty()) item { Text("저장한 책갈피가 없습니다.", Modifier.fillMaxWidth().padding(top = 24.dp), color = Muted, textAlign = TextAlign.Center) }
            items(state.bookmarks.sorted(), key = { it }) { index ->
                Column(Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(5.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BookmarkBorder, null, Modifier.size(22.dp), tint = Blue)
                        Spacer(Modifier.width(8.dp))
                        Text("${index + 1}번째 줄", Modifier.weight(1f), color = BodyColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SmallAction("이동", Blue, White) { move(index); close() }
                        Spacer(Modifier.width(4.dp))
                        SmallAction("삭제", Color.Red, Color.Red, outlined = true) { remove(index) }
                    }
                    Text(state.sentences.getOrNull(index).orEmpty(), color = Ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    state.userTranslations[index]?.let { Text(it, color = Ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(54.dp).border(width = 1.dp, color = Line).clickable(onClick = close), contentAlignment = Alignment.Center) {
            Text("닫기", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val BodyColor = Color(0xFF495057)

@Composable private fun SmallAction(label: String, background: Color, content: Color, outlined: Boolean = false, action: () -> Unit) = Box(
    Modifier.height(24.dp).then(if (outlined) Modifier.border(1.dp, background, RoundedCornerShape(4.dp)) else Modifier.background(background, RoundedCornerShape(4.dp))).clickable(onClick = action).padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center,
) { Text(label, color = content, fontSize = 12.sp) }
@Composable private fun TitleBar(title: String, back: () -> Unit) = Row(Modifier.fillMaxWidth().height(48.dp).background(Color.White).border(1.dp, Line), verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }; Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp); Spacer(Modifier.width(48.dp)) }

@Composable private fun Settings(settings: Map<DisplayTarget, DisplayStyle>, close: () -> Unit, change: (DisplayTarget, DisplayStyle) -> Unit) {
    var target by remember { mutableStateOf(DisplayTarget.ORIGINAL) }; val value = settings.getValue(target)
    Column(Modifier.fillMaxSize().background(Pale)) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(White).border(width = 1.dp, color = Line).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", Modifier.size(22.dp).clickable(onClick = close), tint = Ink)
            Text("읽기 옵션", Modifier.weight(1f), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("저장", Modifier.clickable(onClick = close), color = Blue, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth().height(54.dp).background(White)) {
            listOf(DisplayTarget.ORIGINAL, DisplayTarget.MACHINE_TRANSLATION, DisplayTarget.USER_TRANSLATION).forEach { item ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(if (item == target) Blue else White).border(width = 0.5.dp, color = Line).clickable { target = item },
                    contentAlignment = Alignment.Center,
                ) { Text(item.tab(), color = if (item == target) White else BodyColor, fontSize = 16.sp) }
            }
        }
        Text("설정 미리보기", Modifier.fillMaxWidth().height(40.dp).background(White).padding(horizontal = 16.dp, vertical = 11.dp), color = Muted, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(208.dp).background(value.backgroundColor.color()).padding(16.dp)) {
            Text("적용 예시입니다.\n각 칸별 설정이 가능합니다.\n\nThis is an application example.\nEach column can be set", style = style(value))
        }
        Text("폰트 설정", Modifier.fillMaxWidth().height(40.dp).background(White).padding(horizontal = 16.dp, vertical = 11.dp), color = Muted, fontSize = 12.sp)
        Column(Modifier.fillMaxWidth().background(White).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Setting("폰트 선택") {
                Box(Modifier.width(240.dp).height(44.dp).border(1.dp, Line, RoundedCornerShape(5.dp)).clickable { change(target, value.copy(fontFamily = if (value.fontFamily == "Default") "Serif" else "Default")) }.padding(horizontal = 15.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (value.fontFamily == "Default") "Noto Sans" else "Serif", color = Ink, fontSize = 16.sp)
                    Text("⌄", Modifier.align(Alignment.CenterEnd), color = Muted)
                }
            }
            Palette("배경색", value.backgroundColor) { change(target, value.copy(backgroundColor = it)) }
            Palette("폰트색", value.textColor) { change(target, value.copy(textColor = it)) }
            Spacer(Modifier.height(2.dp))
        }
        Text("본문 설정", Modifier.fillMaxWidth().height(40.dp).background(White).padding(horizontal = 16.dp, vertical = 11.dp), color = Muted, fontSize = 12.sp)
        Column(Modifier.fillMaxWidth().background(White).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Number("글자 크기", value.fontSize, 12f, 40f) { change(target, value.copy(fontSize = it)) }
            Number("줄 간격", value.lineHeight, 1f, 2.5f, .1f) { change(target, value.copy(lineHeight = it)) }
        }
    }
}
@Composable private fun Setting(label: String, content: @Composable () -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontSize = 17.sp); content() }
@Composable private fun Palette(label: String, selected: String, pick: (String) -> Unit) = Setting(label) { listOf("#FFFFFFFF", "#FF4B4B4B", "#FF252525", "#FFECE9E1", "#FFE7D1BD", "#FFC9B683").forEach { hex -> Box(Modifier.padding(start = 6.dp).size(32.dp, 30.dp).background(hex.color(), RoundedCornerShape(4.dp)).border(if (hex == selected) 2.dp else 1.dp, if (hex == selected) Blue else Line, RoundedCornerShape(4.dp)).clickable { pick(hex) }, contentAlignment = Alignment.Center) { if (hex == selected) Text("✓", color = if (hex == "#FFFFFFFF") Ink else White, fontSize = 18.sp) } } }
@Composable private fun Number(label: String, value: Float, min: Float, max: Float, step: Float = 1f, set: (Float) -> Unit) = Setting(label) { SquareAdjust("−") { set((value - step).coerceAtLeast(min)) }; Text(if (step == 1f) value.toInt().toString() else decimal(value), Modifier.width(64.dp), textAlign = TextAlign.Center); SquareAdjust("+") { set((value + step).coerceAtMost(max)) } }
@Composable private fun SquareAdjust(label: String, action: () -> Unit) = Box(Modifier.size(20.dp).background(Blue, RoundedCornerShape(5.dp)).clickable(onClick = action), contentAlignment = Alignment.Center) { Text(label, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
private fun decimal(value: Float) = "${value.toInt()}.${((value * 10).toInt() % 10)}"

@Composable private fun SearchDialog(state: ReaderState, search: (String) -> Unit, move: (Int) -> Unit, close: () -> Unit) = AlertDialog(onDismissRequest = close, title = { Text("문서 검색", color = Blue) }, confirmButton = { TextButton(close) { Text("닫기") } }, text = { Column { PlainField(state.searchQuery, search, "영단어를 입력해 주세요.", Modifier.fillMaxWidth(), TextStyle(fontSize = 16.sp), true); state.searchResults.forEach { index -> Text("${index + 1}. ${state.sentences[index]}", Modifier.fillMaxWidth().clickable { move(index); close() }.padding(vertical = 9.dp)) }; if (state.searchQuery.isNotBlank() && state.searchResults.isEmpty()) Text("검색 결과가 없습니다.") } })
@Composable private fun JumpDialog(state: ReaderState, move: (Int) -> Unit, close: () -> Unit) {
    val lastPosition = state.sentences.size.coerceAtLeast(1)
    var position by remember { mutableFloatStateOf((state.index + 1).toFloat()) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(328.dp).clip(RoundedCornerShape(5.dp)).background(White)) {
            Box(Modifier.fillMaxWidth().height(54.dp).border(width = 1.dp, color = Line), contentAlignment = Alignment.Center) {
                Text("${position.toInt()}번째 줄", color = Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("×", Modifier.align(Alignment.CenterEnd).clickable(onClick = close).padding(16.dp), color = Muted, fontSize = 22.sp)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.sentences.getOrNull(position.toInt() - 1).orEmpty(), color = Ink, fontSize = 16.sp, lineHeight = 23.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Slider(
                    value = position,
                    onValueChange = { position = it },
                    valueRange = 1f..lastPosition.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = Line),
                )
                Box(Modifier.align(Alignment.CenterHorizontally).width(128.dp).height(38.dp).border(1.dp, Line, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                    Text("${position.toInt()}", color = DarkBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("/${state.sentences.size}", Modifier.align(Alignment.CenterEnd).padding(end = 9.dp), color = Muted, fontSize = 16.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(54.dp).border(width = 1.dp, color = Line).clickable { move(position.toInt() - 1); close() }, contentAlignment = Alignment.Center) {
                Text("이동", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable private fun PlainField(value: String, change: (String) -> Unit, placeholder: String, modifier: Modifier, textStyle: TextStyle, singleLine: Boolean = false) = BasicTextField(value, change, modifier.border(1.dp, Line).background(Color.White).padding(12.dp), textStyle = textStyle, singleLine = singleLine, decorationBox = { inner -> Box { if (value.isBlank()) Text(placeholder, color = Color.Gray); inner() } })
private fun DisplayTarget.tab() = when (this) { DisplayTarget.ORIGINAL -> "상단"; DisplayTarget.MACHINE_TRANSLATION -> "중단"; DisplayTarget.USER_TRANSLATION -> "하단"; DisplayTarget.VIEWER -> "전체" }
private fun String.color(fallback: Color = Color.White) = runCatching {
    val rgbOrArgb = removePrefix("#")
    val argb = if (rgbOrArgb.length == 6) "FF$rgbOrArgb" else rgbOrArgb
    Color(argb.toLong(16).toInt())
}.getOrDefault(fallback)
