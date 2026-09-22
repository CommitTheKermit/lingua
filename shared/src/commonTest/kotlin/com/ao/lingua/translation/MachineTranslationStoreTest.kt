package com.ao.lingua.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class MachineTranslationStoreTest {
    @Test
    fun navigationKeepsResultsBoundToTheirSentenceAndReusesCompletedTranslation() {
        val store = MachineTranslationStore()
        val first = SentenceTranslationKey("document", 0, "First sentence.")
        val second = SentenceTranslationKey("document", 1, "Second sentence.")

        store.select(first)
        val firstJob = assertNotNull(store.begin())
        store.select(second)
        val secondJob = assertNotNull(store.begin())
        store.finish(firstJob, "잘못 도착한 첫 문장")
        assertNull(store.state.translated)
        store.finish(secondJob, "두 번째 문장")
        assertEquals("두 번째 문장", store.state.translated)

        store.select(first)
        assertNull(store.state.translated)
        val newFirstJob = assertNotNull(store.begin())
        store.finish(newFirstJob, "첫 번째 문장")
        store.select(second)
        assertEquals("두 번째 문장", store.state.translated)
        assertNull(store.begin())
    }

    @Test
    fun failedSentenceCanRetryWithoutAcceptingPreviousResponse() {
        val store = MachineTranslationStore()
        store.select(SentenceTranslationKey("document", 0, "Sentence."))
        val firstJob = assertNotNull(store.begin())
        store.fail(firstJob, "연결 오류")
        assertEquals("연결 오류", store.state.error)
        assertNull(store.begin())
        val retryJob = assertNotNull(store.retry())
        store.finish(firstJob, "오래된 결과")
        assertNull(store.state.translated)
        store.finish(retryJob, "새 결과")
        assertEquals("새 결과", store.state.translated)
        assertFalse(store.state.loading)
    }

    @Test
    fun hidingTranslationCancelsVisibleRequestAndAllowsResume() {
        val store = MachineTranslationStore()
        store.select(SentenceTranslationKey("document", 0, "Sentence."))
        val hiddenJob = assertNotNull(store.begin())
        store.deactivate()
        assertFalse(store.state.loading)
        val resumedJob = assertNotNull(store.begin())
        store.finish(hiddenJob, "숨겨진 요청")
        assertNull(store.state.translated)
        store.finish(resumedJob, "다시 연 요청")
        assertEquals("다시 연 요청", store.state.translated)
    }
}
