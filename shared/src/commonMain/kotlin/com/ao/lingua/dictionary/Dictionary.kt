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
    val metadata = repository.metadata()
    var state by mutableStateOf(DictionaryState())
        private set

    fun open(query: String = "") {
        state = state.copy(visible = true)
        if (query.isNotBlank()) search(query)
    }

    fun close() {
        state = state.copy(visible = false)
    }

    fun search(query: String) {
        state = state.copy(
            query = query,
            entries = repository.search(query),
            searched = true,
            remoteTranslation = null,
            translationError = null,
        )
    }

    fun beginTranslation() {
        state = state.copy(translating = true, translationError = null)
    }

    fun finishTranslation(translated: String) {
        state = state.copy(translating = false, remoteTranslation = translated)
    }

    fun failTranslation(message: String) {
        state = state.copy(translating = false, translationError = message)
    }

    fun dismissTranslationError() {
        state = state.copy(translationError = null)
    }

    fun dispose() = repository.close()
}

internal fun dictionaryFileName(bytes: ByteArray): String {
    var hash = 14695981039346656037uL
    bytes.forEach { byte -> hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL }
    return "wiktionary-${bytes.size}-${hash.toString(16)}.db"
}
