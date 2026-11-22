package com.therealjidenna.phonescoop.keyboard

class SuggestionEngine(private val dictionary: Set<String>) {

    private val bigrams: Map<String, List<String>> = mapOf(
        "i" to listOf("have", "am", "will", "think", "need"),
        "you" to listOf("have", "are", "can", "should", "need"),
        "we" to listOf("have", "are", "will", "need", "should"),
        "to" to listOf("be", "do", "go", "get", "have"),
        "going" to listOf("to", "home", "out", "there")
    )

    fun getCompletions(partial: String): List<String> {
        if (partial.isBlank()) return emptyList()
        return dictionary
            .filter { it.startsWith(partial, ignoreCase = true) }
            .sortedBy { it.length }
            .take(3)
    }

    fun getNextWordPredictions(lastWord: String): List<String> {
        return bigrams[lastWord.lowercase()] ?: listOf("the", "a", "and")
    }
}
