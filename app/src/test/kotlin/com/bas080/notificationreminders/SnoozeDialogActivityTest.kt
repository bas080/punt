package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
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
class SnoozeDialogActivityTest {

    @Test
    fun testSingleReminderSnoozeFromSwipeIntent() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, SnoozeDialogActivity::class.java).apply {
            putExtra(SnoozeDialogActivity.EXTRA_REMINDER_TEXT, "Buy milk")
        }

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull("List view in dialog should exist", listView)

        // Select first duration option (e.g. 15m)
        shadowOf(listView).performItemClick(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Activity should be finishing after snooze choice", activity.isFinishing)
        assertTrue("Toast should indicate reminder punted", ShadowToast.getTextOfLatestToast().startsWith("Reminder punted for"))

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = prefs.getLong("snooze_buy milk", 0L)
        assertTrue("Snooze timestamp should be saved in preferences", snoozeTime > System.currentTimeMillis())
    }

    @Test
    fun testGroupSummaryMultipleRemindersSnoozeFromSwipeIntent() {
        val context = RuntimeEnvironment.getApplication()
        val targets = arrayOf("Buy milk", "Call doctor", "Pay bill")
        val intent = Intent(context, SnoozeDialogActivity::class.java).apply {
            putExtra(SnoozeDialogActivity.EXTRA_REMINDER_LIST, targets)
        }

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze All dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull("List view in dialog should exist", listView)

        // Select first duration option (e.g. 15m)
        shadowOf(listView).performItemClick(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Activity should finish after snoozing all reminders", activity.isFinishing)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        for (target in targets) {
            val key = "snooze_" + target.trim().lowercase()
            val snoozeTime = prefs.getLong(key, 0L)
            assertTrue("Snooze time for '$target' should be in future", snoozeTime > now)
        }
    }

    @Test
    fun testEmptyIntentFinishesActivity() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, SnoozeDialogActivity::class.java)

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        assertTrue("Activity should finish when started without reminder targets", activity.isFinishing)
    }

    @Test
    fun testCustomSnoozeDialogValidInput() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, SnoozeDialogActivity::class.java).apply {
            putExtra(SnoozeDialogActivity.EXTRA_REMINDER_TEXT, "Buy bread")
        }

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull(listView)

        // Click "Custom..." option (last item)
        val customIndex = listView.adapter.count - 1
        shadowOf(listView).performItemClick(customIndex)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val customDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Custom snooze input dialog should be shown", customDialog)

        val inputEditText = customDialog!!.findViewById<android.widget.EditText>(R.id.import_input)
        assertNotNull("Custom input EditText should exist", inputEditText)
        inputEditText!!.setText("30m")

        customDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Activity should finish after custom snooze entry", activity.isFinishing)
        assertEquals("Reminder punted for 30 minutes", ShadowToast.getTextOfLatestToast())

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = prefs.getLong("snooze_buy bread", 0L)
        assertTrue("Snooze timestamp should be saved in preferences", snoozeTime > System.currentTimeMillis())
    }

    @Test
    fun testCustomSnoozeDialogInvalidInput() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, SnoozeDialogActivity::class.java).apply {
            putExtra(SnoozeDialogActivity.EXTRA_REMINDER_TEXT, "Buy bread")
        }

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull(listView)

        // Click "Custom..." option (last item)
        val customIndex = listView.adapter.count - 1
        shadowOf(listView).performItemClick(customIndex)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val customDialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(customDialog)

        val inputEditText = customDialog!!.findViewById<android.widget.EditText>(R.id.import_input)
        inputEditText!!.setText("invalid_duration")

        customDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Activity should finish on invalid snooze entry", activity.isFinishing)
        assertEquals("Failed to punt: Invalid duration entered", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testCancelSnoozeDialog() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, SnoozeDialogActivity::class.java).apply {
            putExtra(SnoozeDialogActivity.EXTRA_REMINDER_TEXT, "Buy bread")
        }

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should be shown", dialog)

        dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Activity should finish when dialog is cancelled", activity.isFinishing)
    }

    @Test
    fun testFlexibleDateParsingWithoutYearAndWithLeadingZeros() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JANUARY, 1, 10, 0, 0)
        }
        val now = cal.timeInMillis

        // Test MM/dd without year (e.g. 10/25)
        val res1 = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseSnoozeDuration("10/25", now)
        assertNotNull("10/25 should parse cleanly without a year", res1)
        assertTrue(res1!!.first > 0L)

        // Test single digit M/d without year (e.g. 5/1)
        val res2 = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseSnoozeDuration("5/1", now)
        assertNotNull("5/1 should parse cleanly without leading zeros or a year", res2)
        assertTrue(res2!!.first > 0L)

        // Test dd/MM without year (e.g. 25/10)
        val res3 = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseSnoozeDuration("25/10", now)
        assertNotNull("25/10 should parse cleanly without a year", res3)
        assertTrue(res3!!.first > 0L)

        // Test dot separator with leading zeros (e.g. 05.01)
        val res4 = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseSnoozeDuration("05.01", now)
        assertNotNull("05.01 should parse cleanly with dot separator", res4)
        assertTrue(res4!!.first > 0L)

        // Test hyphen separator (e.g. 5-1)
        val res5 = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseSnoozeDuration("5-1", now)
        assertNotNull("5-1 should parse cleanly with hyphen separator", res5)
        assertTrue(res5!!.first > 0L)

        // Test date without year + time (e.g. 10/25 18:00)
        val res6 = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseSnoozeDuration("10/25 18:00", now)
        assertNotNull("10/25 18:00 should parse date + time without year", res6)
        assertTrue(res6!!.first > 0L)

        // Test date without year + 12-hour am/pm time (e.g. 5/1 3pm)
        val res7 = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseSnoozeDuration("5/1 3pm", now)
        assertNotNull("5/1 3pm should parse date + 12-hour am/pm time without year", res7)
        assertTrue(res7!!.first > 0L)
    }
}
