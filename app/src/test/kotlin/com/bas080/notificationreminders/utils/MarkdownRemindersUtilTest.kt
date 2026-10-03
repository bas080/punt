package com.bas080.notificationreminders.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRemindersUtilTest {

    @Test
    fun testExportToMarkdownFormatsChecklist() {
        val reminders = listOf("Buy milk", "Call mom", "Doctor appointment")
        val markdown = MarkdownRemindersUtil.exportToMarkdown(reminders)

        val expected = "- [ ] Buy milk\n- [ ] Call mom\n- [ ] Doctor appointment"
        assertEquals(expected, markdown)
    }

    @Test
    fun testExportToMarkdownEmptyListReturnsEmptyString() {
        val markdown = MarkdownRemindersUtil.exportToMarkdown(emptyList<String>())
        assertEquals("", markdown)
    }

    @Test
    fun testImportFromMarkdownParsesChecklistAndLists() {
        val markdownInput = """
            # Shopping List
            - [ ] Buy apples
            - [x] Buy bread
            * Fresh oranges
            1. Call electrician
            Plain reminder item
        """.trimIndent()

        val imported = MarkdownRemindersUtil.importFromMarkdown(markdownInput)

        assertEquals(5, imported.size)
        assertEquals("Buy apples", imported[0].text)
        assertFalse(imported[0].isDone)

        assertEquals("Buy bread #done", imported[1].text)
        assertTrue(imported[1].isDone)

        assertEquals("Fresh oranges", imported[2].text)
        assertFalse(imported[2].isDone)

        assertEquals("Call electrician", imported[3].text)
        assertFalse(imported[3].isDone)

        assertEquals("Plain reminder item", imported[4].text)
        assertFalse(imported[4].isDone)
    }

    @Test
    fun testImportFromMarkdownBlankInputReturnsEmptyList() {
        val imported = MarkdownRemindersUtil.importFromMarkdown("   \n   ")
        assertTrue(imported.isEmpty())
    }

    @Test
    fun testMultiLineReminderExportAndImport() {
        val multiLineText = "Buy groceries\nMilk 2%\nFresh bread"
        val exportable = listOf(ExportableReminder(multiLineText))

        val exported = MarkdownRemindersUtil.exportToMarkdown(exportable, includePuntInfo = false)
        val expected = "- [ ] Buy groceries\n  Milk 2%\n  Fresh bread"
        assertEquals(expected, exported)

        val imported = MarkdownRemindersUtil.importFromMarkdown(exported)
        assertEquals(1, imported.size)
        assertEquals(multiLineText, imported[0].text)
        assertFalse(imported[0].isDone)
        assertNull(imported[0].snoozeUntilMillis)
    }

    @Test
    fun testMultiLineWithTabIndentationImport() {
        val input = "- [ ] Task title\n\tsubtask line 1\n\tsubtask line 2"
        val imported = MarkdownRemindersUtil.importFromMarkdown(input)
        assertEquals(1, imported.size)
        assertEquals("Task title\nsubtask line 1\nsubtask line 2", imported[0].text)
    }

    @Test
    fun testPuntStateExportAndImport() {
        val snoozeUntil = 1779213600000L // ISO: 2026-05-19T18:00:00Z
        val exportable = listOf(
            ExportableReminder("Water plants\nfront & back", snoozeUntil)
        )

        val exported = MarkdownRemindersUtil.exportToMarkdown(exportable, includePuntInfo = true)
        assertTrue("Exported should contain <time datetime>: $exported", exported.contains("<time datetime=\"2026-05-19T18:00:00Z\">"))
        assertTrue("Exported should contain 'punted until': $exported", exported.contains("punted until"))

        val imported = MarkdownRemindersUtil.importFromMarkdown(exported)
        assertEquals(1, imported.size)
        assertEquals("Water plants\nfront & back", imported[0].text)
        assertNotNull("Imported snoozeUntilMillis should not be null", imported[0].snoozeUntilMillis)
        assertEquals("Snooze timestamp mismatch", snoozeUntil, imported[0].snoozeUntilMillis)
    }

    @Test
    fun testPuntStateExportWithMap() {
        val snoozeUntil = 1779213600000L
        val reminders = listOf("Buy milk", "Water plants")
        val snoozeMap = mapOf("water plants" to snoozeUntil)

        val exported = MarkdownRemindersUtil.exportToMarkdown(reminders, snoozeMap, includePuntInfo = true)
        assertTrue(exported.contains("- [ ] Buy milk"))
        assertTrue(exported.contains("<time datetime=\"2026-05-19T18:00:00Z\">"))
    }

    @Test
    fun testSelfClosingTimeTagImport() {
        val markdown = "- [ ] Water plants <time datetime=\"1779213600000\" />"
        val imported = MarkdownRemindersUtil.importFromMarkdown(markdown)
        assertEquals(1, imported.size)
        assertEquals("Water plants", imported[0].text)
        assertEquals(1779213600000L, imported[0].snoozeUntilMillis)
    }

    @Test
    fun testPuntStateExportDisabled() {
        val snoozeUntil = 1779213600000L
        val exportable = listOf(
            ExportableReminder("Water plants", snoozeUntil)
        )

        val exported = MarkdownRemindersUtil.exportToMarkdown(exportable, includePuntInfo = false)
        assertFalse(exported.contains("<time"))
        assertEquals("- [ ] Water plants", exported)
    }

    @Test
    fun testDoneStatusPreservedWithDoneTag() {
        val reminders = listOf("Finished task #done")

        val exported = MarkdownRemindersUtil.exportToMarkdown(reminders)
        assertEquals("- [x] Finished task #done", exported)

        val imported = MarkdownRemindersUtil.importFromMarkdown(exported)
        assertEquals(1, imported.size)
        assertEquals("Finished task #done", imported[0].text)
        assertTrue(imported[0].isDone)
    }

    @Test
    fun testImportFromMarkdownStringsHelper() {
        val markdown = "- [ ] Task A\n- [x] Task B"
        val strings = MarkdownRemindersUtil.importFromMarkdownStrings(markdown)
        assertEquals(listOf("Task A", "Task B #done"), strings)
    }

    @Test
    fun testUppercaseCheckedCheckbox() {
        val markdown = "- [X] Task completed"
        val imported = MarkdownRemindersUtil.importFromMarkdown(markdown)
        assertEquals(1, imported.size)
        assertEquals("Task completed #done", imported[0].text)
        assertTrue(imported[0].isDone)
    }

    @Test
    fun testParseIsoToMillisFallbackAndInvalid() {
        assertNull(MarkdownRemindersUtil.parseIsoToMillis("invalid-date-string"))
        assertEquals(1000L, MarkdownRemindersUtil.parseIsoToMillis("1000"))
    }
}
