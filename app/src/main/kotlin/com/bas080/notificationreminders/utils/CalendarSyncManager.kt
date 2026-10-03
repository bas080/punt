@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders.utils

import android.Manifest
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

    private var isSyncing = false

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun getOrCreateCalendarId(context: Context): Long {
        if (!hasCalendarPermission(context)) return -1L

        val contentResolver = context.contentResolver
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE)

        try {
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (_: Exception) {
        }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF33B5E5.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }

        return try {
            val insertedUri = contentResolver.insert(uri, values)
            val id = insertedUri?.lastPathSegment?.toLongOrNull() ?: -1L
            if (id != -1L) id else 1L
        } catch (_: Exception) {
            -1L
        }
    }

    @Synchronized
    fun syncRemindersToCalendar(context: Context) {
        if (!hasCalendarPermission(context) || isSyncing) return
        val calendarId = getOrCreateCalendarId(context)
        if (calendarId == -1L) return

        isSyncing = true
        try {
            val contentResolver = context.contentResolver
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val savedReminders = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
            val now = System.currentTimeMillis()

            val existingEvents = mutableMapOf<String, Long>()
            val eventsUri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART
            )
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
            val selectionArgs = arrayOf(calendarId.toString())

            try {
                contentResolver.query(eventsUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val eventId = cursor.getLong(0)
                        val title = cursor.getString(1) ?: ""
                        val locationKey = cursor.getString(2) ?: MainActivity.getCleanTrimmed(title)
                        existingEvents[locationKey] = eventId
                    }
                }
            } catch (_: Exception) {
            }

            val activeKeys = mutableSetOf<String>()

            for (reminder in savedReminders) {
                val isDone = reminder.contains("#done", ignoreCase = true)
                val cleanKey = MainActivity.getCleanTrimmed(reminder)

                if (isDone) {
                    val existingId = existingEvents[cleanKey]
                    if (existingId != null) {
                        try {
                            val deleteUri = ContentUris.withAppendedId(eventsUri, existingId)
                            contentResolver.delete(deleteUri, null, null)
                        } catch (_: Exception) {
                        }
                    }
                    continue
                }

                activeKeys.add(cleanKey)

                val snoozeUntil = prefs.getLong("snooze_$cleanKey", 0L).let {
                    if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanKey"] ?: 0L)
                }

                val cleanTitle = reminder.replace(Regex("(?i)\\s*#done\\b"), "").trim()
                val isPunted = snoozeUntil > now
                val eventStart = if (isPunted) snoozeUntil else now
                val eventEnd = eventStart + 3600000L

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, cleanTitle)
                    put(CalendarContract.Events.DESCRIPTION, "Punt reminder todo")
                    put(CalendarContract.Events.EVENT_LOCATION, cleanKey)
                    put(CalendarContract.Events.DTSTART, eventStart)
                    put(CalendarContract.Events.DTEND, eventEnd)
                    put(CalendarContract.Events.ALL_DAY, if (isPunted) 0 else 1)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }

                val existingId = existingEvents[cleanKey]
                try {
                    if (existingId != null) {
                        val updateUri = ContentUris.withAppendedId(eventsUri, existingId)
                        contentResolver.update(updateUri, values, null, null)
                    } else {
                        contentResolver.insert(eventsUri, values)
                    }
                } catch (_: Exception) {
                }
            }

            for ((key, eventId) in existingEvents) {
                if (!activeKeys.contains(key)) {
                    try {
                        val deleteUri = ContentUris.withAppendedId(eventsUri, eventId)
                        contentResolver.delete(deleteUri, null, null)
                    } catch (_: Exception) {
                    }
                }
            }

            prefs.edit().putStringSet(KEY_SYNCED_CALENDAR_KEYS, activeKeys).commit()
        } catch (_: Exception) {
        } finally {
            isSyncing = false
        }
    }

    @Synchronized
    fun syncCalendarChangesToReminders(context: Context) {
        if (!hasCalendarPermission(context) || isSyncing) return
        val calendarId = getOrCreateCalendarId(context)
        if (calendarId == -1L) return

        isSyncing = true
        try {
            val contentResolver = context.contentResolver
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val syncedCalendarKeys = prefs.getStringSet(KEY_SYNCED_CALENDAR_KEYS, emptySet()) ?: emptySet()
            if (syncedCalendarKeys.isEmpty()) return

            val savedReminders = (prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()).toMutableList()
            var modified = false

            val calendarEvents = mutableMapOf<String, Long>()
            val eventsUri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART
            )
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
            val selectionArgs = arrayOf(calendarId.toString())

            try {
                contentResolver.query(eventsUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(1) ?: ""
                        val locationKey = cursor.getString(2) ?: MainActivity.getCleanTrimmed(title)
                        val dtStart = cursor.getLong(3)
                        calendarEvents[locationKey] = dtStart
                    }
                }
            } catch (_: Exception) {
            }

            if (calendarEvents.isEmpty()) {
                // If query returned no events, do not mark all items done to prevent false positives when query fails or returns empty
                return
            }

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
                    if (calStart > now && Math.abs(calStart - currentSnooze) > 60000L) {
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
        } catch (_: Exception) {
        } finally {
            isSyncing = false
        }
    }
}
