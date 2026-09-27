package com.bas080.notificationreminders.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderNotificationListenerServiceTest {

    @Test
    fun testActionCreateReminderConstant() {
        assertEquals(
            "com.bas080.notificationreminders.ACTION_CREATE_REMINDER",
            ReminderNotificationListenerService.ACTION_CREATE_REMINDER
        )
    }

    @Test
    fun testChannelIdConstant() {
        assertEquals(
            "notification_reminders_status_channel",
            ReminderNotificationListenerService.CHANNEL_ID
        )
    }

    @Test
    fun testNotificationIdConstant() {
        assertEquals(
            1001,
            ReminderNotificationListenerService.NOTIFICATION_ID
        )
    }

    @Test
    fun testGetNotificationIdForReminderIsDeterministic() {
        val id1 = ReminderNotificationListenerService.getNotificationIdForReminder("Buy milk")
        val id2 = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val id3 = ReminderNotificationListenerService.getNotificationIdForReminder("  Buy Milk  ")

        assertEquals(id1, id2)
        assertEquals(id1, id3)
        assertNotEquals(ReminderNotificationListenerService.NOTIFICATION_ID, id1)
    }

    @Test
    fun testStatusNotificationUpdatesOnReminderMatch() {
        val context = RuntimeEnvironment.getApplication()

        // Set up saved reminder in SharedPreferences
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("buy milk")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        // Create mock StatusBarNotification
        val extras = Bundle().apply {
            putCharSequence("android.title", "Shopping")
            putCharSequence("android.text", "Need to buy milk today")
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

        service.onNotificationPosted(sbn)

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Status notification should be updated on reminder match", statusNotif)
        val textLines = statusNotif.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertNotNull("Status notification should contain text lines for active reminders", textLines)
        assertEquals(1, textLines!!.size)
        assertEquals("buy milk", textLines[0].toString())
    }

    @Test
    fun testStatusNotificationTitleTextAndContentIntent() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Buy milk", "Call mom"))
            .putLong("snooze_call mom", System.currentTimeMillis() + 3600000L)
            .commit()

        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Status notification should be posted", statusNotif)

        val title = statusNotif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        assertEquals("Add Reminder", title)

        val statusText = statusNotif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertEquals("1 active • 1 punted", statusText)

        val textLines = statusNotif.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertNotNull("Status notification should have text lines for active items", textLines)
        assertEquals(1, textLines!!.size)
        assertEquals("Buy milk", textLines[0].toString())

        assertNotNull("Content intent should be set on status notification", statusNotif.contentIntent)
        val shadowPendingIntent = Shadows.shadowOf(statusNotif.contentIntent)
        val targetIntent = shadowPendingIntent.savedIntent
        assertNotNull("Target intent should not be null", targetIntent)
        assertEquals("com.bas080.notificationreminders.MainActivity", targetIntent.component?.className)

        assertNotNull("Status notification should have actions", statusNotif.actions)
        assertEquals(2, statusNotif.actions.size)

        val addAction = statusNotif.actions[0]
        assertEquals("From Text", addAction.title.toString())
        assertNotNull("From Text action should have remoteInputs", addAction.remoteInputs)
        assertEquals(1, addAction.remoteInputs.size)

        val remoteInput = addAction.remoteInputs[0]
        assertEquals(ReminderNotificationListenerService.KEY_TEXT_REPLY, remoteInput.resultKey)
        assertEquals("Add Reminder", remoteInput.label.toString())

        val fromNotifAction = statusNotif.actions[1]
        assertEquals("From Notification", fromNotifAction.title.toString())
    }


    @Test
    fun testSelfNotificationIsProcessedForReminderMatch() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        val context = RuntimeEnvironment.getApplication()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("buy milk")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        // Create mock StatusBarNotification originating from the app's own package name
        val extras = Bundle().apply {
            putCharSequence("android.title", "Reminder Alert")
            putCharSequence("android.text", "Need to buy milk today")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            context.packageName,
            context.packageName,
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(sbn)

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Notification from self package should still trigger reminder match update on status notification", statusNotif)
    }

    @Test
    fun testMatchNotificationChannelConfiguresVibrationAndSound() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(ReminderNotificationListenerService.MATCH_CHANNEL_ID)

        assertNotNull("Match notification channel should exist", channel)
        assertTrue("Vibration should be enabled", channel.shouldVibrate())
        assertNotNull("Vibration pattern should not be null", channel.vibrationPattern)
        assertNotNull("Sound URI should not be null", channel.sound)
    }

    @Test
    fun testActiveSnoozeOverrulesNotificationMatch() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        val context = RuntimeEnvironment.getApplication()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("buy milk"))
            .putLong("snooze_buy milk", System.currentTimeMillis() + 60000L)
            .commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        val extras = Bundle().apply {
            putCharSequence("android.title", "Shopping")
            putCharSequence("android.text", "Need to buy milk today")
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

        service.onNotificationPosted(sbn)

        val matchedNotifId = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val matchedNotif = shadowNM.getNotification(matchedNotifId)
        org.junit.Assert.assertNull("Notification match should NOT be posted while snooze is active", matchedNotif)
    }

    @Test
    fun testSnoozeExpiryRetriggersOnAnyNotificationWithNormalPriority() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        val context = RuntimeEnvironment.getApplication()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("buy milk")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        // Set snooze in the past (expired)
        ReminderNotificationListenerService.lastTriggeredMap["snooze_buy milk"] = System.currentTimeMillis() - 1000L

        // Post an unrelated notification that does NOT contain "milk"
        val extras = Bundle().apply {
            putCharSequence("android.title", "Battery Low")
            putCharSequence("android.text", "15% remaining")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            "com.example.system",
            "com.example.system",
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(sbn)

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Expired snoozed item should update status notification when any notification arrives", statusNotif)
    }

    @Test
    fun testGetTopSnoozeChoicesDefaultAndRecent() {
        val context = RuntimeEnvironment.getApplication()
        val defaultChoices = ReminderNotificationListenerService.getTopSnoozeChoices(context)
        assertEquals(5, defaultChoices.size)
        assertEquals("15m", defaultChoices[0].toString())

        // Save custom snooze timestamps
        val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("30m", 1000L)
            .putLong("2h", 2000L)
            .commit()

        val updatedChoices = ReminderNotificationListenerService.getTopSnoozeChoices(context)
        assertEquals(7, updatedChoices.size)
        assertEquals("15m", updatedChoices[0].toString())
        assertEquals("30m", updatedChoices[1].toString())
        assertEquals("1h", updatedChoices[2].toString())
        assertEquals("2h", updatedChoices[3].toString())
        assertEquals("4h", updatedChoices[4].toString())
    }

    @Test
    fun testGetTopSnoozeChoicesCombines4RecentAnd6MostCommonAndSortsAllTogether() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)

        // Set up 10 different choices with varying timestamps and counts:
        // C1: ts 1000, count 1
        // C2: ts 900, count 1
        // C3: ts 800, count 1
        // C4: ts 700, count 1
        // C5: ts 100, count 50
        // C6: ts 90, count 40
        // C7: ts 80, count 30
        // C8: ts 70, count 20
        // C9: ts 60, count 10
        // C10: ts 50, count 5
        prefs.edit()
            .putLong("10m", 1000L).putLong("count_10m", 1L) // Recent #1
            .putLong("20m", 900L).putLong("count_20m", 1L)   // Recent #2
            .putLong("30m", 800L).putLong("count_30m", 1L)   // Recent #3
            .putLong("2h", 700L).putLong("count_2h", 1L)     // Recent #4
            .putLong("3h", 100L).putLong("count_3h", 50L)    // Common #1
            .putLong("5h", 90L).putLong("count_5h", 40L)     // Common #2
            .putLong("12h", 80L).putLong("count_12h", 30L)   // Common #3
            .putLong("1d", 70L).putLong("count_1d", 20L)     // Common #4
            .putLong("2d", 60L).putLong("count_2d", 10L)     // Common #5
            .putLong("3d", 50L).putLong("count_3d", 5L)      // Common #6
            .commit()

        val choices = ReminderNotificationListenerService.getTopSnoozeChoices(context).map { it.toString() }

        // Top 4 recent: 10m, 20m, 30m, 2h
        // Top 6 common: 3h, 5h, 12h, 1d, 2d, 3d
        // Combined (10 items) sorted by duration: 10m, 20m, 30m, 2h, 3h, 5h, 12h, 1d, 2d, 3d (+ defaults if any missing)
        assertTrue("Should contain 10m (recent)", choices.contains("10m"))
        assertTrue("Should contain 20m (recent)", choices.contains("20m"))
        assertTrue("Should contain 30m (recent)", choices.contains("30m"))
        assertTrue("Should contain 2h (recent)", choices.contains("2h"))
        assertTrue("Should contain 3h (common)", choices.contains("3h"))
        assertTrue("Should contain 5h (common)", choices.contains("5h"))
        assertTrue("Should contain 12h (common)", choices.contains("12h"))
        assertTrue("Should contain 24h (common)", choices.contains("24h"))
        assertTrue("Should contain 2d (common)", choices.contains("2d"))
        assertTrue("Should contain 3d (common)", choices.contains("3d"))

        // Verify sorted order (10m < 20m < 30m < 2h < 3h < 5h < 12h < 1d < 2d < 3d)
        val index10m = choices.indexOf("10m")
        val index20m = choices.indexOf("20m")
        val index30m = choices.indexOf("30m")
        val index2h = choices.indexOf("2h")
        val index3h = choices.indexOf("3h")
        val index5h = choices.indexOf("5h")

        assertTrue("10m should come before 20m", index10m < index20m)
        assertTrue("20m should come before 30m", index20m < index30m)
        assertTrue("30m should come before 2h", index30m < index2h)
        assertTrue("2h should come before 3h", index2h < index3h)
        assertTrue("3h should come before 5h", index3h < index5h)
    }

    @Test
    fun testGetTopSnoozeChoicesDeduplicatesEquivalentDurationsAndCanonicalChoices() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)

        prefs.edit()
            .putLong("1d", 1000L).putLong("count_1d", 5L)
            .putLong("24h", 2000L).putLong("count_24h", 10L)
            .commit()

        val choices = ReminderNotificationListenerService.getTopSnoozeChoices(context).map { it.toString() }

        assertEquals("24h should appear exactly once", 1, choices.count { it == "24h" })
        assertFalse("1d should be canonicalized and deduplicated into 24h", choices.contains("1d"))
    }

    @Test
    fun testPostMatchNotificationUpdatesStatusNotification() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Buy grocers")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        service.postMatchNotification("Buy grocers")

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Status notification should be updated when postMatchNotification is called", statusNotif)
        val textLines = statusNotif.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertNotNull("Status notification should contain active reminder lines", textLines)
        assertEquals("Buy grocers", textLines!![0].toString())
    }
}
