package com.ao.lingua.dictionary

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DictionaryTest {
    @Test
    fun exactLookupNormalizesInputAndPreservesSenseOrder() {
        val connection = BundledSQLiteDriver().open(":memory:", SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
        connection.execSQL("CREATE TABLE dictionary_entry(id INTEGER PRIMARY KEY, headword TEXT, part_of_speech TEXT)")
        connection.execSQL("CREATE TABLE dictionary_sense(entry_id INTEGER, sequence INTEGER, korean_definition TEXT)")
        connection.execSQL("CREATE TABLE dictionary_lookup_key(lookup_key TEXT, entry_id INTEGER, priority INTEGER)")
        connection.execSQL("CREATE TABLE dictionary_metadata(data_version TEXT, source_url TEXT, license TEXT)")
        connection.execSQL("INSERT INTO dictionary_entry VALUES (1, 'run', '동사')")
        connection.execSQL("INSERT INTO dictionary_sense VALUES (1, 1, '운영하다'), (1, 0, '달리다')")
        connection.execSQL("INSERT INTO dictionary_lookup_key VALUES ('run', 1, 0), ('ran', 1, 1)")
        connection.execSQL("INSERT INTO dictionary_metadata VALUES ('fixture', 'https://example.test', 'CC BY-SA 4.0')")
        val repository = DictionaryRepository(connection, Unit)

        assertEquals(listOf("달리다", "운영하다"), repository.search("  RUN! ").single().senses.map { it.definition })
        assertEquals("run", repository.search("ran").single().headword)
        assertTrue(repository.search("missing").isEmpty())
        assertEquals("fixture", repository.metadata().dataVersion)
        repository.close()
    }

    @Test
    fun dismissingTranslationErrorPreservesDictionaryMiss() {
        val connection = BundledSQLiteDriver().open(":memory:", SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
        connection.execSQL("CREATE TABLE dictionary_entry(id INTEGER PRIMARY KEY, headword TEXT, part_of_speech TEXT)")
        connection.execSQL("CREATE TABLE dictionary_sense(entry_id INTEGER, sequence INTEGER, korean_definition TEXT)")
        connection.execSQL("CREATE TABLE dictionary_lookup_key(lookup_key TEXT, entry_id INTEGER, priority INTEGER)")
        connection.execSQL("CREATE TABLE dictionary_metadata(data_version TEXT, source_url TEXT, license TEXT)")
        connection.execSQL("INSERT INTO dictionary_metadata VALUES ('fixture', 'https://example.test', 'CC BY-SA 4.0')")
        val store = DictionaryStore(DictionaryRepository(connection, Unit))

        store.open("missing")
        val request = assertNotNull(store.beginTranslation())
        store.failTranslation(request, "번역에 실패했습니다. 네트워크 연결을 확인해 주세요.")
        store.dismissTranslationError()

        assertTrue(store.state.visible)
        assertTrue(store.state.searched)
        assertEquals("missing", store.state.query)
        assertTrue(store.state.entries.isEmpty())
        assertFalse(store.state.translating)
        assertEquals(null, store.state.translationError)
        store.dispose()
    }

    @Test
    fun changingSearchIgnoresOldSuccessAndAllowsNewTranslation() = withStore { store ->
        store.open("missing-one")
        val first = assertNotNull(store.beginTranslation())
        store.search("missing-two")
        assertFalse(store.state.translating)
        val second = assertNotNull(store.beginTranslation())

        store.finishTranslation(first, "이전 결과")
        assertNull(store.state.remoteTranslation)
        assertTrue(store.state.translating)
        store.finishTranslation(second, "새 결과")
        assertEquals("missing-two", store.state.query)
        assertEquals("새 결과", store.state.remoteTranslation)
        assertFalse(store.state.translating)
    }

    @Test
    fun closingAndReopeningSameWordIgnoresOldFailure() = withStore { store ->
        store.open("missing")
        val first = assertNotNull(store.beginTranslation())
        store.close()
        store.open("missing")
        val second = assertNotNull(store.beginTranslation())
        store.failTranslation(first, "이전 오류")
        assertNull(store.state.translationError)
        assertTrue(store.state.translating)
        store.finishTranslation(second, "새 결과")
        assertEquals("새 결과", store.state.remoteTranslation)
        store.open()
        assertEquals("", store.state.query)
        assertFalse(store.state.searched)
        assertNull(store.state.remoteTranslation)
    }

    @Test
    fun onlyVisibleDictionaryMissCanStartOneTranslation() = withStore { store ->
        assertNull(store.beginTranslation())
        store.open(" ! ")
        assertNull(store.beginTranslation())
        store.search("run")
        assertNull(store.beginTranslation())
        store.search(" MISSING! ")
        assertEquals("missing", assertNotNull(store.beginTranslation()).query)
        assertNull(store.beginTranslation())
        store.close()
        assertNull(store.beginTranslation())
    }

    private fun withStore(test: (DictionaryStore) -> Unit) {
        val connection = BundledSQLiteDriver().open(":memory:", SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
        connection.execSQL("CREATE TABLE dictionary_entry(id INTEGER PRIMARY KEY, headword TEXT, part_of_speech TEXT)")
        connection.execSQL("CREATE TABLE dictionary_sense(entry_id INTEGER, sequence INTEGER, korean_definition TEXT)")
        connection.execSQL("CREATE TABLE dictionary_lookup_key(lookup_key TEXT, entry_id INTEGER, priority INTEGER)")
        connection.execSQL("CREATE TABLE dictionary_metadata(data_version TEXT, source_url TEXT, license TEXT)")
        connection.execSQL("INSERT INTO dictionary_entry VALUES (1, 'run', '동사')")
        connection.execSQL("INSERT INTO dictionary_sense VALUES (1, 0, '달리다')")
        connection.execSQL("INSERT INTO dictionary_lookup_key VALUES ('run', 1, 0)")
        connection.execSQL("INSERT INTO dictionary_metadata VALUES ('fixture', 'https://example.test', 'CC BY-SA 4.0')")
        val store = DictionaryStore(DictionaryRepository(connection, Unit))
        try {
            test(store)
        } finally {
            store.dispose()
        }
    }

}
