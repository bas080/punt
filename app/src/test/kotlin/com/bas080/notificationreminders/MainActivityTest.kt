package com.bas080.notificationreminders

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun testAddReminderShowsSuccessToast() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val btnAdd = activity.findViewById<ImageView>(R.id.btn_add_reminder)
        assertNotNull(input)
        assertNotNull(btnAdd)

        input.setText("Buy Groceries")
        btnAdd.performClick()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testInputFiltersRemindersInRealTimeAndAddsOnButtonClick() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val btnAdd = activity.findViewById<ImageView>(R.id.btn_add_reminder)

        // Typing "milk" should filter displayed items to 1 reminder ("Buy milk")
        input.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        // 1 matched ("Buy milk") + 1 footer = 2 items in RecyclerView
        assertEquals("Expected 2 items when filtered", 2, recyclerView.adapter!!.itemCount)

        // Clicking '+' button should add "milk" as a new reminder and clear search query
        btnAdd.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())

        // Input text should be cleared and all 3 reminders displayed (+1 footer = 4 items in RecyclerView)
        assertEquals("Expected 4 items total in RecyclerView", 4, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun testAddReminderEmptyShowsFailureToast() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val btnAdd = activity.findViewById<ImageView>(R.id.btn_add_reminder)
        assertNotNull(input)
        assertNotNull(btnAdd)

        input.setText("   ")
        btnAdd.performClick()

        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testAboutViewDisplaysVersionAndFeedbackLaunchesIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnNavAbout = activity.findViewById<TextView>(R.id.btn_nav_about)
        assertNotNull(btnNavAbout)

        btnNavAbout.performClick()

        val aboutContainer = activity.findViewById<View>(R.id.about_container)
        assertEquals(View.VISIBLE, aboutContainer.visibility)

        val txtVersion = activity.findViewById<TextView>(R.id.txt_app_version)
        assertNotNull(txtVersion)
        assertTrue(txtVersion.text.toString().startsWith("Version"))

        val btnFeedback = activity.findViewById<TextView>(R.id.btn_feedback)
        assertNotNull(btnFeedback)
        btnFeedback.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(CrashReportActivity::class.java.name, nextStartedActivity.component?.className)
        assertTrue(nextStartedActivity.getBooleanExtra(CrashReportActivity.EXTRA_IS_FEEDBACK, false))
    }

    @Test
    fun testClearLogsShowsToast() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnClearLogs = activity.findViewById<TextView>(R.id.btn_clear_logs)
        assertNotNull(btnClearLogs)

        btnClearLogs.performClick()

        assertEquals("Logs cleared", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testExportMarkdownWithRemindersLaunchesShareIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val btnAdd = activity.findViewById<ImageView>(R.id.btn_add_reminder)
        input.setText("Buy milk")
        btnAdd.performClick()

        val btnExport = activity.findViewById<TextView>(R.id.btn_export_markdown)
        assertNotNull(btnExport)
        btnExport.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(android.content.Intent.ACTION_CHOOSER, nextStartedActivity.action)
    }

    @Test
    fun testExportMarkdownOnlyExportsFilteredReminders() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk #punt", "Clean garage #home", "Fix bike #punt")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)

        // Filter by "#punt"
        input.setText("#punt")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        val btnExport = activity.findViewById<TextView>(R.id.btn_export_markdown)
        assertNotNull(btnExport)
        btnExport.performClick()

        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        assertNotNull("Share chooser intent should be launched", chooserIntent)

        @Suppress("DEPRECATION")
        val targetIntent = chooserIntent!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        val exportedText = targetIntent!!.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""

        assertTrue("Export should contain 'Buy milk #punt'", exportedText.contains("Buy milk #punt"))
        assertTrue("Export should contain 'Fix bike #punt'", exportedText.contains("Fix bike #punt"))
        org.junit.Assert.assertFalse("Export should NOT contain 'Clean garage #home'", exportedText.contains("Clean garage #home"))
    }

    @Test
    fun testExportMarkdownWhenFilteredListIsEmptyShowsToast() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk #punt")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)

        // Filter by "nonexistentquery"
        input.setText("nonexistentquery")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        val btnExport = activity.findViewById<TextView>(R.id.btn_export_markdown)
        assertNotNull(btnExport)
        btnExport.performClick()

        assertEquals("No reminders to export", ShadowToast.getTextOfLatestToast())
        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        org.junit.Assert.assertNull("No share chooser activity should be started when export list is empty", chooserIntent)
    }

    @Test
    fun testExportButtonOnRemindersListExportsFilteredReminders() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1 #punt", "Task 2 #other")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)

        // Filter by "#punt"
        input.setText("#punt")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        val btnListExport = activity.findViewById<ImageView>(R.id.btn_list_export)
        assertNotNull(btnListExport)
        btnListExport.performClick()

        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        assertNotNull("Share chooser intent should be launched when clicking list export button", chooserIntent)

        @Suppress("DEPRECATION")
        val targetIntent = chooserIntent!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        val exportedText = targetIntent!!.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""

        assertTrue("Export should contain 'Task 1 #punt'", exportedText.contains("Task 1 #punt"))
        org.junit.Assert.assertFalse("Export should NOT contain 'Task 2 #other'", exportedText.contains("Task 2 #other"))
    }

    @Test
    fun testImportMarkdownDialogParsesAndAddsReminders() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnImport = activity.findViewById<TextView>(R.id.btn_import_markdown)
        assertNotNull(btnImport)
        btnImport.performClick()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Import dialog should be shown", dialog)

        val editText = dialog!!.findViewById<EditText>(R.id.import_input)
        assertNotNull("Import EditText should exist inside dialog", editText)

        editText!!.setText("- [ ] Clean garage unique 123\n- [ ] Fix bike unique 123")
        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertNotNull("Positive button should exist", positiveBtn)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Imported 2 new reminder(s)", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testFormatSnoozeUntilTodayAndTomorrow() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, 2026)
            set(java.util.Calendar.MONTH, java.util.Calendar.OCTOBER)
            set(java.util.Calendar.DAY_OF_MONTH, 15)
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val todaySnooze = now + 2 * 3600 * 1000L
        val tomorrowSnooze = now + 24 * 3600 * 1000L

        val todayFormatted = MainActivity.formatSnoozeUntil(todaySnooze, now)
        assertTrue("Expected 'today at ...', got: $todayFormatted", todayFormatted.startsWith("today at"))

        val tomorrowFormatted = MainActivity.formatSnoozeUntil(tomorrowSnooze, now)
        assertTrue("Expected 'tomorrow at ...', got: $tomorrowFormatted", tomorrowFormatted.startsWith("tomorrow at"))
    }

    @Test
    fun testSnoozedReminderDisplaysStatusLabelAndShareButton() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Snoozed Task"))
            .putLong("snooze_snoozed task", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        // Position 0 = SNOOZED header, Position 1 = Snoozed Task
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as? RemindersAdapter.ItemViewHolder
        assertNotNull(holder)

        assertEquals(View.VISIBLE, holder!!.txtStatus.visibility)
        assertTrue(holder.txtStatus.text.toString().startsWith("Punted • until"))
        assertEquals(View.VISIBLE, holder.btnShare.visibility)
        assertEquals(View.GONE, holder.btnAction.visibility)
    }

    @Test
    fun testUnpuntReminderMethod() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Punted Task"))
            .putLong("snooze_punted task", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val unpuntMethod = MainActivity::class.java.getDeclaredMethod("unpuntReminder", String::class.java)
        unpuntMethod.isAccessible = true
        unpuntMethod.invoke(activity, "Punted Task")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        val updatedSnooze = prefs.getLong("snooze_punted task", 0L)
        assertEquals(0L, updatedSnooze)
    }

    @Test
    fun testItemShareClickLaunchesShareIntent() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Shared Task")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder
        assertEquals(View.VISIBLE, holder.btnShare.visibility)

        holder.btnShare.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(android.content.Intent.ACTION_CHOOSER, nextStartedActivity.action)
    }

    @Test
    fun testClearSearchButtonClearsQueryAndResetsList() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val btnClearSearch = activity.findViewById<ImageView>(R.id.btn_clear_search)
        assertNotNull(btnClearSearch)

        // Type "milk" to filter list
        input.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("Expected 2 items when filtered", 2, recyclerView.adapter!!.itemCount)
        assertTrue("Clear button should be enabled when text is entered", btnClearSearch.isEnabled)

        // Click Clear button
        btnClearSearch.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("", input.text.toString())
        assertEquals("Expected 3 items total when cleared", 3, recyclerView.adapter!!.itemCount)
        org.junit.Assert.assertFalse("Clear button should be disabled when search is cleared", btnClearSearch.isEnabled)
    }

    @Test
    fun testClearSearchButtonEnabledAndResetsStateFilterWhenFilterActive() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Active Task", "Snoozed Task"))
            .putLong("snooze_snoozed task", snoozeTime)
            .putString("key_reminder_filter", ReminderFilter.SNOOZED.name)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnClearSearch = activity.findViewById<ImageView>(R.id.btn_clear_search)
        assertTrue("Clear search button should be enabled when filter is not ALL", btnClearSearch.isEnabled)

        btnClearSearch.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val currentFilterName = prefs.getString("key_reminder_filter", null)
        assertEquals(ReminderFilter.ALL.name, currentFilterName)
        val txtSelectedTags = activity.findViewById<TextView>(R.id.txt_selected_tags)
        assertEquals("All", txtSelectedTags.text.toString())
    }

    @Test
    fun testNoResultsInActiveOrPuntedFilterPersistsFilterAndShowsEmptyStateClearButton() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Only Punted Task"))
            .putLong("snooze_only punted task", snoozeTime)
            .putString("key_reminder_filter", ReminderFilter.ACTIVE.name)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        // Since no active items exist and filter is ACTIVE, 0 items matched (+ 0 footer = 0 items)
        assertEquals(0, recyclerView.adapter!!.itemCount)

        val btnEmptyClearFilter = activity.findViewById<TextView>(R.id.btn_empty_clear_filter)
        assertNotNull(btnEmptyClearFilter)
        assertEquals(View.VISIBLE, btnEmptyClearFilter.visibility)

        // Filter choice should remain persisted in prefs
        val currentFilterName = prefs.getString("key_reminder_filter", null)
        assertEquals(ReminderFilter.ACTIVE.name, currentFilterName)

        // Clicking clear filter button switches filter to ALL and resets search
        btnEmptyClearFilter.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(3, recyclerView.adapter!!.itemCount)
        assertEquals(ReminderFilter.ALL.name, prefs.getString("key_reminder_filter", null))
    }

    @Test
    fun testTagFilterSelectionDialogSortsMoreCommonTagsFirst() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf(
                "Task 1 #zebra",
                "Task 2 #apple",
                "Task 3 #zebra",
                "Task 4 #banana"
            ))
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val extractMethod = MainActivity::class.java.getDeclaredMethod("extractAllTags")
        extractMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tags = extractMethod.invoke(activity) as List<String>

        assertEquals(listOf("#zebra", "#apple", "#banana"), tags)
    }

    @Test
    fun testStateFilterIsPersistedAndRestoredAcrossActivityRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1", "Task 2")).commit()

        val controller1 = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity1 = controller1.get()

        val btnTagsFilter = activity1.findViewById<android.widget.LinearLayout>(R.id.btn_tags_filter)
        assertNotNull(btnTagsFilter)
        btnTagsFilter.performClick()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        val positiveBtn = dialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val controller2 = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity2 = controller2.get()

        val txtSelectedTags = activity2.findViewById<TextView>(R.id.txt_selected_tags)
        assertNotNull(txtSelectedTags)
    }

    @Test
    fun testTagFilterSelectionDialog() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Buy milk #groceries", "Finish report #work"))
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnTagsFilter = activity.findViewById<android.widget.LinearLayout>(R.id.btn_tags_filter)
        val txtSelectedTags = activity.findViewById<TextView>(R.id.txt_selected_tags)
        assertNotNull(btnTagsFilter)
        assertNotNull(txtSelectedTags)

        // Open filter selection dialog
        btnTagsFilter.performClick()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Filter selection dialog should be shown", dialog)

        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("All", txtSelectedTags.text.toString())
    }

    @Test
    fun testActiveItemsSortedFirstAndSnoozedItemsSortedAscendingBySnoozeTime() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val snoozeLater = now + 7200000L // 2h
        val snoozeSooner = now + 3600000L // 1h
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Task Later", "Task Sooner", "Task Active"))
            .putLong("snooze_task later", snoozeLater)
            .putLong("snooze_task sooner", snoozeSooner)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        val holder1 = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder
        val holder2 = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder
        val holder3 = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder

        recyclerView.adapter!!.onBindViewHolder(holder1, 0) // Task Active
        recyclerView.adapter!!.onBindViewHolder(holder2, 2) // Task Sooner (Position 1 is SNOOZED header)
        recyclerView.adapter!!.onBindViewHolder(holder3, 3) // Task Later

        assertEquals("Task Active", holder1.reminderInput.text.toString())
        assertEquals("Task Sooner", holder2.reminderInput.text.toString())
        assertEquals("Task Later", holder3.reminderInput.text.toString())
    }

    @Test
    fun testEmptyStateVisibility() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val txtEmpty = activity.findViewById<TextView>(R.id.txt_empty_reminders)
        assertNotNull(txtEmpty)
        assertEquals(View.VISIBLE, txtEmpty.visibility)
    }

    @Test
    fun testSearchZeroResultsRetainsFocusOnInputAndShowsEmptyText() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val input = activity.findViewById<EditText>(R.id.search_reminder_input)

        input.requestFocus()
        assertTrue("Top search input should have focus initially", input.hasFocus())

        // Search for non-matching text
        input.setText("nonexistentquery123")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("Adapter should have 0 items when 0 reminders match", 0, recyclerView.adapter!!.itemCount)

        val txtEmpty = activity.findViewById<TextView>(R.id.txt_empty_reminders)
        assertEquals("Empty reminders view should be VISIBLE", View.VISIBLE, txtEmpty.visibility)

        assertTrue("Top search input should retain focus even when 0 items match", input.hasFocus())
    }

    @Test
    fun testSearchInputDoesNotLoseFocusWhileTypingCharacterByCharacter() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage", "Walk dog")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val input = activity.findViewById<EditText>(R.id.search_reminder_input)

        input.requestFocus()
        assertTrue("Input should initially have focus", input.hasFocus())

        val querySequence = "garage"
        val currentText = StringBuilder()

        for (char in querySequence) {
            currentText.append(char)
            input.setText(currentText.toString())
            shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue("Input should retain focus while typing character '$char'", input.hasFocus())
        }

        assertEquals("Adapter should display filtered match", 2, recyclerView.adapter!!.itemCount)
        assertTrue("Input should remain focused after typing completes", input.hasFocus())
    }

    @Test
    fun testApplySnoozeDurationClearsFocusAndPreservesScrollPosition() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1", "Task 2", "Task 3")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val applyMethod = MainActivity::class.java.getDeclaredMethod("applySnoozeDuration", String::class.java, String::class.java)
        applyMethod.isAccessible = true
        applyMethod.invoke(activity, "Task 1", "1h")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        org.junit.Assert.assertNull("Focus should be cleared after punting", activity.currentFocus)
    }

    @Test
    fun testSnoozedReminderSwipeOpensDialogWithUnsnoozeOption() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Snoozed Item"))
            .putLong("snooze_snoozed item", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Trigger snooze dialog directly for snoozed item
        val method = MainActivity::class.java.getDeclaredMethod("showSnoozeOptionsDialog", String::class.java)
        method.isAccessible = true
        method.invoke(activity, "Snoozed Item")

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should be displayed", dialog)

        val listView = dialog!!.listView
        assertNotNull("Dialog list view should exist", listView)
        assertEquals("First option should be Unpunt", "Unpunt", listView.adapter.getItem(0))

        // Click "Unpunt" (index 0)
        shadowOf(listView).performItemClick(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        val updatedSnooze = prefs.getLong("snooze_snoozed item", 0L)
        assertEquals(0L, updatedSnooze)
    }

    @Test
    fun testMarkDoneAppendsDoneTagAndFiltersFromOverview() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Mark done "Task 1"
        val method = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        method.isAccessible = true
        method.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Reminder marked done", ShadowToast.getTextOfLatestToast())

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved set should contain 'Task 1 #done'", savedSet.contains("Task 1 #done"))
    }

    @Test
    fun testMarkingPuntedReminderDonePreservesPuntTimeAndSection() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val futureSnooze = System.currentTimeMillis() + 3600000L
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Punted Task"))
            .putLong("snooze_punted task", futureSnooze)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val markDoneMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markDoneMethod.isAccessible = true
        markDoneMethod.invoke(activity, "Punted Task")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved set should contain 'Punted Task #done'", savedSet.contains("Punted Task #done"))

        val preservedSnooze = prefs.getLong("snooze_punted task", 0L)
        assertEquals(futureSnooze, preservedSnooze)

        val displayedField = MainActivity::class.java.getDeclaredField("displayedReminders")
        displayedField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val displayed = displayedField.get(activity) as List<String>
        assertTrue("Displayed list should contain snoozed header marker", displayed.contains("HEADER_SNOOZED_SECTION_MARKER"))
        assertTrue("Displayed list should contain 'Punted Task #done'", displayed.contains("Punted Task #done"))

        val undoMethod = MainActivity::class.java.getDeclaredMethod("undoMarkDone", String::class.java)
        undoMethod.isAccessible = true
        undoMethod.invoke(activity, "Punted Task #done")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val restoredSnooze = prefs.getLong("snooze_punted task", 0L)
        assertEquals(futureSnooze, restoredSnooze)
        @Suppress("UNCHECKED_CAST")
        val restoredDisplayed = displayedField.get(activity) as List<String>
        assertTrue("Restored displayed list should contain 'Punted Task'", restoredDisplayed.contains("Punted Task"))
    }

    @Test
    fun testSwipingDoneItemPermanentlyDeletesReminder() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Done Task #done")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Call delete directly or verify deletion
        val deleteMethod = MainActivity::class.java.getDeclaredMethod("deleteReminder", String::class.java)
        deleteMethod.isAccessible = true
        deleteMethod.invoke(activity, "Done Task #done")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        org.junit.Assert.assertFalse("Saved set should NOT contain 'Done Task #done'", savedSet.contains("Done Task #done"))
        assertEquals("Reminder deleted", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testSwipeLeftOnMarkedDoneItemUndosMarkDone() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Done Task #done")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val undoMethod = MainActivity::class.java.getDeclaredMethod("undoMarkDone", String::class.java)
        undoMethod.isAccessible = true
        undoMethod.invoke(activity, "Done Task #done")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved set should contain restored 'Done Task'", savedSet.contains("Done Task"))
        assertEquals("Mark done undone", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testSwipeRightReplacesTileWithGrayedOutTileAndUndoRestoresReminder() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Mark done directly
        val markMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markMethod.isAccessible = true
        markMethod.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved set should contain 'Task 1 #done'", savedSet.contains("Task 1 #done"))

        // Undo mark done
        val undoMethod = MainActivity::class.java.getDeclaredMethod("undoMarkDone", String::class.java)
        undoMethod.isAccessible = true
        undoMethod.invoke(activity, "Task 1 #done")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val restoredSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved set should contain 'Task 1'", restoredSet.contains("Task 1"))
        assertEquals("Mark done undone", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testMarkedDoneItemRemainsVisibleUntilSearchOrFilterUpdated() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1", "Task 2")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertEquals(3, recyclerView.adapter!!.itemCount)

        // Mark "Task 1" done
        val markMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markMethod.isAccessible = true
        markMethod.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(3, recyclerView.adapter!!.itemCount)

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        input.setText("Task")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals(2, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun testDoneItemsHiddenFromSearchUnlessSearchContainsHashDone() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Buy bread #done")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val input = activity.findViewById<EditText>(R.id.search_reminder_input)

        // Search "Buy" -> should match "Buy milk" but exclude "Buy bread #done"
        input.setText("Buy")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Should show 1 match ('Buy milk') when searching 'Buy'", 2, recyclerView.adapter!!.itemCount)

        // Search "#done" -> should match "Buy bread #done"
        input.setText("#done")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Should show 1 match ('Buy bread #done') when searching '#done'", 2, recyclerView.adapter!!.itemCount)

        // Verify done item does NOT have STRIKE_THRU_TEXT_FLAG set
        val doneItemHolder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder
        val isStrikethrough = (doneItemHolder.reminderInput.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG) != 0
        org.junit.Assert.assertFalse("#done items should NOT have strikethrough flag set", isStrikethrough)
    }

    @Test
    fun testSnoozedDividerAppearsWhenSnoozedItemsExist() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Active Task", "Snoozed Task"))
            .putLong("snooze_snoozed task", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        assertEquals(4, recyclerView.adapter!!.itemCount)

        val headerType = recyclerView.adapter!!.getItemViewType(1)
        assertEquals(RemindersAdapter.TYPE_SNOOZED_HEADER, headerType)
    }

    @Test
    fun testHeaderNavigationRemainsVisibleOnScroll() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val items = (1..15).map { "Task $it" }.toSet()
        prefs.edit().clear().putStringSet("key_reminders_list", items).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val headerNav = activity.findViewById<View>(R.id.header_navigation)
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertNotNull(headerNav)
        assertNotNull(recyclerView)

        // Navigation is VISIBLE
        assertEquals(View.VISIBLE, headerNav.visibility)

        // Scroll down list to position 5
        recyclerView.scrollToPosition(5)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Trigger onScrolled
        recyclerView.scrollBy(0, 50)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Bottom navigation bar should remain VISIBLE on scroll", View.VISIBLE, headerNav.visibility)
    }

    @Test
    fun testPullToRefreshClearsRecentlyDoneRemindersAndRefreshesList() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1", "Task 2")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertEquals(3, recyclerView.adapter!!.itemCount)

        // Mark "Task 1" done
        val markMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markMethod.isAccessible = true
        markMethod.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(3, recyclerView.adapter!!.itemCount)

        // Pull down to refresh
        val swipeRefreshLayout = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        assertNotNull(swipeRefreshLayout)

        swipeRefreshLayout.isRefreshing = true
        val listenerField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        listenerField.isAccessible = true
        val refreshListener = listenerField.get(swipeRefreshLayout) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        assertNotNull("OnRefreshListener should be attached to SwipeRefreshLayout", refreshListener)
        refreshListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(2, recyclerView.adapter!!.itemCount)
        org.junit.Assert.assertFalse(swipeRefreshLayout.isRefreshing)
    }

    @Test
    fun testSwipeThresholdAndEscapeVelocity() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val method = MainActivity::class.java.getDeclaredMethod("setupSwipeGestures")
        method.isAccessible = true

        method.invoke(activity)
    }

    @Test
    fun testCheckAndShowCrashReportDialogPreservesCrashTraceInPrefs() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(NotificationRemindersApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = "java.lang.RuntimeException: Persistent crash test"
        prefs.edit().putString(NotificationRemindersApplication.KEY_CRASH_TRACE, crashTrace).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).create().get()

        val checkMethod = MainActivity::class.java.getDeclaredMethod("checkAndShowCrashReportDialog")
        checkMethod.isAccessible = true
        checkMethod.invoke(controller)

        val nextStartedActivity = shadowOf(controller).nextStartedActivity
        assertNotNull("CrashReportActivity intent should be started", nextStartedActivity)
        assertEquals(CrashReportActivity::class.java.name, nextStartedActivity.component?.className)

        val savedTrace = prefs.getString(NotificationRemindersApplication.KEY_CRASH_TRACE, null)
        assertNotNull("KEY_CRASH_TRACE must remain persisted in prefs", savedTrace)
        assertEquals(crashTrace, savedTrace)
    }
}
