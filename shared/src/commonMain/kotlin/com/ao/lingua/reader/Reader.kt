package com.ao.lingua.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SentenceSplitter {
    const val VERSION = 2

    private val abbreviations = setOf(
        "dr", "e.g", "etc", "i.e", "jr", "mr", "mrs", "ms", "prof", "sr", "st", "u.k", "u.s", "vs",
    )

    fun normalize(raw: String): String = raw.replace(Regex("([A-Za-z])’([A-Za-z])")) { match ->
        "${match.groupValues[1]}'${match.groupValues[2]}"
    }.replace("\r\n", "\n").replace('\r', '\n')

    fun split(raw: String): List<String> {
        val text = normalize(raw)
        val sentences = mutableListOf<String>()
        var start = 0
        var index = 0

        fun add(endExclusive: Int) {
            text.substring(start, endExclusive).trim().takeIf(String::isNotEmpty)?.let(sentences::add)
            start = endExclusive
        }

        while (index < text.length) {
            when (text[index]) {
                '.', '!', '?' -> {
                    if (text[index] == '.' && !isSentenceDot(text, index)) {
                        index++
                        continue
                    }
                    do index++ while (index < text.length && text[index] in ".!?")
                    while (index < text.length && text[index] in "'’\"”)]}") index++
                    add(index)
                }

                '\n' -> {
                    val paragraphEnd = index
                    while (index < text.length && text[index] == '\n') index++
                    if (index - paragraphEnd > 1) add(paragraphEnd)
                }

                else -> index++
            }
        }

        if (start < text.length) add(text.length)
        return sentences
    }

    private fun isSentenceDot(text: String, dotIndex: Int): Boolean {
        if (text.getOrNull(dotIndex - 1)?.isLetterOrDigit() == true &&
            text.getOrNull(dotIndex + 1)?.isLetterOrDigit() == true
        ) {
            return false
        }
        var start = dotIndex - 1
        while (start >= 0 && (text[start].isLetter() || text[start] == '.')) start--
        val token = text.substring(start + 1, dotIndex).lowercase().trim('.')
        if (token in abbreviations) return false
        if (token.length == 1 && token != "i" && token[0].isLetter()) return false
        return true
    }
}

internal fun findSentenceMatches(sentences: List<String>, query: String): List<Int> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return emptyList()

    // ponytail: 문서 전체 O(n) 검색이 느려질 만큼 커지면 검색 인덱스를 추가한다.
    return sentences.indices.filter { sentences[it].contains(keyword, ignoreCase = true) }
}

enum class DisplayTarget {
    ORIGINAL,
    MACHINE_TRANSLATION,
    USER_TRANSLATION,
    VIEWER,
}

data class DisplayStyle(
    val fontFamily: String = "Default",
    val fontSize: Float = 20f,
    val lineHeight: Float = 1.5f,
    val textColor: String = "#FF1B1B1F",
    val backgroundColor: String = "#FFFFFFFF",
)

internal fun defaultDisplaySettings(): Map<DisplayTarget, DisplayStyle> = DisplayTarget.entries.associateWith {
    DisplayStyle(fontSize = if (it == DisplayTarget.VIEWER) 17f else 20f)
}

data class ReaderState(
    val documentId: String = "",
    val title: String = "",
    val sentences: List<String> = emptyList(),
    val index: Int = 0,
    val searchQuery: String = "",
    val searchResults: List<Int> = emptyList(),
    val bookmarks: Set<Int> = emptySet(),
    val userTranslations: Map<Int, String> = emptyMap(),
    val displaySettings: Map<DisplayTarget, DisplayStyle> = defaultDisplaySettings(),
    val viewerVisible: Boolean = false,
    val bookmarksVisible: Boolean = false,
    val settingsVisible: Boolean = false,
) {
    val currentSentence: String get() = sentences.getOrNull(index).orEmpty()
    val currentUserTranslation: String get() = userTranslations[index].orEmpty()
    val positionLabel: String get() = "${if (sentences.isEmpty()) 0 else index + 1}/${sentences.size}"
    val canGoPrevious: Boolean get() = index > 0 && sentences.isNotEmpty()
    val canGoNext: Boolean get() = index in 0..<sentences.lastIndex
    val isCurrentBookmarked: Boolean get() = index in bookmarks
}

class ReaderStore(private val database: ReaderDatabase) {
    var state by mutableStateOf(database.resume() ?: ReaderState(displaySettings = database.loadDisplaySettings()))
        private set

    fun openDocument(title: String, content: String) {
        state = database.openDocument(title, content)
    }

    fun previous() = moveTo(state.index - 1)

    fun next() = moveTo(state.index + 1)

    fun moveTo(sentenceIndex: Int) {
        if (sentenceIndex !in state.sentences.indices || sentenceIndex == state.index) return
        database.savePosition(state.documentId, sentenceIndex)
        state = state.copy(index = sentenceIndex)
    }

    fun search(query: String) {
        state = state.copy(searchQuery = query, searchResults = findSentenceMatches(state.sentences, query))
    }

    fun toggleBookmark() {
        if (state.sentences.isEmpty()) return
        val bookmarked = !state.isCurrentBookmarked
        database.setBookmark(state.documentId, state.index, bookmarked)
        state = state.copy(
            bookmarks = if (bookmarked) state.bookmarks + state.index else state.bookmarks - state.index,
        )
    }

    fun removeBookmark(sentenceIndex: Int) {
        if (sentenceIndex !in state.bookmarks) return
        database.setBookmark(state.documentId, sentenceIndex, false)
        state = state.copy(bookmarks = state.bookmarks - sentenceIndex)
    }

    fun saveUserTranslation(text: String) {
        if (state.sentences.isEmpty()) return
        database.saveUserTranslation(state.documentId, state.index, text)
        state = state.copy(
            userTranslations = if (text.isBlank()) {
                state.userTranslations - state.index
            } else {
                state.userTranslations + (state.index to text)
            },
        )
    }

    fun updateDisplayStyle(target: DisplayTarget, style: DisplayStyle) {
        database.saveDisplayStyle(target, style)
        state = state.copy(displaySettings = state.displaySettings + (target to style))
    }

    fun toggleViewer() {
        state = state.copy(viewerVisible = !state.viewerVisible, bookmarksVisible = false, settingsVisible = false)
    }

    fun toggleBookmarks() {
        state = state.copy(bookmarksVisible = !state.bookmarksVisible, settingsVisible = false)
    }

    fun toggleSettings() {
        state = state.copy(settingsVisible = !state.settingsVisible, viewerVisible = false, bookmarksVisible = false)
    }

    fun exportCsv(): String = buildString {
        append("sentence_index,source,user_translation\r\n")
        state.userTranslations.entries.sortedBy { it.key }.forEach { (index, translation) ->
            append(index)
            append(',')
            append(state.sentences[index].csvCell())
            append(',')
            append(translation.csvCell())
            append("\r\n")
        }
    }
}

internal fun contentId(content: String): String {
    var hash = 14695981039346656037uL
    val bytes = content.encodeToByteArray()
    bytes.forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
    }
    return "${bytes.size}-${hash.toString(16)}"
}

private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""
