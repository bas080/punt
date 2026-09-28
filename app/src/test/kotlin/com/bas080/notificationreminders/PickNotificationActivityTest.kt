package com.bas080.notificationreminders

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.appcompat.app.AlertDialog
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PickNotificationActivityTest {

    @After
    fun tearDown() {
        PickNotificationActivity.mockActiveNotifications = null
    }

    @Test
    fun testFormatNotificationText() {
        assertEquals("Shopping: Need milk", PickNotificationActivity.formatNotificationText("Shopping", "Need milk"))
        assertEquals("Shopping", PickNotificationActivity.formatNotificationText("Shopping", ""))
        assertEquals("Need milk", PickNotificationActivity.formatNotificationText("", "Need milk"))
        assertEquals("", PickNotificationActivity.formatNotificationText("", ""))
    }

    @Test
    fun testShowsNoNotificationsDialogWhenEmpty() {
        val controller = Robolectric.buildActivity(PickNotificationActivity::class.java).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown when no active notifications exist", dialog)

        dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun testSelectionSavesReminderToPrefs() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val extras = Bundle().apply {
            putCharSequence("android.title", "Email")
            putCharSequence("android.text", "Meeting at 3pm")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            "com.example.otherapp",
            "com.example.otherapp",
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        // Mock active notifications
        PickNotificationActivity.mockActiveNotifications = arrayOf(sbn)

        val controller = Robolectric.buildActivity(PickNotificationActivity::class.java).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown for active notifications", dialog)

        val listView = dialog!!.listView
        assertNotNull(listView)
        assertEquals(1, listView.adapter.count)
        assertEquals("Email: Meeting at 3pm", listView.adapter.getItem(0))

        // Perform click on first item
        shadowOf(listView).performItemClick(0)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val savedReminders = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved reminders should contain selected notification text", savedReminders.contains("Email: Meeting at 3pm"))

        assertEquals("Reminder created", org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun testSelfNotificationFilterAndDialogCancel() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()

        val selfExtras = Bundle().apply {
            putCharSequence("android.title", "Self Title")
            putCharSequence("android.text", "Self Text")
        }
        @Suppress("DEPRECATION")
        val selfSbn = StatusBarNotification(
            context.packageName, context.packageName, 1, "tag", 1000, 1000, 1,
            Notification.Builder(context, "test_channel").setExtras(selfExtras).build(),
            android.os.Process.myUserHandle(), System.currentTimeMillis()
        )

        val otherExtras = Bundle().apply {
            putCharSequence("android.title", "Other Title")
            putCharSequence("android.text", "Other Text")
        }
        @Suppress("DEPRECATION")
        val otherSbn = StatusBarNotification(
            "com.other.app", "com.other.app", 2, "tag", 1000, 1000, 1,
            Notification.Builder(context, "test_channel").setExtras(otherExtras).build(),
            android.os.Process.myUserHandle(), System.currentTimeMillis()
        )

        PickNotificationActivity.mockActiveNotifications = arrayOf(selfSbn, otherSbn)

        val controller = Robolectric.buildActivity(PickNotificationActivity::class.java).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        val listView = dialog!!.listView
        assertEquals(1, listView.adapter.count)
        assertEquals("Other Title: Other Text", listView.adapter.getItem(0))

        // Trigger getView on adapter
        val rowView = listView.adapter.getView(0, null, listView)
        assertNotNull(rowView)

        // Cancel dialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
    }
}
