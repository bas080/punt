package com.bas080.notificationreminders

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.bas080.notificationreminders.utils.CalendarSyncManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class CalendarSyncAndroidTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testCalendarSyncPreferencesAndDurationFormatting() {
        CalendarSyncManager.setCalendarSyncEnabled(context, true)
        assertTrue(CalendarSyncManager.isCalendarSyncEnabled(context))

        CalendarSyncManager.setCalendarSyncEnabled(context, false)
        assertFalse(CalendarSyncManager.isCalendarSyncEnabled(context))

        assertEquals("15m", CalendarSyncManager.formatMinutesToDuration(15))
        assertEquals("1h", CalendarSyncManager.formatMinutesToDuration(60))
        assertEquals("1d", CalendarSyncManager.formatMinutesToDuration(1440))
        assertEquals("2d", CalendarSyncManager.formatMinutesToDuration(2880))
    }

    @Test
    fun testUnmockedCalendarSyncWithRealContentResolver() {
        if (!CalendarSyncManager.hasCalendarPermission(context)) return

        CalendarSyncManager.setCalendarSyncEnabled(context, true)
        val calId = CalendarSyncManager.getOrCreateCalendarId(context)
        assertTrue(calId > 0L)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val futureStart = System.currentTimeMillis() + 7200000L

        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Unmocked Task #notify 1h", "Punted Unmocked Task"))
            .putLong("snooze_punted unmocked task", futureStart)
            .commit()

        // 1. Sync Punt tasks to real Calendar Provider
        CalendarSyncManager.syncRemindersToCalendar(context)

        // Query real calendar provider for created events
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.SYNC_DATA1,
            CalendarContract.Events.DTSTART
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calId.toString())

        val eventsMap = mutableMapOf<String, String?>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val syncData1Idx = cursor.getColumnIndex(CalendarContract.Events.SYNC_DATA1)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIdx)
                val syncData1 = cursor.getString(syncData1Idx)
                eventsMap[title] = syncData1
            }
        }

        assertTrue(eventsMap.containsKey("Unmocked Task #notify 1h"))
        assertEquals("unmocked task #notify 1h", eventsMap["Unmocked Task #notify 1h"])
        assertTrue(eventsMap.containsKey("Punted Unmocked Task"))
        assertEquals("punted unmocked task", eventsMap["Punted Unmocked Task"])

        // 2. Recompute / update sync without duplicate creation
        CalendarSyncManager.syncRemindersToCalendar(context)

        var eventCount = 0
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            eventCount = cursor.count
        }
        assertEquals(2, eventCount)
    }

    @Test
    fun testUnmockedExternalCalendarEventImportAndStamp() {
        if (!CalendarSyncManager.hasCalendarPermission(context)) return

        CalendarSyncManager.setCalendarSyncEnabled(context, true)
        val calId = CalendarSyncManager.getOrCreateCalendarId(context)
        assertTrue(calId > 0L)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val futureStart = System.currentTimeMillis() + 3600000L

        // Insert a new event directly into system Calendar Provider as an external calendar app
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, "External Dentist Appointment")
            put(CalendarContract.Events.DESCRIPTION, "Routine checkup")
            put(CalendarContract.Events.DTSTART, futureStart)
            put(CalendarContract.Events.DTEND, futureStart + 3600000L)
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val insertedUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        assertNotNull(insertedUri)
        val eventId = insertedUri?.lastPathSegment?.toLongOrNull() ?: -1L
        assertTrue(eventId > 0L)

        // Add a 15-minute alarm
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 15)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

        // Sync calendar changes back to Punt
        CalendarSyncManager.syncCalendarChangesToReminders(context)

        // Verify task card was imported into Punt with alarm duration tag
        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.any { it.startsWith("External Dentist Appointment") })

        val createdReminder = savedSet.first { it.startsWith("External Dentist Appointment") }
        assertTrue(createdReminder.contains("#every15m"))

        val cleanKey = MainActivity.getCleanTrimmed(createdReminder)
        val snoozeTime = prefs.getLong("snooze_$cleanKey", 0L)
        assertEquals(futureStart, snoozeTime)

        // Verify SYNC_DATA1 was stamped in the real ContentProvider
        val updateProjection = arrayOf(CalendarContract.Events.SYNC_DATA1)
        var stampedSyncData: String? = null
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            updateProjection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                stampedSyncData = cursor.getString(0)
            }
        }
        assertEquals(cleanKey, stampedSyncData)
    }

    @Test
    fun testUnmockedCalendarMoveAndUpdateSnoozeInPunt() {
        if (!CalendarSyncManager.hasCalendarPermission(context)) return

        CalendarSyncManager.setCalendarSyncEnabled(context, true)
        val calId = CalendarSyncManager.getOrCreateCalendarId(context)
        assertTrue(calId > 0L)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val initialStart = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Movable Unmocked Task"))
            .putLong("snooze_movable unmocked task", initialStart)
            .commit()

        CalendarSyncManager.syncRemindersToCalendar(context)

        // Query event ID
        var eventId = -1L
        val proj = arrayOf(CalendarContract.Events._ID)
        val sel = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ?"
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            proj,
            sel,
            arrayOf(calId.toString(), "Movable Unmocked Task"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                eventId = cursor.getLong(0)
            }
        }
        assertTrue(eventId > 0L)

        // Move event 2 hours forward in system calendar
        val newStart = initialStart + 7200000L
        val moveValues = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, newStart)
            put(CalendarContract.Events.DTEND, newStart + 3600000L)
        }
        context.contentResolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            moveValues,
            null,
            null
        )

        // Sync changes back to Punt
        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val updatedSnooze = prefs.getLong("snooze_movable unmocked task", 0L)
        assertEquals(newStart, updatedSnooze)
    }

    @Test
    fun testUnmockedCalendarDeletionMarksNonRecurringDone() {
        if (!CalendarSyncManager.hasCalendarPermission(context)) return

        CalendarSyncManager.setCalendarSyncEnabled(context, true)
        val calId = CalendarSyncManager.getOrCreateCalendarId(context)
        assertTrue(calId > 0L)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Task To Delete")).commit()

        CalendarSyncManager.syncRemindersToCalendar(context)

        // Delete event directly in system Calendar Provider
        val deleteSel = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ?"
        context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, deleteSel, arrayOf(calId.toString(), "Task To Delete"))

        // Sync changes back to Punt
        CalendarSyncManager.syncCalendarChangesToReminders(context)

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.contains("Task To Delete #done"))
    }
}
