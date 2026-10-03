package com.bas080.notificationreminders.utils

object MarkdownRemindersUtil {

    private val CHECKLIST_REGEX = Regex("^\\s*[-*+]\\s*\\[[ xX]?\\]\\s*(.+)$")
    private val BULLET_REGEX = Regex("^\\s*[-*+]\\s+(.+)$")
    private val NUMBER_REGEX = Regex("^\\s*\\d+\\.\\s+(.+)$")

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

        for (line in lines) {
            val item = parseLine(line)
            if (!item.isNullOrBlank() && !results.contains(item)) {
                results.add(item)
            }
        }

        return results
    }

    private fun parseLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val match = CHECKLIST_REGEX.find(line)
            ?: BULLET_REGEX.find(line)
            ?: NUMBER_REGEX.find(line)

        return match?.groupValues?.getOrNull(1)?.trim() ?: trimmed
    }
}
