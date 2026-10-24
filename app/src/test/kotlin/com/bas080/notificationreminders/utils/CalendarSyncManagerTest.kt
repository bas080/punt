package com.bas080.notificationreminders.utils

import android.Manifest
import android.content.Context
import android.provider.CalendarContract
import com.bas080.notificationreminders.MainActivity
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarSyncManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        CalendarSyncManager.setCalendarSyncEnabled(context, true)
    }

    @Test
    fun testCalendarSyncDisabledByDefault() {
        val testCtx = RuntimeEnvironment.getApplication()
        val prefs = testCtx.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        assertFalse(CalendarSyncManager.isCalendarSyncEnabled(testCtx))
    }

    @Test
    fun testHasCalendarPermission() {
        assertFalse(CalendarSyncManager.hasCalendarPermission(context))

        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        assertTrue(CalendarSyncManager.hasCalendarPermission(context))
    }

    @Test
    fun testGetOrCreateCalendarIdWithPermission() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        val calId1 = CalendarSyncManager.getOrCreateCalendarId(context)
        assertTrue(calId1 != -1L)

        val calId2 = CalendarSyncManager.getOrCreateCalendarId(context)
        assertEquals(calId1, calId2)
    }

    @Test
    fun testFormatMinutesToDuration() {
        assertEquals("15m", CalendarSyncManager.formatMinutesToDuration(15))
        assertEquals("1h", CalendarSyncManager.formatMinutesToDuration(60))
        assertEquals("1d", CalendarSyncManager.formatMinutesToDuration(1440))
        assertEquals("2d", CalendarSyncManager.formatMinutesToDuration(2880))
    }

    @Test
    fun testSyncRemindersAndCalendarChanges() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        CalendarSyncManager.setCalendarSyncEnabled(context, true)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val futureSnooze = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Task Active", "Task Punted", "Task Done #done"))
            .putLong("snooze_task punted", futureSnooze)
            .commit()

        // First sync creates events
        CalendarSyncManager.syncRemindersToCalendar(context)

        // Second sync updates existing events
        CalendarSyncManager.syncRemindersToCalendar(context)

        // Sync changes back (no external edits)
        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.contains("Task Active"))
        assertTrue(savedSet.contains("Task Punted"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun testSyncCalendarMoveAndUpdatePuntTime() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        CalendarSyncManager.setCalendarSyncEnabled(context, true)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val initialSnooze = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Movable Task"))
            .putLong("snooze_movable task", initialSnooze)
            .commit()

        CalendarSyncManager.syncRemindersToCalendar(context)

        val newStartTime = initialSnooze + 7200000L
        val roboCursor = org.robolectric.fakes.RoboCursor().apply {
            setColumnNames(listOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.ALL_DAY
            ))
            setResults(arrayOf(arrayOf(1L, "Movable Task", "movable task", newStartTime, "", 0)))
        }
        shadowOf(context.contentResolver).setCursor(CalendarContract.Events.CONTENT_URI, roboCursor)

        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val updatedSnooze = prefs.getLong("snooze_movable task", 0L)
        assertEquals(newStartTime, updatedSnooze)
    }

    @Test
    fun testSyncRemindersAttachesValarmEntriesForNotifyTags() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        CalendarSyncManager.setCalendarSyncEnabled(context, true)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Meeting #notify 1d #notify 1h"))
            .commit()

        CalendarSyncManager.syncRemindersToCalendar(context)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.contains("Meeting #notify 1d #notify 1h"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun testImportNewExternalCalendarEventCreatesTaskCardAndPunts() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        CalendarSyncManager.setCalendarSyncEnabled(context, true)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val futureStart = System.currentTimeMillis() + 3600000L

        val eventsCursor = org.robolectric.fakes.RoboCursor().apply {
            setColumnNames(listOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.ALL_DAY
            ))
            setResults(arrayOf(
                arrayOf(100L, "Doctor Appointment", "", futureStart, "Checkup", 0)
            ))
        }
        shadowOf(context.contentResolver).setCursor(CalendarContract.Events.CONTENT_URI, eventsCursor)

        val remindersCursor = org.robolectric.fakes.RoboCursor().apply {
            setColumnNames(listOf(CalendarContract.Reminders.MINUTES))
            setResults(arrayOf(arrayOf(15)))
        }
        shadowOf(context.contentResolver).setCursor(CalendarContract.Reminders.CONTENT_URI, remindersCursor)

        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.any { it.startsWith("Doctor Appointment") })

        val createdReminder = savedSet.first { it.startsWith("Doctor Appointment") }
        assertTrue(createdReminder.contains("#every15m"))

        val cleanKey = MainActivity.getCleanTrimmed(createdReminder)
        val snoozeTime = prefs.getLong("snooze_$cleanKey", 0L)
        assertEquals(futureStart, snoozeTime)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testSyncCalendarDeleteMarksReminderDone() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        CalendarSyncManager.setCalendarSyncEnabled(context, true)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Deleted Task", "Remaining Task")).commit()

        CalendarSyncManager.syncRemindersToCalendar(context)

        val roboCursor = org.robolectric.fakes.RoboCursor().apply {
            setColumnNames(listOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.ALL_DAY
            ))
            setResults(arrayOf(arrayOf(2L, "Remaining Task", "remaining task", System.currentTimeMillis(), "", 0)))
        }
        shadowOf(context.contentResolver).setCursor(CalendarContract.Events.CONTENT_URI, roboCursor)

        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.any { it.contains("Deleted Task") && it.contains("#done") })
    }

    @Test
    fun testIntermediateTypingKeystrokesAreNotReimportedAsDuplicateCards() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        CalendarSyncManager.setCalendarSyncEnabled(context, true)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)

        // Simulate typing keystrokes: "Alex", "Alex houseparty", "Alex houseparty #social"
        prefs.edit().putStringSet("key_reminders_list", setOf("Alex")).commit()
        CalendarSyncManager.syncRemindersToCalendar(context)

        prefs.edit().putStringSet("key_reminders_list", setOf("Alex houseparty")).commit()
        CalendarSyncManager.syncRemindersToCalendar(context)

        prefs.edit().putStringSet("key_reminders_list", setOf("Alex houseparty #social")).commit()
        CalendarSyncManager.syncRemindersToCalendar(context)

        // Sync changes from calendar back to Punt
        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        // Ensure Punt holds only the final single reminder and did not re-import intermediate typing drafts
        assertEquals(1, savedSet.size)
        assertTrue(savedSet.contains("Alex houseparty #social"))
    }
}
