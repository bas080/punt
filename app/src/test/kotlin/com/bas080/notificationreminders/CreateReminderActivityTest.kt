package com.bas080.notificationreminders

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.EditText
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
class CreateReminderActivityTest {

    @org.junit.Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        kotlinx.coroutines.runBlocking {
            com.bas080.notificationreminders.data.PuntDatabase.getInstance(context).reminderDao().deleteAll()
        }
    }

    @Test
    fun testActionCreateReminderTitleOnly() {
        val intent = Intent("android.intent.action.CREATE_REMINDER").apply {
            putExtra(Intent.EXTRA_TITLE, "Call Alice")
        }

        val controller = Robolectric.buildActivity(CreateReminderActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Confirmation dialog should be displayed", dialog)

        val positiveBtn = dialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
        assertNotNull(positiveBtn)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(activity).reminderDao()
        val stored = kotlinx.coroutines.runBlocking { dao.getReminderByText("Call Alice") }
        assertNotNull("Reminder should be stored in Room", stored)
    }

    @Test
    fun testActionCreateReminderTitleAndNotes() {
        val intent = Intent("android.intent.action.CREATE_REMINDER").apply {
            putExtra(Intent.EXTRA_TITLE, "Call Alice")
            putExtra(Intent.EXTRA_TEXT, "Discuss the project")
        }

        val controller = Robolectric.buildActivity(CreateReminderActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        val positiveBtn = dialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(activity).reminderDao()
        val stored = kotlinx.coroutines.runBlocking { dao.getReminderByText("Call Alice: Discuss the project") }
        assertNotNull("Reminder should be stored in Room", stored)
    }

    @Test
    fun testActionCreateReminderTitleAndFutureTimeSnoozesReminder() {
        val futureTime = System.currentTimeMillis() + 3600000L // 1 hour in future
        val intent = Intent("android.intent.action.CREATE_REMINDER").apply {
            putExtra(Intent.EXTRA_TITLE, "Dentist Appointment")
            putExtra(Intent.EXTRA_TIME, futureTime)
        }

        val controller = Robolectric.buildActivity(CreateReminderActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        val positiveBtn = dialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(activity).reminderDao()
        val stored = kotlinx.coroutines.runBlocking { dao.getReminderByText("Dentist Appointment") }
        assertNotNull("Reminder should be stored in Room", stored)

        val snoozeVal = stored!!.snoozeUntil ?: 0L
        assertEquals(futureTime, snoozeVal)
    }

    @Test
    fun testActionCreateReminderTitleTextAndTime() {
        val futureTime = System.currentTimeMillis() + 7200000L // 2 hours in future
        val intent = Intent("android.intent.action.CREATE_REMINDER").apply {
            putExtra(Intent.EXTRA_TITLE, "Team Sync")
            putExtra(Intent.EXTRA_TEXT, "Room 404")
            putExtra(Intent.EXTRA_TIME, futureTime)
        }

        val controller = Robolectric.buildActivity(CreateReminderActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        val positiveBtn = dialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(activity).reminderDao()
        val stored = kotlinx.coroutines.runBlocking { dao.getReminderByText("Team Sync: Room 404") }
        assertNotNull("Reminder should be stored in Room", stored)

        val snoozeVal = stored!!.snoozeUntil ?: 0L
        assertEquals(futureTime, snoozeVal)
    }

    @Test
    fun testActionCreateReminderMissingExtrasAndEmptyTextFails() {
        val intent = Intent("android.intent.action.CREATE_REMINDER")

        val controller = Robolectric.buildActivity(CreateReminderActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        val positiveBtn = dialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testActionCreateReminderCancellationReturnsResultCanceled() {
        val intent = Intent("android.intent.action.CREATE_REMINDER").apply {
            putExtra(Intent.EXTRA_TITLE, "Cancelled Task")
        }

        val controller = Robolectric.buildActivity(CreateReminderActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        val negativeBtn = dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE)
        negativeBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(activity).reminderDao()
        val stored = kotlinx.coroutines.runBlocking { dao.getReminderByText("Cancelled Task") }
        assertTrue("Cancelled task should not be stored in Room", stored == null)
    }
}
