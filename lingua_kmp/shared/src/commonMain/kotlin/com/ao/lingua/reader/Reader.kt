package com.ao.lingua.reader

object SentenceSplitter {
    fun normalize(raw: String): String = raw.replace(Regex("([A-Za-z])’([A-Za-z])")) { match ->
        "${match.groupValues[1]}'${match.groupValues[2]}"
    }

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
                    do index++ while (index < text.length && text[index] in ".!?")
                    while (index < text.length && text[index] in "’”") index++
                    add(index)
                }

                '’' -> {
                    index++
                    add(index)
                }

                else -> index++
            }
        }

        if (start < text.length) add(text.length)
        return sentences
    }
}

internal fun findSentenceMatches(sentences: List<String>, query: String): List<Int> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return emptyList()

    // ponytail: 문서 전체 O(n) 검색이 느려질 만큼 커지면 검색 인덱스를 추가한다.
    return sentences.indices.filter { sentences[it].contains(keyword, ignoreCase = true) }
}

data class ReaderState(
    val title: String = "",
    val sentences: List<String> = emptyList(),
    val index: Int = 0,
) {
    val currentSentence: String get() = sentences.getOrNull(index).orEmpty()
    val positionLabel: String get() = "${if (sentences.isEmpty()) 0 else index + 1}/${sentences.size}"
    val canGoPrevious: Boolean get() = index > 0 && sentences.isNotEmpty()
    val canGoNext: Boolean get() = index in 0..<sentences.lastIndex

    fun previous(): ReaderState = if (canGoPrevious) copy(index = index - 1) else this
    fun next(): ReaderState = if (canGoNext) copy(index = index + 1) else this
}
