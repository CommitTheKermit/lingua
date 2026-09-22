package com.ao.lingua.dictionary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY

data class DictionarySense(val sequence: Int, val definition: String)

data class DictionaryEntry(
    val headword: String,
    val partOfSpeech: String,
    val senses: List<DictionarySense>,
)

data class DictionaryMetadata(
    val dataVersion: String,
    val sourceUrl: String,
    val license: String,
)

fun normalizeDictionaryQuery(raw: String): String = raw.trim().lowercase().trim {
    !it.isLetterOrDigit() && it !in "'-"
}

class DictionaryRepository private constructor(private val connection: SQLiteConnection) {
    constructor(fileName: String) : this(
        BundledSQLiteDriver().open(fileName, SQLITE_OPEN_READONLY or SQLITE_OPEN_FULLMUTEX),
    )

    internal constructor(connection: SQLiteConnection, @Suppress("UNUSED_PARAMETER") testing: Unit) : this(connection)

    fun search(raw: String): List<DictionaryEntry> {
        val query = normalizeDictionaryQuery(raw)
        if (query.isEmpty()) return emptyList()
        val entries = linkedMapOf<Long, EntryBuilder>()
        connection.prepare(
            """
            SELECT e.id, e.headword, e.part_of_speech, s.sequence, s.korean_definition
            FROM dictionary_lookup_key AS k
            JOIN dictionary_entry AS e ON e.id = k.entry_id
            JOIN dictionary_sense AS s ON s.entry_id = e.id
            WHERE k.lookup_key = ?
            ORDER BY k.priority, e.headword, e.part_of_speech, s.sequence
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, query)
            while (statement.step()) {
                val id = statement.getLong(0)
                val entry = entries.getOrPut(id) {
                    EntryBuilder(statement.getText(1), statement.getText(2))
                }
                entry.senses += DictionarySense(statement.getLong(3).toInt(), statement.getText(4))
            }
        }
        return entries.values.map { DictionaryEntry(it.headword, it.partOfSpeech, it.senses) }
    }

    fun metadata(): DictionaryMetadata = connection.prepare(
        "SELECT data_version, source_url, license FROM dictionary_metadata LIMIT 1",
    ).use { statement ->
        check(statement.step()) { "Dictionary metadata is missing" }
        DictionaryMetadata(statement.getText(0), statement.getText(1), statement.getText(2))
    }

    fun close() = connection.close()

    private data class EntryBuilder(
        val headword: String,
        val partOfSpeech: String,
        val senses: MutableList<DictionarySense> = mutableListOf(),
    )
}

data class DictionaryState(
    val visible: Boolean = false,
    val query: String = "",
    val entries: List<DictionaryEntry> = emptyList(),
    val searched: Boolean = false,
    val remoteTranslation: String? = null,
    val translating: Boolean = false,
    val translationError: String? = null,
)

class DictionaryStore(private val repository: DictionaryRepository) {
    private var nextTranslationId = 0L
    private var activeTranslation: DictionaryTranslationRequest? = null
    val metadata = repository.metadata()
    var state by mutableStateOf(DictionaryState())
        private set

    fun open(query: String = "") {
        state = state.copy(visible = true)
        search(query)
    }

    fun close() {
        activeTranslation = null
        state = state.copy(visible = false, translating = false, translationError = null)
    }

    fun search(query: String) {
        activeTranslation = null
        state = state.copy(
            query = query,
            entries = repository.search(query),
            searched = normalizeDictionaryQuery(query).isNotBlank(),
            translating = false,
            remoteTranslation = null,
            translationError = null,
        )
    }

    fun beginTranslation(): DictionaryTranslationRequest? {
        if (!state.visible || !state.searched || state.entries.isNotEmpty() || state.translating) return null
        val request = DictionaryTranslationRequest(++nextTranslationId, normalizeDictionaryQuery(state.query))
        activeTranslation = request
        state = state.copy(translating = true, translationError = null)
        return request
    }

    fun finishTranslation(request: DictionaryTranslationRequest, translated: String) {
        if (activeTranslation != request) return
        activeTranslation = null
        state = state.copy(translating = false, remoteTranslation = translated)
    }

    fun failTranslation(request: DictionaryTranslationRequest, message: String) {
        if (activeTranslation != request) return
        activeTranslation = null
        state = state.copy(translating = false, translationError = message)
    }

    fun dismissTranslationError() {
        state = state.copy(translationError = null)
    }

    fun dispose() {
        activeTranslation = null
        repository.close()
    }
}

data class DictionaryTranslationRequest internal constructor(val id: Long, val query: String)

internal fun dictionaryFileName(bytes: ByteArray): String {
    var hash = 14695981039346656037uL
    bytes.forEach { byte -> hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL }
    return "wiktionary-${bytes.size}-${hash.toString(16)}.db"
}
