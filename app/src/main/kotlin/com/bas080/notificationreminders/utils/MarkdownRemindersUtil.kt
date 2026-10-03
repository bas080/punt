package com.bas080.notificationreminders.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ExportableReminder(
    val text: String,
    val snoozeUntilMillis: Long? = null
)

data class ImportedReminder(
    val text: String,
    val isDone: Boolean = false,
    val snoozeUntilMillis: Long? = null
)

object MarkdownRemindersUtil {

    private val CHECKLIST_REGEX = Regex("""^\s*[-*+]?\s*\[[ xX]?\]\s*(.+)$""")
    private val BULLET_REGEX = Regex("""^\s*[-*+]\s+(.+)$""")
    private val NUMBER_REGEX = Regex("""^\s*\d+\.\s+(.+)$""")
    private val CHECKLIST_CHECKED_REGEX = Regex("""^\s*[-*+]?\s*\[[xX]\]""")

    private val TIME_TAG_REGEX = Regex(
        """<time\s+datetime="([^"]+)"[^>]*>(.*?)</time>""",
        RegexOption.IGNORE_CASE
    )
    private val TIME_TAG_SELF_CLOSING_REGEX = Regex(
        """<time\s+datetime="([^"]+)"\s*/?>""",
        RegexOption.IGNORE_CASE
    )

    fun exportToMarkdown(
        reminders: List<String>,
        snoozeMap: Map<String, Long> = emptyMap(),
        includePuntInfo: Boolean = false,
        getCleanTrimmed: (String) -> String = {
            it.replace(Regex("(?i)\\s*#done\\b"), "").trim().lowercase()
        }
    ): String {
        val exportableList = reminders.map { reminder ->
            val cleanKey = getCleanTrimmed(reminder)
            val snoozeUntil = snoozeMap[cleanKey]
            ExportableReminder(text = reminder, snoozeUntilMillis = snoozeUntil)
        }
        return exportToMarkdown(exportableList, includePuntInfo = includePuntInfo)
    }

    fun exportToMarkdown(
        reminders: List<ExportableReminder>,
        includePuntInfo: Boolean
    ): String {
        if (reminders.isEmpty()) return ""
        return reminders.joinToString("\n") { reminder ->
            val lines = reminder.text.lines()
            val firstLine = lines.firstOrNull() ?: ""
            val remainingLines = lines.drop(1)

            val isDone = reminder.text.contains("#done", ignoreCase = true)
            val prefix = if (isDone) "- [x] " else "- [ ] "

            val snoozeMillis = reminder.snoozeUntilMillis
            val timeTag = if (includePuntInfo && snoozeMillis != null && snoozeMillis > 0L) {
                val isoStr = millisToIso(snoozeMillis)
                val displayStr = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.US).format(Date(snoozeMillis))
                " <time datetime=\"$isoStr\">punted until $displayStr</time>"
            } else {
                ""
            }

            val formattedFirstLine = "$prefix$firstLine$timeTag"
            val formattedRemaining = remainingLines.map { "  $it" }

            (listOf(formattedFirstLine) + formattedRemaining).joinToString("\n")
        }
    }

    fun importFromMarkdownStrings(markdownContent: String): List<String> {
        return importFromMarkdown(markdownContent).map { it.text }
    }

    fun importFromMarkdown(markdownContent: String): List<ImportedReminder> {
        if (markdownContent.isBlank()) return emptyList()

        val rawItems = parseRawItems(markdownContent.lines())
        val results = mutableListOf<ImportedReminder>()

        for (rawItem in rawItems) {
            val processed = processRawItem(rawItem)
            if (processed != null && results.none { it.text == processed.text }) {
                results.add(processed)
            }
        }

        return results
    }

    private fun parseRawItems(lines: List<String>): List<RawItem> {
        val rawItems = mutableListOf<RawItem>()
        var currentItem: RawItem? = null

        for (line in lines) {
            currentItem = processSingleLine(line, currentItem, rawItems)
        }

        if (currentItem != null) {
            rawItems.add(currentItem)
        }

        return rawItems
    }

    private fun processSingleLine(
        line: String,
        currentItem: RawItem?,
        rawItems: MutableList<RawItem>
    ): RawItem? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            if (currentItem != null) rawItems.add(currentItem)
            return null
        }

        val listMatch = CHECKLIST_REGEX.find(line)
            ?: BULLET_REGEX.find(line)
            ?: NUMBER_REGEX.find(line)

        return if (listMatch != null) {
            if (currentItem != null) rawItems.add(currentItem)
            val isChecked = CHECKLIST_CHECKED_REGEX.containsMatchIn(line)
            val content = listMatch.groupValues.getOrNull(1)?.trim() ?: ""
            RawItem(lines = mutableListOf(content), isChecked = isChecked)
        } else {
            val isIndented = line.startsWith("  ") || line.startsWith("\t")
            if (isIndented && currentItem != null) {
                val unindentedLine = if (line.startsWith("  ")) line.substring(2) else line.removePrefix("\t")
                currentItem.lines.add(unindentedLine)
                currentItem
            } else {
                if (currentItem != null) rawItems.add(currentItem)
                RawItem(lines = mutableListOf(trimmed), isChecked = false)
            }
        }
    }

    private fun processRawItem(rawItem: RawItem): ImportedReminder? {
        val fullContent = rawItem.lines.joinToString("\n")
        val (cleanText, extractedSnooze) = extractPuntTag(fullContent)

        if (cleanText.isBlank()) return null

        val hasDoneTag = cleanText.contains("#done", ignoreCase = true)
        val isDone = rawItem.isChecked || hasDoneTag

        val finalText = if (isDone && !hasDoneTag) {
            "$cleanText #done"
        } else {
            cleanText
        }

        return ImportedReminder(
            text = finalText,
            isDone = isDone,
            snoozeUntilMillis = extractedSnooze
        )
    }

    private fun extractPuntTag(content: String): Pair<String, Long?> {
        val match = TIME_TAG_REGEX.find(content) ?: TIME_TAG_SELF_CLOSING_REGEX.find(content)
        if (match == null) {
            return content to null
        }

        val isoStr = match.groupValues[1]
        val snoozeMillis = parseIsoToMillis(isoStr)

        val cleanContent = content.replace(match.value, "")
        val cleanLines = cleanContent.lines().map { it.trim() }
        val normalized = cleanLines.joinToString("\n").trim()

        return normalized to snoozeMillis
    }

    fun millisToIso(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    fun parseIsoToMillis(datetimeStr: String): Long? {
        val numeric = datetimeStr.toLongOrNull()
        if (numeric != null) return numeric

        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        var result: Long? = null
        for (pattern in patterns) {
            if (result != null) break
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(datetimeStr)
                if (date != null) result = date.time
            } catch (_: Exception) {
                // Ignore
            }
        }
        return result
    }

    private class RawItem(
        val lines: MutableList<String> = mutableListOf(),
        val isChecked: Boolean = false
    )
}
