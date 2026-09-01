package com.ao.lingua.reader

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL

class ReaderDatabase(fileName: String) {
    private val connection = BundledSQLiteDriver().open(
        fileName,
        SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX,
    )

    init {
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS document (
                singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
                content_id TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                last_index INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_translation (
                document_id TEXT NOT NULL,
                sentence_index INTEGER NOT NULL,
                text TEXT NOT NULL,
                PRIMARY KEY (document_id, sentence_index)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmark (
                document_id TEXT NOT NULL,
                sentence_index INTEGER NOT NULL,
                PRIMARY KEY (document_id, sentence_index)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS display_setting (
                target TEXT PRIMARY KEY,
                font_family TEXT NOT NULL,
                font_size REAL NOT NULL,
                line_height REAL NOT NULL,
                text_color TEXT NOT NULL,
                background_color TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    fun openDocument(title: String, content: String): ReaderState {
        val id = contentId(content)
        val currentId = connection.prepare("SELECT content_id FROM document WHERE singleton = 1").use { statement ->
            if (statement.step()) statement.getText(0) else null
        }

        transaction {
            if (currentId == id) {
                connection.prepare("UPDATE document SET title = ?, content = ? WHERE singleton = 1").use { statement ->
                    statement.bindText(1, title)
                    statement.bindText(2, content)
                    statement.step()
                }
            } else {
                connection.execSQL("DELETE FROM bookmark")
                connection.execSQL("DELETE FROM user_translation")
                connection.execSQL("DELETE FROM document")
                connection.prepare(
                    "INSERT INTO document(singleton, content_id, title, content, last_index) VALUES (1, ?, ?, ?, 0)",
                ).use { statement ->
                    statement.bindText(1, id)
                    statement.bindText(2, title)
                    statement.bindText(3, content)
                    statement.step()
                }
            }
        }
        return checkNotNull(resume())
    }

    fun resume(): ReaderState? {
        val document = connection.prepare(
            "SELECT content_id, title, content, last_index FROM document WHERE singleton = 1",
        ).use { statement ->
            if (!statement.step()) return null
            StoredDocument(
                id = statement.getText(0),
                title = statement.getText(1),
                content = statement.getText(2),
                index = statement.getLong(3).toInt(),
            )
        }
        val sentences = SentenceSplitter.split(document.content)
        val safeIndex = document.index.coerceIn(0, sentences.lastIndex.coerceAtLeast(0))
        if (safeIndex != document.index) savePosition(document.id, safeIndex)

        return ReaderState(
            documentId = document.id,
            title = document.title,
            sentences = sentences,
            index = safeIndex,
            bookmarks = loadBookmarks(document.id),
            userTranslations = loadUserTranslations(document.id),
            displaySettings = loadDisplaySettings(),
        )
    }

    fun savePosition(documentId: String, sentenceIndex: Int) {
        connection.prepare(
            "UPDATE document SET last_index = ? WHERE singleton = 1 AND content_id = ?",
        ).use { statement ->
            statement.bindLong(1, sentenceIndex.toLong())
            statement.bindText(2, documentId)
            statement.step()
        }
    }

    fun setBookmark(documentId: String, sentenceIndex: Int, bookmarked: Boolean) {
        val sql = if (bookmarked) {
            "INSERT OR IGNORE INTO bookmark(document_id, sentence_index) VALUES (?, ?)"
        } else {
            "DELETE FROM bookmark WHERE document_id = ? AND sentence_index = ?"
        }
        connection.prepare(sql).use { statement ->
            statement.bindText(1, documentId)
            statement.bindLong(2, sentenceIndex.toLong())
            statement.step()
        }
    }

    fun saveUserTranslation(documentId: String, sentenceIndex: Int, text: String) {
        if (text.isBlank()) {
            connection.prepare(
                "DELETE FROM user_translation WHERE document_id = ? AND sentence_index = ?",
            ).use { statement ->
                statement.bindText(1, documentId)
                statement.bindLong(2, sentenceIndex.toLong())
                statement.step()
            }
            return
        }
        connection.prepare(
            """
            INSERT INTO user_translation(document_id, sentence_index, text) VALUES (?, ?, ?)
            ON CONFLICT(document_id, sentence_index) DO UPDATE SET text = excluded.text
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, documentId)
            statement.bindLong(2, sentenceIndex.toLong())
            statement.bindText(3, text)
            statement.step()
        }
    }

    fun saveDisplayStyle(target: DisplayTarget, style: DisplayStyle) {
        connection.prepare(
            """
            INSERT INTO display_setting(
                target, font_family, font_size, line_height, text_color, background_color
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(target) DO UPDATE SET
                font_family = excluded.font_family,
                font_size = excluded.font_size,
                line_height = excluded.line_height,
                text_color = excluded.text_color,
                background_color = excluded.background_color
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, target.name)
            statement.bindText(2, style.fontFamily)
            statement.bindDouble(3, style.fontSize.toDouble())
            statement.bindDouble(4, style.lineHeight.toDouble())
            statement.bindText(5, style.textColor)
            statement.bindText(6, style.backgroundColor)
            statement.step()
        }
    }

    fun loadDisplaySettings(): Map<DisplayTarget, DisplayStyle> {
        val settings = defaultDisplaySettings().toMutableMap()
        connection.prepare(
            "SELECT target, font_family, font_size, line_height, text_color, background_color FROM display_setting",
        ).use { statement ->
            while (statement.step()) {
                val target = runCatching { DisplayTarget.valueOf(statement.getText(0)) }.getOrNull() ?: continue
                settings[target] = DisplayStyle(
                    fontFamily = statement.getText(1),
                    fontSize = statement.getDouble(2).toFloat(),
                    lineHeight = statement.getDouble(3).toFloat(),
                    textColor = statement.getText(4),
                    backgroundColor = statement.getText(5),
                )
            }
        }
        return settings
    }

    fun close() = connection.close()

    private fun loadBookmarks(documentId: String): Set<Int> = buildSet {
        connection.prepare(
            "SELECT sentence_index FROM bookmark WHERE document_id = ? ORDER BY sentence_index",
        ).use { statement ->
            statement.bindText(1, documentId)
            while (statement.step()) add(statement.getLong(0).toInt())
        }
    }

    private fun loadUserTranslations(documentId: String): Map<Int, String> = buildMap {
        connection.prepare(
            "SELECT sentence_index, text FROM user_translation WHERE document_id = ?",
        ).use { statement ->
            statement.bindText(1, documentId)
            while (statement.step()) put(statement.getLong(0).toInt(), statement.getText(1))
        }
    }

    private inline fun transaction(block: () -> Unit) {
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            block()
            connection.execSQL("COMMIT")
        } catch (error: Throwable) {
            connection.execSQL("ROLLBACK")
            throw error
        }
    }

    private data class StoredDocument(
        val id: String,
        val title: String,
        val content: String,
        val index: Int,
    )
}
