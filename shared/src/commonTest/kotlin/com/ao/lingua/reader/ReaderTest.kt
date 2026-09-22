package com.ao.lingua.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReaderTest {
    @Test
    fun splitsAndNormalizesDocumentTextWithStableIndexes() {
        assertEquals(
            listOf("I don't know.", "‘Really?’", "A paragraph without punctuation", "Last line"),
            SentenceSplitter.split("I don’t know. ‘Really?’\r\n\r\nA paragraph without punctuation\n\nLast line"),
        )
        assertEquals(
            listOf("Dr. Smith measured 3.14 inches.", "\"Really?\"", "Next."),
            SentenceSplitter.split("Dr. Smith measured 3.14 inches. \"Really?\" Next."),
        )
        assertEquals(
            listOf("Visit example.com for details.", "Next sentence."),
            SentenceSplitter.split("Visit example.com for details. Next sentence."),
        )
    }

    @Test
    fun readerFeaturesShareAndResumeTheSentenceIndexContract() {
        val database = ReaderDatabase(":memory:")
        val store = ReaderStore(database)
        store.openDocument("first.txt", "Repeat. A \"quoted\" line. Repeat.")

        val firstDocumentId = store.state.documentId
        assertEquals(listOf(0, 2), findSentenceMatches(store.state.sentences, " repeat "))
        store.search("repeat")
        store.moveTo(store.state.searchResults.last())
        store.toggleViewer()
        assertTrue(store.state.viewerVisible)
        assertEquals(2, store.state.index)
        store.openBookmarks(returnToViewer = true)
        assertTrue(store.state.bookmarksVisible)
        assertFalse(store.state.viewerVisible)
        assertEquals(2, store.state.index)
        store.toggleBookmark()
        store.saveUserTranslation("반복, 그리고 \"인용\"")
        store.closeBookmarks()
        assertTrue(store.state.viewerVisible)
        store.openSettings(returnToViewer = true)
        assertTrue(store.state.settingsVisible)
        assertFalse(store.state.bookmarksVisible)
        assertEquals(2, store.state.index)
        store.updateDisplayStyle(
            DisplayTarget.USER_TRANSLATION,
            store.state.displaySettings.getValue(DisplayTarget.USER_TRANSLATION).copy(
                fontFamily = "Serif",
                fontSize = 24f,
                lineHeight = 1.8f,
                textColor = "#FFFFFFFF",
                backgroundColor = "#FF1B1B1F",
            ),
        )
        store.closeSettings()
        assertTrue(store.state.viewerVisible)

        val resumed = ReaderStore(database)
        assertEquals(2, resumed.state.index)
        assertEquals(setOf(2), resumed.state.bookmarks)
        assertEquals("반복, 그리고 \"인용\"", resumed.state.currentUserTranslation)
        assertEquals(24f, resumed.state.displaySettings.getValue(DisplayTarget.USER_TRANSLATION).fontSize)
        assertEquals(
            "sentence_index,source,user_translation\r\n2,\"Repeat.\",\"반복, 그리고 \"\"인용\"\"\"\r\n",
            resumed.exportCsv(),
        )

        resumed.openDocument("renamed.txt", "Repeat. A \"quoted\" line. Repeat.")
        assertEquals(firstDocumentId, resumed.state.documentId)
        assertEquals(2, resumed.state.index)

        resumed.openDocument("second.txt", "New first. New second.")
        assertNotEquals(firstDocumentId, resumed.state.documentId)
        assertEquals(0, resumed.state.index)
        assertTrue(resumed.state.bookmarks.isEmpty())
        assertTrue(resumed.state.userTranslations.isEmpty())
        database.close()
    }

    @Test
    fun navigationAndViewerIndexesStopAtDocumentBounds() {
        val database = ReaderDatabase(":memory:")
        val store = ReaderStore(database)
        store.openDocument("bounds.txt", "First. Second.")

        store.previous()
        assertEquals(0, store.state.index)
        assertFalse(store.state.canGoPrevious)
        store.next()
        store.next()
        assertEquals(1, store.state.index)
        assertEquals("2/2", store.state.positionLabel)
        assertTrue(store.state.canGoPrevious)
        assertFalse(store.state.canGoNext)
        store.moveTo(99)
        assertEquals(1, store.state.index)
        database.close()
    }
}
