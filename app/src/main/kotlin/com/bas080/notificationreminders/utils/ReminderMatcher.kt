@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders.utils

object ReminderMatcher {

    val DEFAULT_COMMON_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
        "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
        "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
        "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
        "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no",
        "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
        "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
        "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
        "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those",
        "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll",
        "we're", "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's",
        "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't",
        "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves"
    )

    fun parseCommonWords(commaSeparated: String): Set<String> {
        if (commaSeparated.isBlank()) return DEFAULT_COMMON_WORDS
        return commaSeparated.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun extractNonCommonWords(
        text: String,
        commonWords: Set<String> = DEFAULT_COMMON_WORDS
    ): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { word -> word.length >= 2 && word !in commonWords }
            .toSet()
    }

    /**
     * Checks if a reminder matches the notification content based on non-common word matching
     * or substring inclusion.
     */
    fun matches(
        reminder: String,
        notificationContent: String,
        commonWords: Set<String> = DEFAULT_COMMON_WORDS
    ): Boolean {
        val trimmed = reminder.trim()
        if (trimmed.isEmpty()) return false

        val reminderWords = extractNonCommonWords(trimmed, commonWords)
        val notificationWords = extractNonCommonWords(notificationContent, commonWords)

        if (reminderWords.isNotEmpty() && notificationWords.isNotEmpty()) {
            if (reminderWords.any { it in notificationWords }) {
                return true
            }
        }

        return notificationContent.contains(trimmed, ignoreCase = true)
    }

    /**
     * Filters a list of reminders using a 4-tiered search matching algorithm.
     * Higher tiers prevent lower tiers from evaluating if any matches are found.
     *
     * Tier 1: Case-insensitive AND word match
     * Tier 2: Case-insensitive AND substring match
     * Tier 3: Case-insensitive OR word match
     * Tier 4: Case-insensitive OR substring match
     */
    fun filterSearchQueryTiered(reminders: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return reminders

        fun tokenize(text: String): List<String> {
            return text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        }

        val rawQueryWords = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (rawQueryWords.isEmpty()) return reminders

        val tokenizedQueryWords = tokenize(q)

        // Tier 1: Case-insensitive AND word match
        if (tokenizedQueryWords.isNotEmpty()) {
            val tier1 = reminders.filter { reminder ->
                val words = tokenize(reminder)
                tokenizedQueryWords.all { qWord -> words.contains(qWord) }
            }
            if (tier1.isNotEmpty()) return tier1
        }

        // Tier 2: Case-insensitive AND substring match
        val tier2 = reminders.filter { reminder ->
            rawQueryWords.all { qWord -> reminder.contains(qWord, ignoreCase = true) }
        }
        if (tier2.isNotEmpty()) return tier2

        // Tier 3: Case-insensitive OR word match
        if (tokenizedQueryWords.isNotEmpty()) {
            val tier3 = reminders.filter { reminder ->
                val words = tokenize(reminder)
                tokenizedQueryWords.any { qWord -> words.contains(qWord) }
            }
            if (tier3.isNotEmpty()) return tier3
        }

        // Tier 4: Case-insensitive OR substring match
        val tier4 = reminders.filter { reminder ->
            rawQueryWords.any { qWord -> reminder.contains(qWord, ignoreCase = true) }
        }
        if (tier4.isNotEmpty()) return tier4

        return emptyList()
    }

    /**
     * Lenient search matching function to check if a reminder matches a search query.
     */
    @Suppress("UNUSED_PARAMETER")
    fun matchesSearchQuery(
        reminder: String,
        query: String,
        commonWords: Set<String> = DEFAULT_COMMON_WORDS
    ): Boolean {
        return filterSearchQueryTiered(listOf(reminder), query).isNotEmpty()
    }
}
