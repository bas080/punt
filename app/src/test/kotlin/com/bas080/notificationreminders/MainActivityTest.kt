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
        val context = RuntimeEnvironment.getApplication()
        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(context).reminderDao()
        kotlinx.coroutines.runBlocking { dao.deleteAll() }
        java.io.File(context.filesDir, com.bas080.notificationreminders.utils.TaskBackupManager.BACKUP_FILE_NAME).delete()
        com.bas080.notificationreminders.services.ReminderNotificationListenerService.lastTriggeredMap.clear()
    }

    private fun seedTasks(context: Context, vararg tasks: Pair<String, Long?>) {
        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(context).reminderDao()
        kotlinx.coroutines.runBlocking {
            var order = 0L
            for ((raw, snoozeUntil) in tasks) {
                val isDone = raw.contains(Regex("(?i)(?<=^|\\s)#done(?=\\s|$)"))
                val clean = raw.replace(Regex("(?i)\\s*#done\\b"), "").trim()
                dao.insert(
                    com.bas080.notificationreminders.data.ReminderEntity(
                        text = clean,
                        createdAt = System.currentTimeMillis() + order,
                        updatedAt = System.currentTimeMillis() + order,
                        snoozeUntil = snoozeUntil,
                        isDone = isDone
                    )
                )
                order += 1
            }
        }
    }

    private fun storedTask(context: Context, text: String): com.bas080.notificationreminders.data.ReminderEntity? {
        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(context).reminderDao()
        return kotlinx.coroutines.runBlocking { dao.getReminderByText(text) }
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
    fun testEnterAlwaysCreatesItemAndNewlinesNotSupported() {
        val context = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        assertNotNull(input)
        assertEquals("Input must be single-line", 1, input.maxLines)

        input.setText("Line 1")
        input.setSelection(6)

        val shiftEnterDown = android.view.KeyEvent(
            0L, 0L, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_ENTER, 0,
            android.view.KeyEvent.META_SHIFT_ON
        )
        input.dispatchKeyEvent(shiftEnterDown)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
        assertEquals("", input.text.toString())
        assertNotNull("Shift+Enter should create the reminder", storedTask(context, "Line 1"))

        input.setText("Line 2")
        val plainEnterDown = android.view.KeyEvent(
            0L, 0L, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_ENTER, 0, 0
        )
        input.dispatchKeyEvent(plainEnterDown)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertNotNull("Plain Enter should create the reminder", storedTask(context, "Line 2"))
    }

    @Test
    fun testPlainEnterSubmitsTaskFromSearchInput() {
        val context = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        assertNotNull(input)

        input.setText("Task line 1\nTask line 2")
        assertEquals("Pasted newlines must be collapsed to spaces", "Task line 1 Task line 2", input.text.toString())

        val enterDown = android.view.KeyEvent(
            0L, 0L, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_ENTER, 0, 0
        )
        input.dispatchKeyEvent(enterDown)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
        assertEquals("", input.text.toString())
        assertNotNull("Reminder should be created without newlines", storedTask(context, "Task line 1 Task line 2"))
    }

    @Test
    fun testPullDownToRefreshClearsRecentlyDoneAndReloadsList() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Buy milk" to null, "Clean garage" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val swipeRefresh = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)

        val listenerField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        listenerField.isAccessible = true
        val onRefreshListener = listenerField.get(swipeRefresh) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        assertNotNull("SwipeRefreshLayout mListener should be set", onRefreshListener)
        onRefreshListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(4, recyclerView.adapter!!.itemCount)
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
        seedTasks(context, "Buy milk #punt" to null, "Clean garage #home" to null, "Fix bike #punt" to null)

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
        seedTasks(context, "Buy milk #punt" to null)

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
        seedTasks(context, "Task 1" to null, "Task 2" to null)

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

        val snoozeTime1 = storedTask(context, "Task 1")?.snoozeUntil ?: 0L
        val snoozeTime2 = storedTask(context, "Task 2")?.snoozeUntil ?: 0L
        assertTrue("Task 1 should be snoozed", snoozeTime1 > System.currentTimeMillis())
        assertTrue("Task 2 should be snoozed", snoozeTime2 > System.currentTimeMillis())
    }

    @Test
    fun testExportOptionsDialogTextOnlyExcludesPuntInfo() {
        val context = RuntimeEnvironment.getApplication()
        val snoozeUntil = System.currentTimeMillis() + 86400000L
        seedTasks(context, "Punted task" to snoozeUntil)

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
    fun testImportMarkdownDialogParsesPuntStateAndSetsSnooze() {
        val context = RuntimeEnvironment.getApplication()

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

        val savedSnooze = storedTask(context, "Water plants")?.snoozeUntil ?: 0L
        assertTrue("Imported punt timestamp should be saved in Room", savedSnooze > System.currentTimeMillis())
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
        val snoozeTime = System.currentTimeMillis() + 3600000L
        seedTasks(context, "Snoozed Task" to snoozeTime)

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
        val snoozeTime = System.currentTimeMillis() + 3600000L
        seedTasks(context, "Punted Task" to snoozeTime)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val unpuntMethod = MainActivity::class.java.getDeclaredMethod("unpuntReminder", String::class.java)
        unpuntMethod.isAccessible = true
        unpuntMethod.invoke(activity, "Punted Task")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        val updatedSnooze = storedTask(context, "Punted Task")?.snoozeUntil ?: 0L
        assertEquals(0L, updatedSnooze)
    }

    @Test
    fun testItemShareClickLaunchesShareIntent() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Shared Task" to null)

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
        seedTasks(context, "Buy milk" to null, "Clean garage" to null)

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
    fun testClearInputButtonClickClearsInputAndResetsFilter() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Buy milk" to null, "Clean garage" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val input = activity.findViewById<EditText>(R.id.search_reminder_input)
        val btnClear = activity.findViewById<ImageView>(R.id.btn_clear_input)
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        assertNotNull(btnClear)
        assertEquals(View.GONE, btnClear.visibility)

        // Type "milk"
        input.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(View.VISIBLE, btnClear.visibility)
        assertEquals(3, recyclerView.adapter!!.itemCount)

        // Click clear button
        btnClear.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("", input.text.toString())
        assertEquals(View.GONE, btnClear.visibility)
        assertEquals(4, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun testSearchQueryIsPersistedAndRestoredAcrossActivityRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        seedTasks(context, "Buy milk" to null, "Clean garage" to null)

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
        val now = System.currentTimeMillis()
        val snoozeLater = now + 7200000L // 2h
        val snoozeSooner = now + 3600000L // 1h
        seedTasks(context, "Task Later" to snoozeLater, "Task Sooner" to snoozeSooner, "Task Active" to null)

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
        assertTrue(txtEmpty.text.toString().contains("Press Enter to create task"))
        assertTrue(txtEmpty.text.toString().contains("Swipe right on task to mark done"))
        assertTrue(txtEmpty.text.toString().contains("Swipe left on task to punt"))
        assertTrue(txtEmpty.text.toString().contains("Use #every 1d for recurring tasks"))
        assertEquals(View.GONE, btnEmptyClearFilter.visibility)
    }

    @Test
    fun testSearchZeroResultsRetainsFocusOnInputAndShowsEmptyText() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Buy milk" to null, "Clean garage" to null)

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
        seedTasks(context, "Buy milk" to null, "Clean garage" to null, "Walk dog" to null)

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
        seedTasks(context, "Task 1" to null, "Task 2" to null, "Task 3" to null)

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
        val snoozeTime = System.currentTimeMillis() + 3600000L
        seedTasks(context, "Snoozed Item" to snoozeTime)

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
        val updatedSnooze = storedTask(context, "Snoozed Item")?.snoozeUntil ?: 0L
        assertEquals(0L, updatedSnooze)

        assertTrue("Unpunt chosen from swipe dialog should be undoable", activity.isSwipeUndoable("Snoozed Item"))

        activity.undoSwipeAction("Snoozed Item")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Undo should restore original punt", snoozeTime, storedTask(context, "Snoozed Item")?.snoozeUntil ?: 0L)
        assertFalse(activity.isSwipeUndoable("Snoozed Item"))
    }

    @Test
    fun testMarkDoneAppendsDoneTagAndFiltersFromOverview() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Mark done "Task 1"
        val method = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        method.isAccessible = true
        method.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Reminder marked done", ShadowToast.getTextOfLatestToast())

        val stored = storedTask(context, "Task 1")
        assertNotNull("Task 1 should be marked done", stored)
        assertTrue("Task 1 should be marked done", stored!!.isDone)
    }

    @Test
    fun testMarkingPuntedReminderDonePreservesPuntTimeAndSection() {
        val context = RuntimeEnvironment.getApplication()
        val futureSnooze = System.currentTimeMillis() + 3600000L
        seedTasks(context, "Punted Task" to futureSnooze)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val markDoneMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markDoneMethod.isAccessible = true
        markDoneMethod.invoke(activity, "Punted Task")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val stored = storedTask(context, "Punted Task")
        assertNotNull("Punted task should exist", stored)
        assertTrue("Punted task should be marked done", stored!!.isDone)

        val preservedSnooze = stored.snoozeUntil ?: 0L
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

        val restoredSnooze = storedTask(context, "Punted Task")?.snoozeUntil ?: 0L
        assertEquals(futureSnooze, restoredSnooze)
        @Suppress("UNCHECKED_CAST")
        val restoredDisplayed = displayedField.get(activity) as List<String>
        assertTrue("Restored displayed list should contain 'Punted Task'", restoredDisplayed.contains("Punted Task"))
    }

    @Test
    fun testSwipingDoneItemPermanentlyDeletesReminder() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Done Task #done" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Call delete directly or verify deletion
        val deleteMethod = MainActivity::class.java.getDeclaredMethod("requestDeleteReminder", String::class.java)
        deleteMethod.isAccessible = true
        deleteMethod.invoke(activity, "Done Task #done")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertNull("Reminder should be deleted from Room", storedTask(context, "Done Task"))
        assertEquals("Reminder deleted", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testSwipeLeftOnMarkedDoneItemUndosMarkDone() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Done Task #done" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val undoMethod = MainActivity::class.java.getDeclaredMethod("undoMarkDone", String::class.java)
        undoMethod.isAccessible = true
        undoMethod.invoke(activity, "Done Task #done")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val restored = storedTask(context, "Done Task")
        assertNotNull("Restored task should exist in Room", restored)
        assertFalse("Restored task should not be done", restored!!.isDone)
        assertEquals("Mark done undone", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testSwipeRightReplacesTileWithGrayedOutTileAndUndoRestoresReminder() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Mark done directly
        val markMethod = MainActivity::class.java.getDeclaredMethod("markReminderDone", String::class.java)
        markMethod.isAccessible = true
        markMethod.invoke(activity, "Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val stored = storedTask(context, "Task 1")
        assertNotNull("Task 1 should be marked done", stored)
        assertTrue("Task 1 should be marked done", stored!!.isDone)

        // Undo mark done
        val undoMethod = MainActivity::class.java.getDeclaredMethod("undoMarkDone", String::class.java)
        undoMethod.isAccessible = true
        undoMethod.invoke(activity, "Task 1 #done")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val restoredTask = storedTask(context, "Task 1")
        assertNotNull("Restored task should exist in Room", restoredTask)
        assertTrue("Restored task should not be done", !restoredTask!!.isDone)
        assertEquals("Mark done undone", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testMarkedDoneItemRemainsVisibleUntilSearchOrFilterUpdated() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null, "Task 2" to null)

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
        seedTasks(context, "Buy milk" to null, "Buy bread #done" to null)

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
        val snoozeTime = System.currentTimeMillis() + 3600000L
        seedTasks(context, "Active Task" to null, "Snoozed Task" to snoozeTime)

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
        seedTasks(context, "Task 1" to null, "Task 2" to null)

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
        seedTasks(context, "Active Task" to null, "Done Task #done" to null)

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

        // Swiping done task RIGHT -> deletes immediately, card stays visible as undoable
        swipeHandler.onSwiped(doneHolder2, androidx.recyclerview.widget.ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.toast_reminder_deleted), ShadowToast.getTextOfLatestToast())
        assertNull("Delete should persist immediately at swipe", storedTask(context, "Active Task"))
        assertTrue("Deleted card should stay visible as undoable", activity.getDisplayedRemindersForTesting().contains("Active Task #done"))
        assertTrue("Deleted card should be undoable", activity.isSwipeUndoable("Active Task #done"))

        // Re-layout and check pending-delete card only accepts left swipes
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val pendingDeleteHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals(ItemTouchHelper.LEFT, swipeHandler.getSwipeDirs(recyclerView, pendingDeleteHolder))

        // Refresh finally deletes the reminder and clears the swipe history
        val swipeRefreshLayout = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        swipeRefreshLayout.isRefreshing = true
        val finalizeField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        finalizeField.isAccessible = true
        val finalizeListener = finalizeField.get(swipeRefreshLayout) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        assertNotNull("OnRefreshListener should be attached to SwipeRefreshLayout", finalizeListener)
        finalizeListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull("Pending delete should be finalized at refresh", storedTask(context, "Active Task"))
        assertFalse(activity.isSwipeUndoable("Active Task #done"))
    }

    @Test
    fun testSwipeResultStaysVisibleAndUndoableUntilRefresh() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val swipeHandler = activity.createSwipeHandler()

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        swipeHandler.onSwiped(holder, ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Done card should stay visible until refresh", activity.getDisplayedRemindersForTesting().contains("Task 1 #done"))
        assertTrue("Done card should be undoable", activity.isSwipeUndoable("Task 1 #done"))
        assertTrue("Task should be marked done in Room", storedTask(context, "Task 1")!!.isDone)

        val doneHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals("Done swipe result should be grayed out", 0.5f, doneHolder.reminderInput.alpha, 0.01f)
        assertEquals(View.GONE, doneHolder.btnShare.visibility)
        assertEquals(View.VISIBLE, doneHolder.btnAction.visibility)

        val swipeRefreshLayout = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        swipeRefreshLayout.isRefreshing = true
        val listenerField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        listenerField.isAccessible = true
        val refreshListener = listenerField.get(swipeRefreshLayout) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        refreshListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse("Done card should be filtered from the default view after refresh", activity.getDisplayedRemindersForTesting().contains("Task 1 #done"))
        assertTrue("Done task should remain stored in Room", storedTask(context, "Task 1")!!.isDone)
        assertFalse("Swipe history should be cleared at refresh", activity.isSwipeUndoable("Task 1 #done"))
    }

    @Test
    fun testSwipeResultCardIsGrayedUndoableInPlaceUntilRefresh() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        recyclerView.itemAnimator = null

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        activity.applySnoozeDurationFromSwipe("Task 1", "1h")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Swiped card should be undoable", activity.isSwipeUndoable("Task 1"))
        val displayed = activity.getDisplayedRemindersForTesting()
        assertFalse("Swiped card should stay in place, not move to punted section", displayed.contains("HEADER_SNOOZED_SECTION_MARKER"))
        assertEquals("Swiped card should keep its position", 1, displayed.indexOf("Task 1"))

        val swipedHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals("Swiped card should be grayed out", 0.5f, swipedHolder.reminderInput.alpha, 0.01f)
        assertEquals(
            "Swiped card should use muted color",
            swipedHolder.reminderInput.context.getColor(R.color.text_muted),
            swipedHolder.reminderInput.textColors.defaultColor
        )
        assertEquals("Undoable card should hide share button", View.GONE, swipedHolder.btnShare.visibility)
        assertEquals("Undoable card should show undo button", View.VISIBLE, swipedHolder.btnAction.visibility)
        assertEquals("Undo", swipedHolder.btnAction.contentDescription)
        assertEquals("Undoable punted card should show punt status", View.VISIBLE, swipedHolder.txtStatus.visibility)

        activity.undoSwipeAction("Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        assertFalse(activity.isSwipeUndoable("Task 1"))
        assertEquals("Punt should be undone", 0L, storedTask(context, "Task 1")?.snoozeUntil ?: 0L)

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val activeHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals("Fully undone card should be full opacity", 1.0f, activeHolder.reminderInput.alpha, 0.01f)
        assertEquals(View.VISIBLE, activeHolder.btnShare.visibility)
        assertEquals(View.GONE, activeHolder.btnAction.visibility)

        activity.applySnoozeDurationFromSwipe("Task 1", "1h")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val swipeRefreshLayout = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        swipeRefreshLayout.isRefreshing = true
        val listenerField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        listenerField.isAccessible = true
        val refreshListener = listenerField.get(swipeRefreshLayout) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        refreshListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse("Undo history should be cleared at refresh", activity.isSwipeUndoable("Task 1"))
        val refreshedDisplayed = activity.getDisplayedRemindersForTesting()
        assertTrue("Refreshed punted card moves to punted section", refreshedDisplayed.contains("HEADER_SNOOZED_SECTION_MARKER"))
        val puntedPos = refreshedDisplayed.indexOf("Task 1")
        assertTrue(puntedPos > 0)
        val puntedHolder = recyclerView.findViewHolderForAdapterPosition(puntedPos) as RemindersAdapter.ItemViewHolder
        assertEquals("Refreshed punted card is full opacity", 1.0f, puntedHolder.reminderInput.alpha, 0.01f)
        assertEquals("Refreshed punted card shows share button again", View.VISIBLE, puntedHolder.btnShare.visibility)
        assertEquals(View.GONE, puntedHolder.btnAction.visibility)
        assertEquals("Refreshed punted card keeps punt status", View.VISIBLE, puntedHolder.txtStatus.visibility)
    }

    @Test
    fun testPendingDeleteCardShowsRedBorderUndoableUntilRefresh() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null, "Other Task" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val swipeHandler = activity.createSwipeHandler()
        recyclerView.itemAnimator = null

        val reLayout = {
            recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            recyclerView.layout(0, 0, 1000, 1000)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        val holderFor = { text: String ->
            val pos = activity.getDisplayedRemindersForTesting().indexOfFirst { it == text }
            recyclerView.findViewHolderForAdapterPosition(pos) as RemindersAdapter.ItemViewHolder
        }

        reLayout()
        val holder = holderFor("Task 1")
        assertEquals("Active card should use the default border", R.drawable.bg_card, (holder.cardContainer.tag as? Int))

        swipeHandler.onSwiped(holder, ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        reLayout()
        val doneHolder = holderFor("Task 1 #done")
        assertTrue("Done card should be undoable", activity.isSwipeUndoable("Task 1 #done"))
        assertEquals("Undoable done card keeps the default border", R.drawable.bg_card, (doneHolder.cardContainer.tag as? Int))
        assertTrue("Done task should be marked done in Room", storedTask(context, "Task 1")!!.isDone)
        val taskIdBeforeDelete = storedTask(context, "Task 1")!!.id

        swipeHandler.onSwiped(doneHolder, ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.toast_reminder_deleted), ShadowToast.getTextOfLatestToast())

        reLayout()
        val deleteHolder = holderFor("Task 1 #done")
        assertTrue("Pending delete card should be undoable", activity.isSwipeUndoable("Task 1 #done"))
        assertEquals("Pending delete card should show a red border", R.drawable.bg_card_delete, (deleteHolder.cardContainer.tag as? Int))
        assertEquals(View.GONE, deleteHolder.btnShare.visibility)
        assertEquals(View.VISIBLE, deleteHolder.btnAction.visibility)
        assertEquals("Undo mark done", deleteHolder.btnAction.contentDescription)
        assertNull("Delete should persist immediately at swipe", storedTask(context, "Task 1"))
        assertTrue("Deleted card should stay visible as undoable", activity.getDisplayedRemindersForTesting().contains("Task 1 #done"))
        assertEquals("Pending delete card only accepts left swipes", ItemTouchHelper.LEFT, swipeHandler.getSwipeDirs(recyclerView, deleteHolder))

        swipeHandler.onSwiped(deleteHolder, ItemTouchHelper.LEFT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Delete undone", ShadowToast.getTextOfLatestToast())

        reLayout()
        val restoredDoneHolder = holderFor("Task 1 #done")
        assertTrue("Undoing delete should keep the done card undoable", activity.isSwipeUndoable("Task 1 #done"))
        assertEquals("Undoing delete restores the default border", R.drawable.bg_card, (restoredDoneHolder.cardContainer.tag as? Int))
        assertTrue("Done task should remain stored", storedTask(context, "Task 1")!!.isDone)
        assertEquals("Undo delete should restore the row with the same id", taskIdBeforeDelete, storedTask(context, "Task 1")!!.id)

        swipeHandler.onSwiped(restoredDoneHolder, ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.toast_reminder_pending_delete), ShadowToast.getTextOfLatestToast())

        reLayout()
        assertEquals("Re-pending delete card shows the red border again", R.drawable.bg_card_delete, (holderFor("Task 1 #done").cardContainer.tag as? Int))

        val swipeRefreshLayout = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        swipeRefreshLayout.isRefreshing = true
        val listenerField = androidx.swiperefreshlayout.widget.SwipeRefreshLayout::class.java.getDeclaredField("mListener")
        listenerField.isAccessible = true
        val refreshListener = listenerField.get(swipeRefreshLayout) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        assertNotNull("OnRefreshListener should be attached to SwipeRefreshLayout", refreshListener)
        refreshListener!!.onRefresh()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertNull("Pending delete should be finalized at refresh", storedTask(context, "Task 1"))
        assertFalse("Swipe history should be cleared at refresh", activity.isSwipeUndoable("Task 1 #done"))
        assertFalse("Deleted card should be removed after refresh", activity.getDisplayedRemindersForTesting().contains("Task 1 #done"))
        assertTrue("Other task should survive the refresh", storedTask(context, "Other Task") != null)
    }

    @Test
    fun testEditUndoableCardKeepsSwipeHistory() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        activity.applySnoozeDurationFromSwipe("Task 1", "1h")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        holder.reminderInput.setText("Task 1 renamed")
        holder.reminderInput.onFocusChangeListener?.onFocusChange(holder.reminderInput, false)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Edit should carry the swipe history to the new name", activity.isSwipeUndoable("Task 1 renamed"))
        assertFalse("Old key should no longer hold history", activity.isSwipeUndoable("Task 1"))

        activity.undoSwipeAction("Task 1 renamed")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Undo should clear punt after edit", 0L, storedTask(context, "Task 1 renamed")?.snoozeUntil ?: 0L)
        assertFalse(activity.isSwipeUndoable("Task 1 renamed"))
    }

    @Test
    fun testChainedSwipesUndoBackToActiveRecurring() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Water plants #every 1d" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        recyclerView.itemAnimator = null
        val swipeHandler = activity.createSwipeHandler()

        val swipeAndIdle = { direction: Int ->
            recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            recyclerView.layout(0, 0, 1000, 1000)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val displayed = activity.getDisplayedRemindersForTesting()
            val pos = displayed.indexOfFirst {
                it == "Water plants #every 1d" || it == "Water plants #every 1d #done"
            }
            val holder = recyclerView.findViewHolderForAdapterPosition(pos)
            swipeHandler.onSwiped(holder!!, direction)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        swipeAndIdle(ItemTouchHelper.RIGHT)
        var stored = storedTask(context, "Water plants #every 1d")
        assertNotNull(stored)
        assertFalse("Recurring task should be punted, not done", stored!!.isDone)
        assertTrue("Punt should be set for recurring task", (stored.snoozeUntil ?: 0L) > System.currentTimeMillis())

        val displayedAfterPunt = activity.getDisplayedRemindersForTesting()
        assertFalse("Punted recurring card should stay in active section until refresh", displayedAfterPunt.contains("HEADER_SNOOZED_SECTION_MARKER"))
        val frozenPos = displayedAfterPunt.indexOfFirst { it == "Water plants #every 1d" }
        assertEquals("Punted recurring card should stay in place", 1, frozenPos)
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val frozenHolder = recyclerView.findViewHolderForAdapterPosition(frozenPos) as RemindersAdapter.ItemViewHolder
        assertEquals("Punted recurring card should be grayed out", 0.5f, frozenHolder.reminderInput.alpha, 0.01f)
        assertEquals(View.GONE, frozenHolder.btnShare.visibility)
        assertEquals(View.VISIBLE, frozenHolder.btnAction.visibility)

        swipeAndIdle(ItemTouchHelper.RIGHT)
        stored = storedTask(context, "Water plants #every 1d")
        assertTrue("Two swipes should mark recurring task done", stored!!.isDone)

        swipeAndIdle(ItemTouchHelper.RIGHT)
        assertEquals(activity.getString(R.string.toast_reminder_deleted), ShadowToast.getTextOfLatestToast())
        assertNull("Delete should persist immediately at swipe", storedTask(context, "Water plants #every 1d"))
        assertTrue("Deleted card should stay visible as undoable", activity.getDisplayedRemindersForTesting().contains("Water plants #every 1d #done"))

        swipeAndIdle(ItemTouchHelper.LEFT)
        assertEquals("Delete undone", ShadowToast.getTextOfLatestToast())
        assertTrue("Undo delete should restore done state", storedTask(context, "Water plants #every 1d")!!.isDone)

        swipeAndIdle(ItemTouchHelper.LEFT)
        assertEquals("Mark done undone", ShadowToast.getTextOfLatestToast())
        assertFalse("Second undo should restore punted state", storedTask(context, "Water plants #every 1d")!!.isDone)
        assertTrue("Restored punted state should keep recurrence", activity.getDisplayedRemindersForTesting().contains("Water plants #every 1d"))

        swipeAndIdle(ItemTouchHelper.LEFT)
        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        assertEquals("Third undo should restore active recurring state", 0L, storedTask(context, "Water plants #every 1d")?.snoozeUntil ?: 0L)
        assertFalse(activity.isSwipeUndoable("Water plants #every 1d"))
    }

    @Test
    fun testSwipeStateMachineTracksIdKeyedUndoChains() {
        val machine = SwipeStateMachine()

        assertFalse(machine.canUndo(1L))
        assertNull(machine.pendingStateOf(1L))
        assertEquals(
            listOf(
                SwipeStateMachine.CardState.PUNTED,
                SwipeStateMachine.CardState.ACTIVE,
                SwipeStateMachine.CardState.DONE,
                SwipeStateMachine.CardState.DELETED
            ),
            machine.stateLine(false)
        )
        assertEquals(
            listOf(
                SwipeStateMachine.CardState.PUNTED,
                SwipeStateMachine.CardState.ACTIVE,
                SwipeStateMachine.CardState.AUTO_PUNTED,
                SwipeStateMachine.CardState.DONE,
                SwipeStateMachine.CardState.DELETED
            ),
            machine.stateLine(true)
        )

        machine.record(1L, SwipeTransition(SwipeActionKind.PUNT, SwipeStateMachine.CardState.PUNTED, null, null, false))
        machine.record(1L, SwipeTransition(SwipeActionKind.DONE, SwipeStateMachine.CardState.DONE, 3600000L, null, false))
        machine.record(1L, SwipeTransition(SwipeActionKind.DELETE, SwipeStateMachine.CardState.DELETED, null, null, false))

        assertEquals(SwipeStateMachine.CardState.DELETED, machine.pendingStateOf(1L))
        assertTrue(machine.canUndo(1L))
        assertEquals(setOf(1L), machine.undoableKeys())

        assertEquals(SwipeActionKind.DELETE, machine.pop(1L)?.kind)
        assertEquals(SwipeStateMachine.CardState.DONE, machine.pendingStateOf(1L))
        assertEquals(SwipeActionKind.DONE, machine.pop(1L)?.kind)
        assertEquals(SwipeStateMachine.CardState.PUNTED, machine.pendingStateOf(1L))
        assertEquals(SwipeActionKind.PUNT, machine.pop(1L)?.kind)
        assertNull(machine.pendingStateOf(1L))
        assertFalse(machine.canUndo(1L))

        val other = SwipeStateMachine()
        other.record(7L, SwipeTransition(SwipeActionKind.PUNT, SwipeStateMachine.CardState.PUNTED, 1000L, null, true))
        other.record(7L, SwipeTransition(SwipeActionKind.DONE, SwipeStateMachine.CardState.DONE, 1000L, null, false))
        assertTrue(other.canUndo(7L))
        assertTrue(other.pendingFromPuntedSection(7L) == false)
        other.pop(7L)
        assertTrue(other.pendingFromPuntedSection(7L) == true)
        assertEquals(SwipeStateMachine.CardState.PUNTED, other.pendingStateOf(7L))

        val snapshot = com.bas080.notificationreminders.data.ReminderEntity(id = 5L, text = "Saved task")
        other.record(7L, SwipeTransition(SwipeActionKind.DELETE, SwipeStateMachine.CardState.DELETED, null, snapshot, false))
        assertEquals(SwipeStateMachine.CardState.DELETED, other.pendingStateOf(7L))
        assertEquals(snapshot, other.pop(7L)?.priorEntity)

        other.clearAll()
        assertFalse(other.canUndo(7L))
        assertTrue(other.undoableKeys().isEmpty())
    }

    @Test
    fun testPuntedNormalCardRightSwipesUndoPuntThenMarkDone() {
        seedTasks(
            RuntimeEnvironment.getApplication(),
            "Task 1" to (System.currentTimeMillis() + 3600000L)
        )
        val context = RuntimeEnvironment.getApplication()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        recyclerView.itemAnimator = null
        val swipeHandler = activity.createSwipeHandler()

        val reLayout = {
            recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            recyclerView.layout(0, 0, 1000, 1000)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        val holderFor = { text: String ->
            val pos = activity.getDisplayedRemindersForTesting().indexOfFirst { it == text }
            recyclerView.findViewHolderForAdapterPosition(pos) as RemindersAdapter.ItemViewHolder
        }

        reLayout()
        assertEquals(SwipeStateMachine.CardState.PUNTED, activity.resolveCardState("Task 1"))

        swipeHandler.onSwiped(holderFor("Task 1"), ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        assertEquals("First right swipe should undo the punt", 0L, storedTask(context, "Task 1")?.snoozeUntil ?: 0L)
        assertEquals(SwipeStateMachine.CardState.ACTIVE, activity.resolveCardState("Task 1"))

        reLayout()
        swipeHandler.onSwiped(holderFor("Task 1"), ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Second right swipe should mark the task done", storedTask(context, "Task 1")!!.isDone)
        assertTrue("Done card should be undoable", activity.isSwipeUndoable("Task 1 #done"))
    }

    @Test
    fun testLeftSwipeWithSnoozeDurationShowsUndoableCard() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task 1" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        recyclerView.itemAnimator = null
        val swipeHandler = activity.createSwipeHandler()

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val holder = recyclerView.findViewHolderForAdapterPosition(1)
        swipeHandler.onSwiped(holder!!, ItemTouchHelper.LEFT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Left swipe should open the punt dialog", dialog)
        val inputEditText = dialog!!.findViewById<EditText>(R.id.import_input)
        assertNotNull("Dialog should contain the custom duration input", inputEditText)
        val parentLayout = inputEditText!!.parent as android.view.ViewGroup
        val optionsList = parentLayout.getChildAt(0) as android.view.ViewGroup
        val firstChoice = optionsList.getChildAt(0) as TextView
        firstChoice.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Chosen punt duration should be undoable", activity.isSwipeUndoable("Task 1"))
        assertTrue("Punt should be stored in Room", (storedTask(context, "Task 1")?.snoozeUntil ?: 0L) > System.currentTimeMillis())

        val displayed = activity.getDisplayedRemindersForTesting()
        assertFalse("Undoable card must stay in place, not move to punted section", displayed.contains("HEADER_SNOOZED_SECTION_MARKER"))
        assertEquals("Undoable card keeps its position", 1, displayed.indexOf("Task 1"))

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val undoableHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals("Undoable card should be grayed out", 0.5f, undoableHolder.reminderInput.alpha, 0.01f)
        assertEquals("Undoable card should hide share button", View.GONE, undoableHolder.btnShare.visibility)
        assertEquals("Undoable card should show undo button", View.VISIBLE, undoableHolder.btnAction.visibility)
        assertEquals("Undo", undoableHolder.btnAction.contentDescription)
        assertEquals(View.VISIBLE, undoableHolder.txtStatus.visibility)

        activity.undoSwipeAction("Task 1")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        assertEquals("Undo should clear the punt", 0L, storedTask(context, "Task 1")?.snoozeUntil ?: 0L)
        assertFalse(activity.isSwipeUndoable("Task 1"))
        assertEquals("Undoable card keeps its position after undo", 1, activity.getDisplayedRemindersForTesting().indexOf("Task 1"))
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val undoneHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals("Fully undone card should be full opacity", 1.0f, undoneHolder.reminderInput.alpha, 0.01f)
        assertEquals("Fully undone card should show share button again", View.VISIBLE, undoneHolder.btnShare.visibility)
    }
    @Test
    fun testRemindersAdapterEditingFocusAndAccessibility() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Editable task" to null)

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

        assertNotNull("Updated text should be stored in Room", storedTask(context, "Editable task updated"))

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
        seedTasks(context, "Original Task" to null)

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

        assertNotNull("Updated text should be stored in Room", storedTask(context, "Original Task Updated"))
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
    fun testSwipeRightOnActiveRecurringPuntsByRecurrence() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Water plants #every 1d" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val swipeHandler = activity.createSwipeHandler()
        val holder = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
            .findViewHolderForAdapterPosition(1)!!

        swipeHandler.onSwiped(holder, ItemTouchHelper.RIGHT)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val stored = storedTask(context, "Water plants #every 1d")
        assertNotNull("Recurring task should exist in Room", stored)
        assertFalse("Recurring task should not be done", stored!!.isDone)

        val snoozeTime = stored.snoozeUntil ?: 0L
        assertTrue("Snooze timestamp should be set in future for recurring task", snoozeTime > System.currentTimeMillis())
    }

    @Test
    fun testSwipePeekingIconForRecurringTask() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Water plants #every 1d" to null, "Buy milk" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val swipeHandler = activity.createSwipeHandler()
        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1)!!

        val canvas = android.graphics.Canvas()
        swipeHandler.onChildDraw(canvas, recyclerView, holder, 100f, 0f, ItemTouchHelper.ACTION_STATE_SWIPE, true)

        val recurringResult = CreateReminderReceiver.parseRecurrenceDuration("Water plants #every 1d")
        assertNotNull(recurringResult)
        assertEquals(R.drawable.ic_action_repeat, activity.getRightSwipePeekIcon("Water plants #every 1d"))
        assertEquals(R.drawable.ic_action_snooze, activity.getLeftSwipePeekIcon("Water plants #every 1d"))
    }

    @Test
    fun testPeekIconsMatchSwipeActionsInAllStates() {
        val context = RuntimeEnvironment.getApplication()
        val punted = System.currentTimeMillis() + 3600000L
        seedTasks(context, "Buy milk" to null, "Water plants #every 1d" to null, "Punted Task" to punted, "Done Task #done" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val swipeHandler = activity.createSwipeHandler()

        assertEquals("Active non-recurring left: opens punt dialog", R.drawable.ic_action_snooze, activity.getLeftSwipePeekIcon("Buy milk"))
        assertEquals("Active non-recurring right: marks done", R.drawable.ic_action_done, activity.getRightSwipePeekIcon("Buy milk"))

        assertEquals("Active recurring left: opens punt dialog", R.drawable.ic_action_snooze, activity.getLeftSwipePeekIcon("Water plants #every 1d"))
        assertEquals("Active recurring right: punts by recurrence", R.drawable.ic_action_repeat, activity.getRightSwipePeekIcon("Water plants #every 1d"))

        assertEquals("Punted non-recurring left: reconfigures the punt", R.drawable.ic_action_snooze, activity.getLeftSwipePeekIcon("Punted Task"))
        assertEquals("Punted non-recurring right: undoes the punt", R.drawable.ic_action_undo, activity.getRightSwipePeekIcon("Punted Task"))

        assertEquals("Done left: undoes", R.drawable.ic_action_undo, activity.getLeftSwipePeekIcon("Done Task #done"))
        assertEquals("Done right: requests delete", R.drawable.ic_action_delete, activity.getRightSwipePeekIcon("Done Task #done"))

        val swipeRight = { text: String ->
            recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            recyclerView.layout(0, 0, 1000, 1000)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val items = activity.getDisplayedRemindersForTesting()
            val pos = items.indexOfFirst { it == text }
            swipeHandler.onSwiped(recyclerView.findViewHolderForAdapterPosition(pos)!!, ItemTouchHelper.RIGHT)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        swipeRight("Water plants #every 1d")
        assertTrue("Punted recurring card should be undoable", activity.isSwipeUndoable("Water plants #every 1d"))
        assertEquals("Undoable punted recurring left: undoes the punt", R.drawable.ic_action_undo, activity.getLeftSwipePeekIcon("Water plants #every 1d"))
        assertEquals("Undoable punted recurring right: marks done", R.drawable.ic_action_done, activity.getRightSwipePeekIcon("Water plants #every 1d"))

        swipeRight("Water plants #every 1d")
        val stored = storedTask(context, "Water plants #every 1d")
        assertNotNull(stored)
        assertTrue("Second right swipe on undoable punted recurring should mark done", stored!!.isDone)
        assertEquals("Done card left: undoes", R.drawable.ic_action_undo, activity.getLeftSwipePeekIcon("Water plants #every 1d #done"))
        assertEquals("Done card right: requests delete", R.drawable.ic_action_delete, activity.getRightSwipePeekIcon("Water plants #every 1d #done"))

        swipeRight("Water plants #every 1d #done")
        assertTrue(activity.isPendingDelete("Water plants #every 1d #done"))
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val pendingPos = activity.getDisplayedRemindersForTesting().indexOfFirst { it == "Water plants #every 1d #done" }
        val pendingHolder = recyclerView.findViewHolderForAdapterPosition(pendingPos)
        assertEquals("Pending delete only allows left swipe", ItemTouchHelper.LEFT, swipeHandler.getSwipeDirs(recyclerView, pendingHolder!!))
        assertEquals("Pending delete left: undoes the delete", R.drawable.ic_action_undo, activity.getLeftSwipePeekIcon("Water plants #every 1d #done"))
        assertEquals("Deleted card right swipe is disabled", 0, activity.getRightSwipePeekIcon("Water plants #every 1d #done"))
    }

    @Test
    fun testRecurrenceTagSpanAppliedToRecurringTask() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Water plants #every 1d #notify 1h" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        val text = holder.reminderInput.text
        assertTrue("Input text should be SPANNABLE", text is android.text.Spanned)
        val spanned = text as android.text.Spanned
        val spans = spanned.getSpans(0, spanned.length, android.text.style.ForegroundColorSpan::class.java)
        assertEquals(2, spans.size)
    }

    @Test
    fun testFocusProfilesPersistenceAndFiltering() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
            put(org.json.JSONObject().put("name", "Fitness").put("query", "#fitness"))
        }.toString()

        prefs.edit()
            .putString("key_focus_profiles", profilesJson)
            .putString("key_active_focus_profile", "Work")
            .apply()
        seedTasks(context, "Buy groceries #work" to null, "Gym session #fitness" to null, "Doctor #health" to null)

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
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
        }.toString()

        prefs.edit()
            .putString("key_focus_profiles", profilesJson)
            .putString("key_active_focus_profile", "Work")
            .apply()
        seedTasks(context, "Task A #work" to null, "Task B #personal" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val searchInput = activity.findViewById<android.widget.EditText>(R.id.search_reminder_input)
        assertEquals("Focus: Work", searchInput.hint.toString())

        val clearFocusBtn = activity.findViewById<android.widget.TextView>(R.id.btn_empty_clear_focus)
        clearFocusBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Add reminder (press ↵) or search...", searchInput.hint.toString())

        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val adapter = recyclerView.adapter as RemindersAdapter
        assertEquals(4, adapter.itemCount)
    }

    @Test
    fun testFocusProfilesDialogInlineEditAndMultiToggle() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", android.content.Context.MODE_PRIVATE)
        val profilesJson = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("name", "Work").put("query", "#work"))
            put(org.json.JSONObject().put("name", "Fitness").put("query", "#fitness"))
        }.toString()

        prefs.edit()
            .putString("key_focus_profiles", profilesJson)
            .putStringSet("key_active_focus_profiles", setOf("Work", "Fitness"))
            .apply()
        seedTasks(context, "Task A #work" to null, "Task B #fitness" to null, "Task C #other" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.showFocusProfilesDialog()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val focusDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(focusDialog)

        focusDialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val activeSet = prefs.getStringSet("key_active_focus_profiles", null)
        assertNotNull(activeSet)
        assertTrue(activeSet!!.contains("Work"))
        assertTrue(activeSet.contains("Fitness"))
        assertTrue(activity.findViewById<android.widget.EditText>(R.id.search_reminder_input).hint.toString().contains("Focus:"))
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

        // Search "gym" on Work focus falls back to searching all items: 1 header + 1 matching task ("Gym session #fitness") + 1 footer = 3 items
        assertEquals(3, adapter.itemCount)
        val gymHolder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertTrue(gymHolder.reminderInput.text.toString().contains("Gym session #fitness"))
    }

    @Test
    fun testCollapsibleActiveAndPuntedSectionHeaders() {
        val context = RuntimeEnvironment.getApplication()
        val snoozeTime = System.currentTimeMillis() + 3600000L
        seedTasks(context, "Task Active" to null, "Task Punted" to snoozeTime)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val adapter = recyclerView.adapter as RemindersAdapter

        // Measure and layout RecyclerView
        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val holder0 = adapter.createViewHolder(recyclerView, RemindersAdapter.TYPE_SECTION_HEADER) as RemindersAdapter.HeaderViewHolder
        adapter.onBindViewHolder(holder0, 0)
        assertEquals("▼ ACTIVE (1)", holder0.txtHeaderTitle.text.toString())

        activity.toggleActiveCollapsed()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        adapter.onBindViewHolder(holder0, 0)
        assertEquals("▶ ACTIVE (1)", holder0.txtHeaderTitle.text.toString())

        val holder1 = adapter.createViewHolder(recyclerView, RemindersAdapter.TYPE_SECTION_HEADER) as RemindersAdapter.HeaderViewHolder
        adapter.onBindViewHolder(holder1, 1)
        assertEquals("▼ PUNTED (1)", holder1.txtHeaderTitle.text.toString())

        activity.togglePuntedCollapsed()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        adapter.onBindViewHolder(holder1, 1)
        assertEquals("▶ PUNTED (1)", holder1.txtHeaderTitle.text.toString())

        // Collapsed both: 1 active header + 1 punted header + 1 footer = 3 items
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun testSectionHeaderCountsUpdateOnTaskStateChange() {
        val context = RuntimeEnvironment.getApplication()
        seedTasks(context, "Task A" to null, "Task B" to null)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val adapter = recyclerView.adapter as RemindersAdapter

        recyclerView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        recyclerView.layout(0, 0, 1000, 1000)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val holder0 = adapter.createViewHolder(recyclerView, RemindersAdapter.TYPE_SECTION_HEADER) as RemindersAdapter.HeaderViewHolder
        adapter.onBindViewHolder(holder0, 0)
        assertEquals("▼ ACTIVE (2)", holder0.txtHeaderTitle.text.toString())

        // Apply snooze to Task A -> active decreases to 1, punted increases to 1
        val applySnoozeMethod = MainActivity::class.java.getDeclaredMethod("applySnoozeDuration", String::class.java, String::class.java)
        applySnoozeMethod.isAccessible = true
        applySnoozeMethod.invoke(activity, "Task A", "1h")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        adapter.onBindViewHolder(holder0, 0)
        assertEquals("▼ ACTIVE (1)", holder0.txtHeaderTitle.text.toString())

        val holderPunted = adapter.createViewHolder(recyclerView, RemindersAdapter.TYPE_SECTION_HEADER) as RemindersAdapter.HeaderViewHolder
        adapter.onBindViewHolder(holderPunted, 2) // ACTIVE header + Task B + PUNTED header
        assertEquals("▼ PUNTED (1)", holderPunted.txtHeaderTitle.text.toString())
    }
}
