package com.ao.lingua

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ao.lingua.dictionary.DictionaryRepository
import com.ao.lingua.dictionary.DictionarySheet
import com.ao.lingua.dictionary.DictionaryStore
import com.ao.lingua.dictionary.dictionaryFileName
import com.ao.lingua.dictionary.rememberDictionaryInstaller
import com.ao.lingua.reader.CsvDocument
import com.ao.lingua.reader.ReaderScreen
import com.ao.lingua.reader.ReaderStore
import com.ao.lingua.reader.rememberCsvExporter
import com.ao.lingua.reader.rememberReaderDatabase
import com.ao.lingua.reader.rememberTextFilePicker
import lingua.shared.generated.resources.Res
import com.ao.lingua.translation.RemoteTranslationClient
import com.ao.lingua.translation.MachineTranslationStore
import com.ao.lingua.translation.SentenceTranslationKey
import com.ao.lingua.translation.TranslationJob
import com.ao.lingua.translation.translationMessage
import com.ao.lingua.ui.linguaTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun App(translationAvailable: Boolean) {
    val database = rememberReaderDatabase()
    val store = remember(database) { ReaderStore(database) }
    val exportCsv = rememberCsvExporter()
    val openFile = rememberTextFilePicker { file ->
        file?.let { store.openDocument(it.name, it.content) }
    }
    val installDictionary = rememberDictionaryInstaller()
    var dictionaryStore by remember { mutableStateOf<DictionaryStore?>(null) }
    var showSplash by remember { mutableStateOf(true) }
    var translationUsageLabel by remember { mutableStateOf("확인 전") }
    var translationVisible by remember { mutableStateOf(true) }
    val translationClient = remember { RemoteTranslationClient() }
    val machineTranslation = remember { MachineTranslationStore() }
    val scope = rememberCoroutineScope()
    val sentenceKey = SentenceTranslationKey(store.state.documentId, store.state.index, store.state.currentSentence)
    val shownTranslation = machineTranslation.state.takeIf { it.key == sentenceKey }
    val translateSentence: suspend (TranslationJob) -> Unit = { job ->
        if (!translationAvailable) {
            machineTranslation.fail(job, "온라인 번역 설정이 준비되지 않았습니다.")
        } else {
            try {
                val result = translationClient.translate(job.key.sentence)
                val used = (result.quotaMax - result.quotaRemaining).coerceAtLeast(0)
                translationUsageLabel = "$used/${result.quotaMax}"
                machineTranslation.finish(job, result.translated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = failure.translationMessage()
                if (message.contains("한도")) translationUsageLabel = "한도 도달"
                machineTranslation.fail(job, message)
            }
        }
    }
    LaunchedEffect(sentenceKey, translationVisible, store.state.viewerVisible, store.state.settingsVisible, showSplash) {
        if (showSplash || !translationVisible || store.state.viewerVisible || store.state.settingsVisible || sentenceKey.sentence.isBlank()) {
            machineTranslation.deactivate()
        } else {
            machineTranslation.select(sentenceKey)
            machineTranslation.begin()?.let { translateSentence(it) }
        }
    }
    LaunchedEffect(installDictionary) {
        dictionaryStore = withContext(Dispatchers.Default) {
            val bytes = Res.readBytes("files/dict/wiktionary_en_ko.db")
            val path = installDictionary(bytes, dictionaryFileName(bytes))
            DictionaryStore(DictionaryRepository(path))
        }
    }
    LaunchedEffect(Unit) {
        delay(750)
        showSplash = false
    }
    DisposableEffect(Unit) {
        onDispose { dictionaryStore?.dispose() }
    }

    MaterialTheme(
        typography = linguaTypography(),
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF44698F),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            surface = androidx.compose.ui.graphics.Color(0xFFF8F9FA),
            onSurface = androidx.compose.ui.graphics.Color(0xFF181B1E),
        ),
    ) {
        if (showSplash) {
            SplashScreen()
        } else ReaderScreen(
            state = store.state,
            onOpenFile = openFile,
            onPrevious = store::previous,
            onNext = store::next,
            onMoveTo = store::moveTo,
            onToggleViewer = store::toggleViewer,
            onOpenBookmarks = store::openBookmarks,
            onCloseBookmarks = store::closeBookmarks,
            onToggleBookmark = store::toggleBookmark,
            onRemoveBookmark = store::removeBookmark,
            onUserTranslationChange = store::saveUserTranslation,
            onOpenSettings = store::openSettings,
            onCloseSettings = store::closeSettings,
            onDisplayStyleChange = store::updateDisplayStyle,
            onExportCsv = {
                exportCsv(CsvDocument("${store.state.title.substringBeforeLast('.')}.csv", store.exportCsv()))
            },
            onOpenDictionary = { query -> dictionaryStore?.open(query) },
            translationUsageLabel = translationUsageLabel,
            translationVisible = translationVisible,
            onToggleTranslation = { translationVisible = !translationVisible },
            machineTranslation = shownTranslation,
            onRetryTranslation = {
                machineTranslation.retry()?.let { job -> scope.launch { translateSentence(job) } }
            },
        )
        if (!showSplash) dictionaryStore?.let { dictionary ->
            if (dictionary.state.visible) {
                DictionarySheet(
                    state = dictionary.state,
                    metadata = dictionary.metadata,
                    onDismiss = dictionary::close,
                    onSearch = dictionary::search,
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() = Box(
    Modifier.fillMaxSize().background(Color(0xFF44698F)),
    contentAlignment = Alignment.Center,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Text("✒", color = Color.White, fontSize = 76.sp)
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.Text("Lingua", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 19.sp, letterSpacing = 9.5.sp)
    }
}
