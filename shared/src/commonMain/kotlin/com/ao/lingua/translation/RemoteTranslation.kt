package com.ao.lingua.translation

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

@Serializable
private data class TranslationRequest(
    val text: String,
    val sourceLang: String = "EN",
    val targetLang: String = "KO",
)

@Serializable
data class RemoteTranslation(
    val translated: String,
    val cached: Boolean = false,
    val quotaRemaining: Int = 200,
    val quotaMax: Int = 200,
    val nextRefillAtMs: Long? = null,
)

class RemoteTranslationClient {
    private val functions = Firebase.functions("asia-northeast3")

    suspend fun translate(text: String): RemoteTranslation {
        if (Firebase.auth.currentUser == null) Firebase.auth.signInAnonymously()
        return functions.httpsCallable("translateProxy")(TranslationRequest(text.trim())).data()
    }

}

fun Throwable.translationMessage(): String = when {
    this is FirebaseFunctionsException && code == FunctionsExceptionCode.RESOURCE_EXHAUSTED ->
        "번역 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."
    else -> "번역에 실패했습니다. 네트워크 연결을 확인해 주세요."
}
