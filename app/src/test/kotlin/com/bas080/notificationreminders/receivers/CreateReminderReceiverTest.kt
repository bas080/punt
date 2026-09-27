package com.bas080.notificationreminders.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateReminderReceiverTest {

    @org.junit.Before
    fun setUp() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
    }

    @Test
    fun testCreateReminderShowsSuccessToast() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY, "Buy apples")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_CREATE_REMINDER)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_TEXT_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testCreateReminderUpdatesStatusNotificationDirectly() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val receiver = CreateReminderReceiver()
        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY, "Buy apples")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_CREATE_REMINDER)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_TEXT_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNM = org.robolectric.Shadows.shadowOf(notificationManager)
        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        org.junit.Assert.assertNotNull("Creating a reminder should directly update status notification", statusNotif)
        val textLines = statusNotif.extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
        assertEquals(1, textLines!!.size)
        assertEquals("Buy apples", textLines[0].toString())
    }

    @Test
    fun testCreateReminderEmptyShowsFailureToast() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY, "   ")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_CREATE_REMINDER)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_TEXT_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testDoneReminderShowsSuccessToastAndPreservesSnooze() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val futureSnooze = System.currentTimeMillis() + 60000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Buy milk"))
            .putLong("snooze_buy milk", futureSnooze)
            .commit()

        val receiver = CreateReminderReceiver()

        val intent = Intent(ReminderNotificationListenerService.ACTION_DONE_REMINDER).apply {
            putExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT, "Buy milk")
        }

        receiver.onReceive(context, intent)

        assertEquals("Reminder marked done", ShadowToast.getTextOfLatestToast())
        val snoozeTimestamp = prefs.getLong("snooze_buy milk", 0L)
        assertEquals(futureSnooze, snoozeTimestamp)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        org.junit.Assert.assertTrue("Saved set should contain 'Buy milk #done'", savedSet.contains("Buy milk #done"))
    }

    @Test
    fun testSnoozeReminderShowsSuccessToastAndSetsSnoozeTimestamp() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val intent = Intent(ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER).apply {
            putExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT, "Buy milk")
        }

        receiver.onReceive(context, intent)

        assertEquals("Reminder punted for 1 hour", ShadowToast.getTextOfLatestToast())
        val snoozeUntil = ReminderNotificationListenerService.lastTriggeredMap["snooze_buy milk"] ?: 0L
        org.junit.Assert.assertTrue("Snooze timestamp should be in the future", snoozeUntil > System.currentTimeMillis())
    }

    @Test
    fun testSnoozeWithSelectedDuration() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_SNOOZE_REPLY, "15m")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER).apply {
            putExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT, "Buy bread")
        }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_SNOOZE_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        assertEquals("Reminder punted for 15 minutes", ShadowToast.getTextOfLatestToast())

        val freqPrefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)
        val timestamp = freqPrefs.getLong("15m", 0L)
        org.junit.Assert.assertTrue("Timestamp for 15m snooze choice should be positive", timestamp > 0L)
    }

    @Test
    fun testParseSnoozeDurationHelper() {
        val (ms15m, label15m) = CreateReminderReceiver.parseSnoozeDuration("15m")!!
        assertEquals(15 * 60 * 1000L, ms15m)
        assertEquals("15 minutes", label15m)

        val (ms4h, label4h) = CreateReminderReceiver.parseSnoozeDuration("4h")!!
        assertEquals(4 * 60 * 60 * 1000L, ms4h)
        assertEquals("4 hours", label4h)

        val (ms24h, label24h) = CreateReminderReceiver.parseSnoozeDuration("24h")!!
        assertEquals(24 * 60 * 60 * 1000L, ms24h)
        assertEquals("24 hours", label24h)

        val (ms1d, label1d) = CreateReminderReceiver.parseSnoozeDuration("1d")!!
        assertEquals(24 * 60 * 60 * 1000L, ms1d)
        assertEquals("24 hours", label1d)

        val (ms2w, label2w) = CreateReminderReceiver.parseSnoozeDuration("2w")!!
        assertEquals(2 * 7 * 24 * 60 * 60 * 1000L, ms2w)
        assertEquals("2 weeks", label2w)

        // Absolute time test with fixed base timestamp e.g. 12:00 PM today
        val baseCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val baseMillis = baseCal.timeInMillis

        // "18:00" from 12:00 -> 6 hours later today
        val (ms1800, label1800) = CreateReminderReceiver.parseSnoozeDuration("18:00", baseMillis)!!
        assertEquals(6 * 60 * 60 * 1000L, ms1800)
        assertEquals("today at 18:00", label1800)

        // "1800" (no colon) from 12:00 -> 6 hours later today
        val (ms1800NoColon, label1800NoColon) = CreateReminderReceiver.parseSnoozeDuration("1800", baseMillis)!!
        assertEquals(6 * 60 * 60 * 1000L, ms1800NoColon)
        assertEquals("today at 18:00", label1800NoColon)

        // "7pm" from 12:00 -> 7 hours later today (19:00)
        val (ms7pm, label7pm) = CreateReminderReceiver.parseSnoozeDuration("7pm", baseMillis)!!
        assertEquals(7 * 60 * 60 * 1000L, ms7pm)
        assertEquals("today at 19:00", label7pm)

        // "1am" from 12:00 PM -> 13 hours later tomorrow (01:00)
        val (ms1am, label1am) = CreateReminderReceiver.parseSnoozeDuration("1am", baseMillis)!!
        assertEquals(13 * 60 * 60 * 1000L, ms1am)
        assertEquals("tomorrow at 01:00", label1am)
    }

    @Test
    fun testParseSnoozeDurationInvalidInputsReturnNull() {
        org.junit.Assert.assertNull(CreateReminderReceiver.parseSnoozeDuration("5s"))
        org.junit.Assert.assertNull(CreateReminderReceiver.parseSnoozeDuration("invalid_text"))
        org.junit.Assert.assertNull(CreateReminderReceiver.parseSnoozeDuration("0m"))
    }

    @Test
    fun testSnoozeWithInvalidDurationShowsErrorToast() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_SNOOZE_REPLY, "5s")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER).apply {
            putExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT, "Buy milk")
        }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_SNOOZE_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        assertEquals("Failed to punt: Invalid duration entered", ShadowToast.getTextOfLatestToast())
        val snoozeUntil = ReminderNotificationListenerService.lastTriggeredMap["snooze_buy milk"] ?: 0L
        assertEquals(0L, snoozeUntil)
    }

    @Test
    fun testParseWeekdaySnoozeDuration() {
        // Fix base timestamp at Wednesday, March 12, 2025 at 10:00 AM
        val cal = java.util.Calendar.getInstance().apply {
            set(2025, java.util.Calendar.MARCH, 12, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = cal.timeInMillis

        // "Mon" / "monday" from Wednesday -> next Monday (March 17) at 09:00 AM = 119 hours later
        val (msMon, labelMon) = CreateReminderReceiver.parseSnoozeDuration("Mon", nowMillis)!!
        assertEquals(119 * 60 * 60 * 1000L, msMon)
        assertEquals("Monday at 09:00", labelMon)

        val (msMonday, labelMonday) = CreateReminderReceiver.parseSnoozeDuration("monday", nowMillis)!!
        assertEquals(119 * 60 * 60 * 1000L, msMonday)
        assertEquals("Monday at 09:00", labelMonday)

        // "fri 18:00" from Wednesday -> Friday (March 14) at 18:00 = 56 hours later
        val (msFri18, labelFri18) = CreateReminderReceiver.parseSnoozeDuration("fri 18:00", nowMillis)!!
        assertEquals(56 * 60 * 60 * 1000L, msFri18)
        assertEquals("Friday at 18:00", labelFri18)

        // "Friday 6pm" from Wednesday -> Friday (March 14) at 18:00 = 56 hours later
        val (msFri6pm, labelFri6pm) = CreateReminderReceiver.parseSnoozeDuration("Friday 6pm", nowMillis)!!
        assertEquals(56 * 60 * 60 * 1000L, msFri6pm)
        assertEquals("Friday at 18:00", labelFri6pm)

        assertEquals("mon", CreateReminderReceiver.canonicalizeSnoozeChoice("Mon"))
        assertEquals("mon", CreateReminderReceiver.canonicalizeSnoozeChoice("monday"))
        assertEquals("fri 18:00", CreateReminderReceiver.canonicalizeSnoozeChoice("Fri 18:00"))
        assertEquals("fri 18:00", CreateReminderReceiver.canonicalizeSnoozeChoice("Friday 6pm"))
    }

    @Test
    fun testCanonicalizeSnoozeChoice() {
        assertEquals("18:00", CreateReminderReceiver.canonicalizeSnoozeChoice("6pm"))
        assertEquals("18:00", CreateReminderReceiver.canonicalizeSnoozeChoice("1800"))
        assertEquals("18:00", CreateReminderReceiver.canonicalizeSnoozeChoice("18:00"))
        assertEquals("06:00", CreateReminderReceiver.canonicalizeSnoozeChoice("6am"))
        assertEquals("15m", CreateReminderReceiver.canonicalizeSnoozeChoice("15 mins"))
        assertEquals("1h", CreateReminderReceiver.canonicalizeSnoozeChoice("1 hour"))
        assertEquals("24h", CreateReminderReceiver.canonicalizeSnoozeChoice("1d"))
        assertEquals("24h", CreateReminderReceiver.canonicalizeSnoozeChoice("24h"))
        org.junit.Assert.assertNull(CreateReminderReceiver.canonicalizeSnoozeChoice("5s"))
    }

    @Test
    fun testParseCompositeSnoozeDuration() {
        // Fix base timestamp at Monday, October 12, 2026 at 10:00 AM
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 12, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = cal.timeInMillis

        // "2d 1800": Monday 10:00 AM + 2 days = Wednesday 10:00 AM; then until 18:00 = Wednesday 18:00 (56 hours total)
        val (ms2d1800, _) = CreateReminderReceiver.parseSnoozeDuration("2d 1800", nowMillis)!!
        assertEquals(56 * 60 * 60 * 1000L, ms2d1800)

        // "1d 2h 15m": Monday 10:00 AM + 1d 2h 15m = Tuesday 12:15 PM (26 hours 15 mins = 94,500,000 ms)
        val (msComposite1, _) = CreateReminderReceiver.parseSnoozeDuration("1d 2h 15m", nowMillis)!!
        assertEquals((26 * 60 * 60 * 1000L) + (15 * 60 * 1000L), msComposite1)

        // "2d 7pm": Monday 10:00 AM + 2d = Wednesday 10:00 AM; then 7pm (19:00) = Wednesday 19:00 (57 hours total)
        val (ms2d7pm, _) = CreateReminderReceiver.parseSnoozeDuration("2d 7pm", nowMillis)!!
        assertEquals(57 * 60 * 60 * 1000L, ms2d7pm)

        assertEquals("2d 18:00", CreateReminderReceiver.canonicalizeSnoozeChoice("2d 1800"))
        assertEquals("24h 2h 15m", CreateReminderReceiver.canonicalizeSnoozeChoice("1d 2h 15m"))
    }

    @Test
    fun testParseDateSnoozeDuration() {
        // Fix base timestamp at Monday, October 12, 2026 at 10:00 AM
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 12, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = cal.timeInMillis

        // "10/25/2026": October 25, 2026 at 09:00 AM
        val targetCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 25, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val expectedMs = targetCal.timeInMillis - nowMillis

        val (msDate, _) = CreateReminderReceiver.parseSnoozeDuration("10/25/2026", nowMillis)!!
        assertEquals(expectedMs, msDate)
    }

    @Test
    fun testCustomSnoozeHintFormat() {
        val context = RuntimeEnvironment.getApplication()
        val hint = CreateReminderReceiver.getSnoozeCustomHint(context)
        org.junit.Assert.assertTrue("Custom hint should start with 'e.g. 15m, 18:00, Mon, or '", hint.startsWith("e.g. 15m, 18:00, Mon, or "))
    }
}
