package com.ao.lingua.translation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class SentenceTranslationKey(val documentId: String, val index: Int, val sentence: String)

data class MachineTranslationState(
    val key: SentenceTranslationKey? = null,
    val translated: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class MachineTranslationStore {
    private val cache = mutableMapOf<SentenceTranslationKey, String>()
    private var nextRequestId = 0L
    private var activeRequest: TranslationJob? = null

    var state by mutableStateOf(MachineTranslationState())
        private set

    fun select(key: SentenceTranslationKey) {
        if (state.key == key) return
        activeRequest = null
        state = MachineTranslationState(key = key, translated = cache[key])
    }

    fun deactivate() {
        activeRequest = null
        state = state.copy(loading = false)
    }

    fun begin(): TranslationJob? {
        val key = state.key ?: return null
        if (key.sentence.isBlank() || state.translated != null || state.loading || state.error != null) return null
        val job = TranslationJob(++nextRequestId, key)
        activeRequest = job
        state = state.copy(loading = true)
        return job
    }

    fun retry(): TranslationJob? {
        state = state.copy(error = null)
        return begin()
    }

    fun finish(job: TranslationJob, translated: String) {
        if (activeRequest != job) return
        activeRequest = null
        cache[job.key] = translated
        state = state.copy(translated = translated, loading = false)
    }

    fun fail(job: TranslationJob, message: String) {
        if (activeRequest != job) return
        activeRequest = null
        state = state.copy(loading = false, error = message)
    }
}

class TranslationJob internal constructor(val id: Long, val key: SentenceTranslationKey)
