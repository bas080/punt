package com.bas080.notificationreminders.utils

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.bas080.notificationreminders.MainActivity
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import java.util.TimeZone

object CalendarSyncManager {

    private const val ACCOUNT_NAME = "Punt"
    private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
    private const val CALENDAR_NAME = "Punt Reminders"
    private const val PREFS_REMINDERS = "reminders_prefs"
    private const val KEY_REMINDERS = "key_reminders_list"
    private const val KEY_SYNCED_CALENDAR_KEYS = "key_synced_calendar_keys"
    private const val KEY_CALENDAR_SYNC_ENABLED = "key_calendar_sync_enabled"
    private const val ONE_HOUR_MS = 3_600_000L
    private const val ONE_MINUTE_MS = 60_000L
    private const val CALENDAR_COLOR = 0xFF33B5E5.toInt()
    private const val ALL_DAY_TRUE = 1
    private const val ALL_DAY_FALSE = 0
    private const val PROJECTION_DTSTART_INDEX = 3

    private var isSyncing = false

    fun isCalendarSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CALENDAR_SYNC_ENABLED, false)
    }

    fun setCalendarSyncEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CALENDAR_SYNC_ENABLED, enabled).commit()
    }

    fun hasCalendarPermission(context: Context): Boolean {
        val readPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val writePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return readPerm == PackageManager.PERMISSION_GRANTED && writePerm == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun getOrCreateCalendarId(context: Context): Long {
        if (!hasCalendarPermission(context)) return -1L

        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
                "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE)

        var foundId = -1L
        try {
            val proj = arrayOf(CalendarContract.Calendars._ID)
            context.contentResolver.query(uri, proj, selection, selectionArgs, null)?.use {
                if (it.moveToFirst()) foundId = it.getLong(0)
            }
        } catch (_: Exception) {
        }

        if (foundId == -1L) {
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
                put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }

            try {
                val insertedUri = context.contentResolver.insert(uri, values)
                val id = insertedUri?.lastPathSegment?.toLongOrNull() ?: -1L
                foundId = if (id != -1L) id else 1L
            } catch (_: Exception) {
            }
        }

        return foundId
    }

    @Synchronized
    fun syncRemindersToCalendar(context: Context) {
        if (!isCalendarSyncEnabled(context) || !hasCalendarPermission(context) || isSyncing) return
        val calendarId = getOrCreateCalendarId(context)
        if (calendarId == -1L) return

        isSyncing = true
        try {
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val savedReminders = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
            val existingEvents = queryCalendarEvents(
                context.contentResolver,
                calendarId,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.EVENT_LOCATION
                )
            )
            val activeKeys = processSyncEntries(context, calendarId, savedReminders, existingEvents)
            prefs.edit().putStringSet(KEY_SYNCED_CALENDAR_KEYS, activeKeys).commit()
        } catch (_: Exception) {
        } finally {
            isSyncing = false
        }
    }

    private fun processSyncEntries(
        context: Context,
        calendarId: Long,
        savedReminders: Set<String>,
        existingEvents: Map<String, Long>
    ): Set<String> {
        val activeKeys = mutableSetOf<String>()
        val eventsUri = CalendarContract.Events.CONTENT_URI
        for (reminder in savedReminders) {
            val cleanKey = MainActivity.getCleanTrimmed(reminder)
            val existingId = existingEvents[cleanKey]
            val isDone = reminder.contains("#done", ignoreCase = true)
            if (isDone && existingId != null) {
                try {
                    val deleteUri = ContentUris.withAppendedId(eventsUri, existingId)
                    context.contentResolver.delete(deleteUri, null, null)
                } catch (_: Exception) {
                }
            } else if (!isDone) {
                activeKeys.add(cleanKey)
                upsertReminderEvent(context, calendarId, reminder, cleanKey, existingId)
            }
        }
        for ((key, eventId) in existingEvents) {
            if (!activeKeys.contains(key)) {
                try {
                    val deleteUri = ContentUris.withAppendedId(eventsUri, eventId)
                    context.contentResolver.delete(deleteUri, null, null)
                } catch (_: Exception) {
                }
            }
        }
        return activeKeys
    }

    private fun queryCalendarEvents(
        contentResolver: ContentResolver,
        calendarId: Long,
        projection: Array<String>
    ): MutableMap<String, Long> {
        val events = mutableMapOf<String, Long>()
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        val cursor = try {
            contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, null)
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val title = it.getString(1) ?: ""
                val locationKey = it.getString(2) ?: MainActivity.getCleanTrimmed(title)
                val hasDtStart = projection.size > PROJECTION_DTSTART_INDEX
                val valToStore = if (hasDtStart) it.getLong(PROJECTION_DTSTART_INDEX) else id
                events[locationKey] = valToStore
            }
        }
        return events
    }

    private fun upsertReminderEvent(
        context: Context,
        calendarId: Long,
        reminder: String,
        cleanKey: String,
        existingId: Long?
    ) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val snoozeUntil = prefs.getLong("snooze_$cleanKey", 0L).let {
            if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanKey"] ?: 0L)
        }

        val cleanTitle = reminder.replace(Regex("(?i)\\s*#done\\b"), "").trim()
        val isPunted = snoozeUntil > now
        val eventStart = if (isPunted) snoozeUntil else now
        val eventEnd = eventStart + ONE_HOUR_MS

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, cleanTitle)
            put(CalendarContract.Events.DESCRIPTION, "Punt reminder todo")
            put(CalendarContract.Events.EVENT_LOCATION, cleanKey)
            put(CalendarContract.Events.DTSTART, eventStart)
            put(CalendarContract.Events.DTEND, eventEnd)
            put(CalendarContract.Events.ALL_DAY, if (isPunted) ALL_DAY_FALSE else ALL_DAY_TRUE)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        try {
            if (existingId != null) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                context.contentResolver.update(updateUri, values, null, null)
            } else {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun syncCalendarChangesToReminders(context: Context) {
        if (!isCalendarSyncEnabled(context) || !hasCalendarPermission(context) || isSyncing) return
        val calendarId = getOrCreateCalendarId(context)
        if (calendarId == -1L) return

        isSyncing = true
        try {
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val syncedCalendarKeys = prefs.getStringSet(KEY_SYNCED_CALENDAR_KEYS, emptySet()) ?: emptySet()
            if (syncedCalendarKeys.isNotEmpty()) {
                val calendarEvents = queryCalendarEvents(
                    context.contentResolver,
                    calendarId,
                    arrayOf(
                        CalendarContract.Events._ID,
                        CalendarContract.Events.TITLE,
                        CalendarContract.Events.EVENT_LOCATION,
                        CalendarContract.Events.DTSTART
                    )
                )
                if (calendarEvents.isNotEmpty()) {
                    processCalendarDiffs(context, syncedCalendarKeys, calendarEvents)
                }
            }
        } catch (_: Exception) {
        } finally {
            isSyncing = false
        }
    }

    private fun processCalendarDiffs(
        context: Context,
        syncedCalendarKeys: Set<String>,
        calendarEvents: Map<String, Long>
    ) {
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedReminders = (prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()).toMutableList()
        var modified = false
        val editor = prefs.edit()
        val now = System.currentTimeMillis()

        for (i in savedReminders.indices) {
            val reminder = savedReminders[i]
            if (reminder.contains("#done", ignoreCase = true)) continue

            val cleanKey = MainActivity.getCleanTrimmed(reminder)
            if (syncedCalendarKeys.contains(cleanKey) && !calendarEvents.containsKey(cleanKey)) {
                savedReminders[i] = "$reminder #done"
                modified = true
            } else if (calendarEvents.containsKey(cleanKey)) {
                val calStart = calendarEvents[cleanKey] ?: 0L
                val currentSnooze = prefs.getLong("snooze_$cleanKey", 0L)
                if (calStart > now && Math.abs(calStart - currentSnooze) > ONE_MINUTE_MS) {
                    editor.putLong("snooze_$cleanKey", calStart)
                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanKey"] = calStart
                    modified = true
                }
            }
        }

        if (modified) {
            editor.putStringSet(KEY_REMINDERS, savedReminders.toSet()).commit()
            ReminderNotificationListenerService.instance?.showStatusNotification()
        }
    }
}
