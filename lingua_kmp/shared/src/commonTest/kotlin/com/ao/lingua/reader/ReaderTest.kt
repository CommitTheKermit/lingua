package com.ao.lingua.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTest {
    @Test
    fun splitsAndNormalizesDocumentText() {
        assertEquals(
            listOf("I don't know.", "‘Really?’", "Last line"),
            SentenceSplitter.split("I don’t know. ‘Really?’ Last line"),
        )
    }

    @Test
    fun keepsRepeatedMatchesAtDistinctIndexes() {
        assertEquals(
            listOf(0, 2),
            findSentenceMatches(listOf("Repeat.", "Between.", "Repeat."), " repeat "),
        )
    }

    @Test
    fun navigationStopsAtDocumentBounds() {
        val first = ReaderState(sentences = listOf("First.", "Second."))
        val second = first.next()

        assertEquals(first, first.previous())
        assertEquals("Second.", second.currentSentence)
        assertEquals("2/2", second.positionLabel)
        assertTrue(second.canGoPrevious)
        assertFalse(second.canGoNext)
        assertEquals(second, second.next())
    }
}
