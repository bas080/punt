@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders.utils

object MarkdownRemindersUtil {

    fun exportToMarkdown(reminders: List<String>): String {
        if (reminders.isEmpty()) return ""
        return reminders.joinToString("\n") { reminder ->
            "- [ ] ${reminder.trim()}"
        }
    }

    fun importFromMarkdown(markdownContent: String): List<String> {
        if (markdownContent.isBlank()) return emptyList()

        val results = mutableListOf<String>()
        val lines = markdownContent.lines()

        val checklistRegex = Regex("^\\s*[-*+]\\s*\\[[ xX]?\\]\\s*(.+)$")
        val bulletRegex = Regex("^\\s*[-*+]\\s+(.+)$")
        val numberRegex = Regex("^\\s*\\d+\\.\\s+(.+)$")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val checklistMatch = checklistRegex.find(line)
            if (checklistMatch != null) {
                val item = checklistMatch.groupValues[1].trim()
                if (item.isNotEmpty() && !results.contains(item)) {
                    results.add(item)
                }
                continue
            }

            val bulletMatch = bulletRegex.find(line)
            if (bulletMatch != null) {
                val item = bulletMatch.groupValues[1].trim()
                if (item.isNotEmpty() && !results.contains(item)) {
                    results.add(item)
                }
                continue
            }

            val numberMatch = numberRegex.find(line)
            if (numberMatch != null) {
                val item = numberMatch.groupValues[1].trim()
                if (item.isNotEmpty() && !results.contains(item)) {
                    results.add(item)
                }
                continue
            }

            if (trimmed.isNotEmpty() && !results.contains(trimmed)) {
                results.add(trimmed)
            }
        }

        return results
    }
}
