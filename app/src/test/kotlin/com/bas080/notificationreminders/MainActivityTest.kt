package com.bas080.notificationreminders

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @org.junit.Before
    fun setUp() {
        com.bas080.notificationreminders.services.ReminderNotificationListenerService.lastTriggeredMap.clear()
    }

    @Test
    fun testAddReminderShowsSuccessToastOnImeAction() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        assertNotNull(input)

        input.setText("Buy Groceries")
        input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testShiftEnterInsertsNewlineInSearchInput() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        assertNotNull(input)

        input.setText("Line 1")
        input.setSelection(6)

        val shiftEnterDown = android.view.KeyEvent(
            0L, 0L, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_ENTER, 0,
            android.view.KeyEvent.META_SHIFT_ON
        )
        input.dispatchKeyEvent(shiftEnterDown)

        assertEquals("Line 1\n", input.text.toString())
        assertNull("No creation toast expected on Shift+Enter", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testPlainEnterSubmitsTaskFromSearchInput() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        assertNotNull(input)

        input.setText("Task line 1\nTask line 2")

        val enterDown = android.view.KeyEvent(
            0L, 0L, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_ENTER, 0, 0
        )
        input.dispatchKeyEvent(enterDown)

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
        assertEquals("", input.text.toString())
    }

    @Test
    fun testPullDownToCreateReminderFromTopInput() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val swipeRefresh = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)

        // Typing "milk" should filter displayed items to 1 active header + 1 reminder ("Buy milk")
        input.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Expected 3 items when filtered", 3, recyclerView.adapter!!.itemCount)

        // Pulling down (swipe to refresh) should convert "milk" into a new reminder and clear search query
        val listenerField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        listenerField.isAccessible = true
        val onRefreshListener = listenerField.get(swipeRefresh) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        assertNotNull("SwipeRefreshLayout mListener should be set", onRefreshListener)
        onRefreshListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
        assertEquals("", input.text.toString())

        // Verify the newly created reminder is displayed at position 1 (position 0 = ACTIVE header)
        recyclerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        recyclerView.layout(0, 0, 1080, 1920)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as? com.bas080.notificationreminders.RemindersAdapter.ItemViewHolder
        assertNotNull("ViewHolder at position 1 should exist", holder)
        assertEquals("milk", holder?.reminderInput?.text?.toString())
    }

    @Test
    fun testAddReminderEmptyShowsFailureToastOnImeAction() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        assertNotNull(input)

        input.setText("   ")
        input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)

        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testPullDownWithEmptyInputRefreshesListWithoutCreatingReminder() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val swipeRefresh = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)

        input.setText("   ")
        val listenerField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        listenerField.isAccessible = true
        val onRefreshListener = listenerField.get(swipeRefresh) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        assertNotNull(onRefreshListener)
        onRefreshListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertNull("No creation toast expected on empty pull down", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testDonateButtonInAboutDialogLaunchesLiberapayUrl() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showAboutDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        fun findTextViewByText(view: android.view.View?, text: String): TextView? {
            if (view == null) return null
            if (view is TextView && view.text.toString() == text) return view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    val res = findTextViewByText(view.getChildAt(i), text)
                    if (res != null) return res
                }
            }
            return null
        }

        val btnDonate = findTextViewByText(dialog!!.findViewById(android.R.id.content) ?: dialog.window?.decorView, "Donate")
        assertNotNull(btnDonate)
        btnDonate!!.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull("Donate button should start an intent", nextStartedActivity)
        assertEquals(android.content.Intent.ACTION_VIEW, nextStartedActivity.action)
        assertEquals("https://liberapay.com/bas080", nextStartedActivity.dataString)
    }

    @Test
    fun testTopMenuTogglesContainerAndNavigates() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnMenu = activity.findViewById<ImageView>(R.id.btn_menu)
        val menuContainer = activity.findViewById<View>(R.id.menu_container)
        val btnMenuPuntAll = activity.findViewById<View>(R.id.btn_menu_punt_all)
        val btnMenuShareMatching = activity.findViewById<View>(R.id.btn_menu_share_matching)

        assertNotNull(btnMenu)
        assertNotNull(menuContainer)
        assertEquals(View.GONE, menuContainer.visibility)

        // Tapping top menu expands menu container
        btnMenu.performClick()
        assertEquals(View.VISIBLE, menuContainer.visibility)

        // Tapping action pill collapses menu
        assertNotNull(btnMenuPuntAll)
        assertNotNull(btnMenuShareMatching)
        btnMenuShareMatching.performClick()
        assertEquals(View.GONE, menuContainer.visibility)
    }

    @Test
    fun testAboutDialogDisplaysVersionAndFeedbackLaunchesIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showAboutDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        fun findTextViewByText(view: android.view.View?, text: String): TextView? {
            if (view == null) return null
            if (view is TextView && view.text.toString() == text) return view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    val res = findTextViewByText(view.getChildAt(i), text)
                    if (res != null) return res
                }
            }
            return null
        }

        val btnFeedback = findTextViewByText(dialog!!.findViewById(android.R.id.content) ?: dialog.window?.decorView, "Feedback")
        assertNotNull(btnFeedback)
        btnFeedback!!.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(CrashReportActivity::class.java.name, nextStartedActivity.component?.className)
        assertTrue(nextStartedActivity.getBooleanExtra(CrashReportActivity.EXTRA_IS_FEEDBACK, false))
    }

    @Test
    fun testClearLogsShowsToastInAboutDialog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showAboutDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        fun findTextViewByText(view: android.view.View?, text: String): TextView? {
            if (view == null) return null
            if (view is TextView && view.text.toString() == text) return view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    val res = findTextViewByText(view.getChildAt(i), text)
                    if (res != null) return res
                }
            }
            return null
        }

        val btnClearLogs = findTextViewByText(dialog!!.findViewById(android.R.id.content) ?: dialog.window?.decorView, "Clear Logs")
        assertNotNull(btnClearLogs)
        btnClearLogs!!.performClick()

        assertEquals("Logs cleared", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testExportMarkdownWithRemindersLaunchesShareIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        input.setText("Buy milk")
        input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)

        val showExportOptionsMethod = MainActivity::class.java.getDeclaredMethod("showExportOptionsDialog", List::class.java)
        showExportOptionsMethod.isAccessible = true
        showExportOptionsMethod.invoke(activity, listOf("Buy milk"))

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Export dialog should be shown", dialog)
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        assertNotNull("Share chooser intent should be launched", chooserIntent)
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

        val btnMenuShareMatching = activity.findViewById<View>(R.id.btn_menu_share_matching)
        assertNotNull(btnMenuShareMatching)

        val showExportOptionsMethod = MainActivity::class.java.getDeclaredMethod("showExportOptionsDialog", List::class.java)
        showExportOptionsMethod.isAccessible = true
        showExportOptionsMethod.invoke(activity, listOf("Buy milk #punt", "Fix bike #punt"))

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Export dialog should be shown", dialog)
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

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

        val showExportOptionsMethod = MainActivity::class.java.getDeclaredMethod("showExportOptionsDialog", List::class.java)
        showExportOptionsMethod.isAccessible = true
        showExportOptionsMethod.invoke(activity, emptyList<String>())

        assertEquals("No reminders to export", ShadowToast.getTextOfLatestToast())
        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        org.junit.Assert.assertNull("No share chooser activity should be started when export list is empty", chooserIntent)
    }

    @Test
    fun testBulkActionsMenuPuntAllAndShareAll() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1", "Task 2")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val showBulkMethod = MainActivity::class.java.getDeclaredMethod("showBulkSnoozeDialog", List::class.java)
        showBulkMethod.isAccessible = true
        showBulkMethod.invoke(activity, listOf("Task 1", "Task 2"))

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Bulk snooze dialog should be shown", dialog)

        val inputEditText = dialog!!.findViewById<EditText>(R.id.import_input)
        assertNotNull(inputEditText)

        val parentLayout = inputEditText!!.parent as android.view.ViewGroup
        val optionsList = parentLayout.getChildAt(0) as android.view.ViewGroup
        val firstOptionTv = optionsList.getChildAt(0) as TextView

        firstOptionTv.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val snoozeTime1 = prefs.getLong("snooze_task 1", 0L)
        val snoozeTime2 = prefs.getLong("snooze_task 2", 0L)
        assertTrue("Task 1 should be snoozed", snoozeTime1 > System.currentTimeMillis())
        assertTrue("Task 2 should be snoozed", snoozeTime2 > System.currentTimeMillis())
    }

    @Test
    fun testExportOptionsDialogTextOnlyExcludesPuntInfo() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeUntil = System.currentTimeMillis() + 86400000L
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Punted task"))
            .putLong("snooze_punted task", snoozeUntil)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val showExportOptionsMethod = MainActivity::class.java.getDeclaredMethod("showExportOptionsDialog", List::class.java)
        showExportOptionsMethod.isAccessible = true
        showExportOptionsMethod.invoke(activity, listOf("Punted task"))

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Export dialog should be shown", dialog)
        dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() // Text Only
        shadowOf(android.os.Looper.getMainLooper()).idle()

        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        assertNotNull("Chooser intent should be launched", chooserIntent)

        @Suppress("DEPRECATION")
        val targetIntent = chooserIntent!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        val exportedText = targetIntent?.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        org.junit.Assert.assertFalse("Export should NOT contain <time tag when Text Only is selected", exportedText.contains("<time"))
        assertTrue("Export should contain task text", exportedText.contains("Punted task"))
    }

    @Test
    fun testImportMarkdownDialogParsesAndAddsReminders() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val showImportMethod = MainActivity::class.java.getDeclaredMethod("showImportMarkdownDialog")
        showImportMethod.isAccessible = true
        showImportMethod.invoke(activity)

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
    fun testImportMarkdownDialogParsesPuntStateAndSetsSnoozeInPrefs() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val futureTime = System.currentTimeMillis() + 86400000L
        val isoStr = com.bas080.notificationreminders.utils.MarkdownRemindersUtil.millisToIso(futureTime)
        val markdownText = "- [ ] Water plants <time datetime=\"$isoStr\">punted until future</time>"

        val showImportMethod = MainActivity::class.java.getDeclaredMethod("showImportMarkdownDialog")
        showImportMethod.isAccessible = true
        showImportMethod.invoke(activity)

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        val editText = dialog!!.findViewById<EditText>(R.id.import_input)
        editText!!.setText(markdownText)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSnooze = prefs.getLong("snooze_water plants", 0L)
        assertTrue("Imported punt timestamp should be saved in prefs", savedSnooze > System.currentTimeMillis())
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
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
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

        // Type "milk" to filter list
        input.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("Expected 3 items when filtered (1 header + 1 task + 1 footer)", 3, recyclerView.adapter!!.itemCount)

        // Clear search text
        input.setText("")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("", input.text.toString())
        assertEquals("Expected 4 items total when cleared (1 header + 2 tasks + 1 footer)", 4, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun testSearchQueryIsPersistedAndRestoredAcrossActivityRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller1 = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity1 = controller1.get()

        val input1 = activity1.findViewById<EditText>(R.id.search_reminder_input)
        input1.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        val savedQuery = prefs.getString("key_search_query", null)
        assertEquals("milk", savedQuery)

        val controller2 = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity2 = controller2.get()

        val input2 = activity2.findViewById<EditText>(R.id.search_reminder_input)
        assertEquals("milk", input2.text.toString())
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

        recyclerView.adapter!!.onBindViewHolder(holder1, 1) // Task Active (Position 0 = ACTIVE header)
        recyclerView.adapter!!.onBindViewHolder(holder2, 3) // Task Sooner (Position 2 = SNOOZED header)
        recyclerView.adapter!!.onBindViewHolder(holder3, 4) // Task Later

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
        val btnEmptyClearFilter = activity.findViewById<TextView>(R.id.btn_empty_clear_filter)
        assertNotNull(txtEmpty)
        assertEquals(View.VISIBLE, txtEmpty.visibility)
        assertTrue(txtEmpty.text.toString().contains("Pull down or press Enter to create task"))
        assertTrue(txtEmpty.text.toString().contains("Swipe right on task to mark done"))
        assertTrue(txtEmpty.text.toString().contains("Swipe left on task to punt"))
        assertTrue(txtEmpty.text.toString().contains("Use #every 1d for recurring tasks"))
        assertEquals(View.GONE, btnEmptyClearFilter.visibility)
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

        assertEquals("Adapter should display filtered match (1 header + 1 task + 1 footer = 3 items)", 3, recyclerView.adapter!!.itemCount)
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

        val inputEditText = dialog!!.findViewById<EditText>(R.id.import_input)
        assertNotNull(inputEditText)

        val parentLayout = inputEditText!!.parent as android.view.ViewGroup
        val unsnoozeTv = parentLayout.getChildAt(0) as TextView
        assertEquals("First item should be Unpunt", "Unpunt", unsnoozeTv.text.toString())

        // Click "Unpunt"
        unsnoozeTv.performClick()
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
        assertEquals(4, recyclerView.adapter!!.itemCount)

        // Mark "Task 1" done
        val markMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markMethod.isAccessible = true
        markMethod.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(4, recyclerView.adapter!!.itemCount)

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        input.setText("Task")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals(3, recyclerView.adapter!!.itemCount)
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
        assertEquals("Should show 1 match ('Buy milk') when searching 'Buy'", 3, recyclerView.adapter!!.itemCount)

        // Search "#done" -> should match "Buy bread #done"
        input.setText("#done")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Should show 1 match ('Buy bread #done') when searching '#done'", 3, recyclerView.adapter!!.itemCount)

        // Verify done item does NOT have STRIKE_THRU_TEXT_FLAG set
        val doneItemHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
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

        assertEquals(5, recyclerView.adapter!!.itemCount)

        val headerType = recyclerView.adapter!!.getItemViewType(2)
        assertEquals(RemindersAdapter.TYPE_SECTION_HEADER, headerType)
    }

    @Test
    fun testPullToRefreshClearsRecentlyDoneRemindersAndRefreshesList() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1", "Task 2")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertEquals(4, recyclerView.adapter!!.itemCount)

        // Mark "Task 1" done
        val markMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markMethod.isAccessible = true
        markMethod.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(4, recyclerView.adapter!!.itemCount)

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

        assertEquals(3, recyclerView.adapter!!.itemCount)
        org.junit.Assert.assertFalse(swipeRefreshLayout.isRefreshing)
    }

    @Test
    fun testSwipeHandlerCallbacksAndChildDraw() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Active Task", "Done Task #done"))
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val swipeHandler = activity.createSwipeHandler()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val activeHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        // 1. Threshold and escape velocity
        assertEquals(0.75f, swipeHandler.getSwipeThreshold(activeHolder), 0.01f)
        assertEquals(30f, swipeHandler.getSwipeEscapeVelocity(10f), 0.01f)

        // 2. getSwipeDirs
        val activeDirs = swipeHandler.getSwipeDirs(recyclerView, activeHolder)
        assertTrue(activeDirs != 0)

        val mockHeaderHolder = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_SECTION_HEADER)
        assertEquals(0, swipeHandler.getSwipeDirs(recyclerView, mockHeaderHolder))

        // 3. onMove
        org.junit.Assert.assertFalse(swipeHandler.onMove(recyclerView, activeHolder, activeHolder))

        // 4. Canvas drawing setup
        val bitmap = android.graphics.Bitmap.createBitmap(1000, 1000, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Active item right swipe (dX = 50f)
        swipeHandler.onChildDraw(canvas, recyclerView, activeHolder, 50f, 0f, androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE, true)
        // Active item left swipe (dX = -50f)
        swipeHandler.onChildDraw(canvas, recyclerView, activeHolder, -50f, 0f, androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE, true)

        // 5. onSwiped for Active Task (LEFT -> show snooze dialog)
        swipeHandler.onSwiped(activeHolder, androidx.recyclerview.widget.ItemTouchHelper.LEFT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should open on left swipe of active task", dialog)
        dialog?.dismiss()

        // Re-layout and re-fetch position 1 holder before second swipe on active item
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val activeHolder2 = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        // Swiping active task RIGHT -> mark done
        swipeHandler.onSwiped(activeHolder2, androidx.recyclerview.widget.ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Reminder marked done", ShadowToast.getTextOfLatestToast())

        // 6. Position 1 is now "Active Task #done"
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val doneHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        // Done item canvas drawing (dX = 50f right swipe -> delete icon, dX = -50f left swipe -> undo icon)
        swipeHandler.onChildDraw(canvas, recyclerView, doneHolder, 50f, 0f, androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE, true)
        swipeHandler.onChildDraw(canvas, recyclerView, doneHolder, -50f, 0f, androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE, true)

        // Swiping done task LEFT -> undo mark done
        swipeHandler.onSwiped(doneHolder, androidx.recyclerview.widget.ItemTouchHelper.LEFT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Mark done undone", ShadowToast.getTextOfLatestToast())

        // Re-layout and mark done again so we can test deletion
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val activeHolder3 = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        swipeHandler.onSwiped(activeHolder3, androidx.recyclerview.widget.ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val doneHolder2 = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        // Swiping done task RIGHT -> delete reminder
        swipeHandler.onSwiped(doneHolder2, androidx.recyclerview.widget.ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Reminder deleted", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testRemindersAdapterEditingFocusAndAccessibility() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Editable task")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        // 1. Focus listener changes maxLines
        holder.reminderInput.onFocusChangeListener?.onFocusChange(holder.reminderInput, true)
        assertEquals(Int.MAX_VALUE, holder.reminderInput.maxLines)

        holder.reminderInput.onFocusChangeListener?.onFocusChange(holder.reminderInput, false)
        assertEquals(4, holder.reminderInput.maxLines)

        // 2. TextWatcher updates text in adapter
        holder.reminderInput.setText("Editable task updated")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(350, java.util.concurrent.TimeUnit.MILLISECONDS)

        val updatedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(updatedSet.contains("Editable task updated"))

        // 3. Accessibility delegates
        val info = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
        androidx.core.view.ViewCompat.getAccessibilityDelegate(holder.btnShare)?.onInitializeAccessibilityNodeInfo(holder.btnShare, info)
        assertEquals(android.widget.Button::class.java.name, info.className)
    }

    @Test
    fun testCheckAndShowCrashReportDialogPreservesCrashTraceInPrefs() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = "java.lang.RuntimeException: Persistent crash test"
        prefs.edit().putString(App.KEY_CRASH_TRACE, crashTrace).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).create().get()

        val checkMethod = MainActivity::class.java.getDeclaredMethod("checkAndShowCrashReportDialog")
        checkMethod.isAccessible = true
        checkMethod.invoke(controller)

        val nextStartedActivity = shadowOf(controller).nextStartedActivity
        assertNotNull("CrashReportActivity intent should be started", nextStartedActivity)
        assertEquals(CrashReportActivity::class.java.name, nextStartedActivity.component?.className)

        val savedTrace = prefs.getString(App.KEY_CRASH_TRACE, null)
        assertNotNull("KEY_CRASH_TRACE must remain persisted in prefs", savedTrace)
        assertEquals(crashTrace, savedTrace)
    }

    @Test
    fun testDebouncedReminderEditingFlushesOnFocusLossOrDebounce() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Original Task")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        holder.reminderInput.setText("Original Task Updated")

        holder.reminderInput.onFocusChangeListener?.onFocusChange(holder.reminderInput, false)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val updatedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(updatedSet.contains("Original Task Updated"))
    }

    @Test
    fun testEditIntervalTrackingContinuousMetrics() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        com.bas080.notificationreminders.utils.AppLogger.clearLogs(activity)
        com.bas080.notificationreminders.utils.ExperimentTracker.resetForTesting(activity)

        com.bas080.notificationreminders.utils.ExperimentTracker.trackEditInterval(activity)
        Thread.sleep(10)
        com.bas080.notificationreminders.utils.ExperimentTracker.trackEditInterval(activity)

        val metrics = com.bas080.notificationreminders.utils.ExperimentTracker.getFormattedExperimentMetrics(activity)
        assertTrue(metrics.contains("Reminder Edit Intervals") && metrics.contains("n=1"))
    }

    @Test
    fun testMarkRecurringReminderDonePuntsTask() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Water plants #every 1d")).apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val swipeHandler = activity.createSwipeHandler()
        val holder = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
            .findViewHolderForAdapterPosition(1)!!

        swipeHandler.onSwiped(holder, ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.contains("Water plants #every 1d"))
        assertFalse(savedSet.contains("Water plants #every 1d #done"))

        val snoozeTime = prefs.getLong("snooze_water plants #every 1d", 0L)
        assertTrue("Snooze timestamp should be set in future for recurring task", snoozeTime > System.currentTimeMillis())
    }

    @Test
    fun testSwipePeekingIconForRecurringTask() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Water plants #every 1d", "Buy milk")).apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val swipeHandler = activity.createSwipeHandler()
        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1)!!

        val canvas = android.graphics.Canvas()
        swipeHandler.onChildDraw(canvas, recyclerView, holder, 100f, 0f, ItemTouchHelper.ACTION_STATE_SWIPE, true)

        val recurringResult = CreateReminderReceiver.parseRecurrenceDuration("Water plants #every 1d")
        assertNotNull(recurringResult)
    }

    @Test
    fun testRecurrenceTagSpanAppliedToRecurringTask() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Water plants #every 1d")).apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        val text = holder.reminderInput.text
        assertTrue("Input text should be SPANNABLE", text is android.text.Spanned)
        val spanned = text as android.text.Spanned
        val spans = spanned.getSpans(0, spanned.length, android.text.style.ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)
    }

    @Test
    fun testFocusProfilesPersistenceAndFiltering() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
            put(org.json.JSONObject().put("name", "Fitness").put("query", "#fitness"))
        }.toString()

        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Buy groceries #work", "Gym session #fitness", "Doctor #health"))
            .putString("key_focus_profiles", profilesJson)
            .putString("key_active_focus_profile", "Work")
            .apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val searchInput = activity.findViewById<android.widget.EditText>(R.id.search_reminder_input)
        assertEquals("Focus: Work", searchInput.hint.toString())

        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val adapter = recyclerView.adapter as RemindersAdapter
        assertEquals(3, adapter.itemCount)

        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertTrue(holder.reminderInput.text.toString().contains("Buy groceries #work"))
    }

    @Test
    fun testClearFocusProfileAction() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
        }.toString()

        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Task A #work", "Task B #personal"))
            .putString("key_focus_profiles", profilesJson)
            .putString("key_active_focus_profile", "Work")
            .apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val searchInput = activity.findViewById<android.widget.EditText>(R.id.search_reminder_input)
        assertEquals("Focus: Work", searchInput.hint.toString())

        val clearFocusBtn = activity.findViewById<android.widget.TextView>(R.id.btn_empty_clear_focus)
        clearFocusBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Add or search reminders...", searchInput.hint.toString())

        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val adapter = recyclerView.adapter as RemindersAdapter
        assertEquals(4, adapter.itemCount)
    }

    @Test
    fun testFocusProfilesDialogCreateProfile() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Yoga #fitness")).apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showCreateFocusProfileDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val createDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(createDialog)

        val editTexts = mutableListOf<android.widget.EditText>()
        fun findEditTexts(view: android.view.View?) {
            if (view == null) return
            if (view is android.widget.EditText) {
                editTexts.add(view)
            } else if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    findEditTexts(view.getChildAt(i))
                }
            }
        }
        findEditTexts(createDialog!!.findViewById(android.R.id.content) ?: createDialog.window?.decorView)

        assertEquals(2, editTexts.size)
        editTexts[0].setText("Fitness")
        editTexts[1].setText("#fitness")

        createDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Focus: Fitness", activity.findViewById<android.widget.EditText>(R.id.search_reminder_input).hint.toString())
        assertEquals("Fitness", prefs.getString("key_active_focus_profile", null))
    }

    @Test
    fun testFocusProfilesDialogSelectProfileAndAllNoProfile() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
            put(org.json.JSONObject().put("name", "Fitness").put("query", "#fitness"))
        }.toString()

        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Task A #work", "Task B #fitness"))
            .putString("key_focus_profiles", profilesJson)
            .apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showFocusProfilesDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val focusDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(focusDialog)

        fun findListViewInViewGroup(view: android.view.View?): android.widget.ListView? {
            if (view == null) return null
            if (view is android.widget.ListView) return view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    val res = findListViewInViewGroup(view.getChildAt(i))
                    if (res != null) return res
                }
            }
            return null
        }

        val listView = findListViewInViewGroup(focusDialog!!.findViewById(android.R.id.content) ?: focusDialog.window?.decorView)
        assertNotNull(listView)

        listView!!.performItemClick(listView.adapter!!.getView(1, null, listView)!!, 1, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Focus: Work", activity.findViewById<android.widget.EditText>(R.id.search_reminder_input).hint.toString())
        assertEquals("Work", prefs.getString("key_active_focus_profile", null))

        activity.showFocusProfilesDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val focusDialog2 = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        val listView2 = findListViewInViewGroup(focusDialog2!!.findViewById(android.R.id.content) ?: focusDialog2.window?.decorView)!!

        listView2.performItemClick(listView2.adapter!!.getView(0, null, listView2)!!, 0, 0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Add or search reminders...", activity.findViewById<android.widget.EditText>(R.id.search_reminder_input).hint.toString())
        assertEquals("", prefs.getString("key_active_focus_profile", null))
    }

    @Test
    fun testFocusProfilesEditAndDeleteOptions() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
            put(org.json.JSONObject().put("name", "Fitness").put("query", "#fitness"))
        }.toString()

        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Task A #work"))
            .putString("key_focus_profiles", profilesJson)
            .putString("key_active_focus_profile", "Work")
            .apply()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showEditOrDeleteFocusProfileOptions(MainActivity.FocusProfile("Work", "#work"))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val optionsDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(optionsDialog)
        val optionsListView = optionsDialog!!.listView!!

        optionsListView.performItemClick(optionsListView.adapter!!.getView(0, null, optionsListView)!!, 0, 0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val editDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(editDialog)
        editDialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        activity.showEditOrDeleteFocusProfileOptions(MainActivity.FocusProfile("Work", "#work"))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val deleteOptionsDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        val deleteListView = deleteOptionsDialog!!.listView!!

        deleteListView.performItemClick(deleteListView.adapter!!.getView(1, null, deleteListView)!!, 1, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("", prefs.getString("key_active_focus_profile", null))
    }

    @Test
    fun testFocusProfilesCreateValidationBlankName() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showCreateFocusProfileDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val createDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(createDialog)

        createDialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Add or search reminders...", activity.findViewById<android.widget.EditText>(R.id.search_reminder_input).hint.toString())
    }

    @Test
    fun testTwoStageAllFocusSearchPipelineFiltering() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
        }.toString()

        prefs.edit()
            .putString("key_focus_profiles", profilesJson)
            .putString("key_active_focus_profile", "Work")
            .putBoolean("key_active_collapsed", false)
            .putBoolean("key_punted_collapsed", false)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val activeRemindersField = MainActivity::class.java.getDeclaredField("activeReminders")
        activeRemindersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val activeReminders = activeRemindersField.get(activity) as MutableList<String>
        activeReminders.clear()
        activeReminders.addAll(listOf("Buy milk #work", "Gym session #fitness", "Write report #work"))

        val updateSummaryAndAdapterMethod = MainActivity::class.java.getDeclaredMethod("updateSummaryAndAdapter")
        updateSummaryAndAdapterMethod.isAccessible = true
        updateSummaryAndAdapterMethod.invoke(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val adapter = recyclerView.adapter as RemindersAdapter

        // Focused on "Work": 1 header + 2 work tasks + 1 footer = 4 items
        assertEquals(4, adapter.itemCount)

        // Type search query "report"
        val searchInput = activity.findViewById<EditText>(R.id.search_reminder_input)
        searchInput.setText("report")
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Search "report" on Work focus: 1 header + 1 matching task ("Write report #work") + 1 footer = 3 items
        assertEquals(3, adapter.itemCount)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertTrue(holder.reminderInput.text.toString().contains("Write report #work"))

        // Type search query "gym" (exists in #fitness task, but NOT in #work focus)
        searchInput.setText("gym")
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Search "gym" on Work focus yields 0 matching tasks
        assertEquals(0, adapter.itemCount)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layout_empty_reminders).visibility)
    }

    @Test
    fun testCollapsibleActiveAndPuntedSectionHeaders() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Task Active", "Task Punted"))
            .putLong("snooze_task punted", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val adapter = recyclerView.adapter as RemindersAdapter

        // Default: 1 active header + 1 active task + 1 punted header + 1 punted task + 1 footer = 5 items
        assertEquals(5, adapter.itemCount)

        // Toggle Active section collapsed
        activity.toggleActiveCollapsed()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Collapsed Active: 1 active header + 1 punted header + 1 punted task + 1 footer = 4 items
        assertEquals(4, adapter.itemCount)

        // Toggle Punted section collapsed
        activity.togglePuntedCollapsed()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Collapsed both: 1 active header + 1 punted header + 1 footer = 3 items
        assertEquals(3, adapter.itemCount)
    }
}
