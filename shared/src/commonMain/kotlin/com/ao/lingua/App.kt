package com.ao.lingua

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.ao.lingua.dictionary.DictionaryRepository
import com.ao.lingua.dictionary.DictionarySheet
import com.ao.lingua.dictionary.DictionaryStore
import com.ao.lingua.dictionary.TranslationErrorDialog
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
import com.ao.lingua.translation.translationMessage
import kotlinx.coroutines.launch

@Composable
fun App() {
    val database = rememberReaderDatabase()
    val store = remember(database) { ReaderStore(database) }
    val exportCsv = rememberCsvExporter()
    val openFile = rememberTextFilePicker { file ->
        file?.let { store.openDocument(it.name, it.content) }
    }
    val installDictionary = rememberDictionaryInstaller()
    var dictionaryStore by remember { mutableStateOf<DictionaryStore?>(null) }
    val translationClient = remember { RemoteTranslationClient() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(installDictionary) {
        val bytes = Res.readBytes("files/dict/wiktionary_en_ko.db")
        val path = installDictionary(bytes, dictionaryFileName(bytes))
        dictionaryStore = DictionaryStore(DictionaryRepository(path))
    }
    DisposableEffect(Unit) {
        onDispose { dictionaryStore?.dispose() }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF466F99),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            surface = androidx.compose.ui.graphics.Color(0xFFF8F9FB),
            onSurface = androidx.compose.ui.graphics.Color(0xFF202124),
        ),
    ) {
        ReaderScreen(
            state = store.state,
            onOpenFile = openFile,
            onPrevious = store::previous,
            onNext = store::next,
            onMoveTo = store::moveTo,
            onSearch = store::search,
            onToggleViewer = store::toggleViewer,
            onToggleBookmarks = store::toggleBookmarks,
            onToggleBookmark = store::toggleBookmark,
            onUserTranslationChange = store::saveUserTranslation,
            onToggleSettings = store::toggleSettings,
            onDisplayStyleChange = store::updateDisplayStyle,
            onExportCsv = {
                exportCsv(CsvDocument("${store.state.title.substringBeforeLast('.')}.csv", store.exportCsv()))
            },
            onOpenDictionary = { query -> dictionaryStore?.open(query) },
        )
        dictionaryStore?.let { dictionary ->
            if (dictionary.state.visible) {
                DictionarySheet(
                    state = dictionary.state,
                    metadata = dictionary.metadata,
                    onDismiss = dictionary::close,
                    onSearch = dictionary::search,
                    onTranslate = {
                        dictionary.beginTranslation()
                        scope.launch {
                            runCatching { translationClient.translate(dictionary.state.query) }
                                .onSuccess { result ->
                                    dictionary.finishTranslation(result.translated)
                                }
                                .onFailure { dictionary.failTranslation(it.translationMessage()) }
                        }
                    },
                )
            }
            dictionary.state.translationError?.let { message ->
                TranslationErrorDialog(message, dictionary::dismissTranslationError)
            }
        }
    }
}
