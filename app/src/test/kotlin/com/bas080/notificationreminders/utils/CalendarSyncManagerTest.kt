@file:Suppress("DEPRECATION")
package com.bas080.notificationreminders.utils

import android.Manifest
import android.content.Context
import android.provider.CalendarContract
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
import org.robolectric.fakes.RoboCursor

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
    fun testSyncRemindersAndCalendarChanges() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

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
    fun testSyncCalendarMoveAndUpdatePuntTime() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val initialSnooze = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Movable Task"))
            .putLong("snooze_movable task", initialSnooze)
            .commit()

        CalendarSyncManager.syncRemindersToCalendar(context)

        val newStartTime = initialSnooze + 7200000L
        val roboCursor = RoboCursor().apply {
            setColumnNames(listOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART
            ))
            setResults(arrayOf(arrayOf(1L, "Movable Task", "movable task", newStartTime)))
        }
        shadowOf(context.contentResolver).setCursor(CalendarContract.Events.CONTENT_URI, roboCursor)

        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val updatedSnooze = prefs.getLong("snooze_movable task", 0L)
        assertEquals(newStartTime, updatedSnooze)
    }

    @Test
    fun testSyncCalendarDeleteMarksReminderDone() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Deleted Task", "Remaining Task")).commit()

        CalendarSyncManager.syncRemindersToCalendar(context)

        val roboCursor = RoboCursor().apply {
            setColumnNames(listOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART
            ))
            setResults(arrayOf(arrayOf(2L, "Remaining Task", "remaining task", System.currentTimeMillis())))
        }
        shadowOf(context.contentResolver).setCursor(CalendarContract.Events.CONTENT_URI, roboCursor)

        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.any { it.contains("Deleted Task") && it.contains("#done") })
    }
}
