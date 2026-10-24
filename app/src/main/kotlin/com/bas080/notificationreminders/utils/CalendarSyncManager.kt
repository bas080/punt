@file:Suppress("TooManyFunctions")
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
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
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
    private const val DEFAULT_ALL_DAY_HOUR = 9
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 1440

    private var isSyncing = false

    data class CalendarEventDetails(
        val id: Long,
        val title: String,
        val locationKey: String,
        val dtStart: Long,
        val description: String?,
        val allDay: Boolean
    )

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
            val existingEvents = queryCalendarEventsMap(context.contentResolver, calendarId)
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

    private fun queryCalendarEventsMap(
        contentResolver: ContentResolver,
        calendarId: Long
    ): MutableMap<String, Long> {
        val events = mutableMapOf<String, Long>()
        val details = queryCalendarEventsDetails(contentResolver, calendarId)
        for (event in details) {
            events[event.locationKey] = event.id
        }
        return events
    }

    private fun queryCalendarEventsDetails(
        contentResolver: ContentResolver,
        calendarId: Long
    ): List<CalendarEventDetails> {
        val eventsList = mutableListOf<CalendarEventDetails>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        val cursor = try {
            contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, null)
                ?: contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, null, null, null)
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val locationIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val dtStartIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)

            while (it.moveToNext()) {
                val id = if (idIdx >= 0) it.getLong(idIdx) else -1L
                val title = if (titleIdx >= 0) (it.getString(titleIdx) ?: "") else ""
                val location = if (locationIdx >= 0) it.getString(locationIdx) else null
                val locationKey = if (!location.isNullOrBlank()) location else MainActivity.getCleanTrimmed(title)
                val dtStart = if (dtStartIdx >= 0) it.getLong(dtStartIdx) else 0L
                eventsList.add(CalendarEventDetails(id, title, locationKey, dtStart, null, false))
            }
        }
        return eventsList
    }

    private fun queryEventAlarmMinutes(contentResolver: ContentResolver, eventId: Long): List<Int> {
        val minutesList = mutableListOf<Int>()
        val projection = arrayOf(CalendarContract.Reminders.MINUTES)
        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        try {
            contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val minIdx = cursor.getColumnIndex(CalendarContract.Reminders.MINUTES)
                while (cursor.moveToNext()) {
                    val mins = if (minIdx >= 0) cursor.getInt(minIdx) else -1
                    if (mins >= 0) {
                        minutesList.add(mins)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return minutesList
    }

    fun formatMinutesToDuration(minutes: Int): String {
        return when {
            minutes % MINUTES_PER_DAY == 0 && minutes > 0 -> "${minutes / MINUTES_PER_DAY}d"
            minutes % MINUTES_PER_HOUR == 0 && minutes > 0 -> "${minutes / MINUTES_PER_HOUR}h"
            else -> "${minutes}m"
        }
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

        val eventId = try {
            var id: Long? = null
            if (existingId != null) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                val updatedRows = context.contentResolver.update(updateUri, values, null, null)
                if (updatedRows > 0) {
                    id = existingId
                }
            }
            if (id == null) {
                val insertedUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                id = insertedUri?.lastPathSegment?.toLongOrNull()
            }
            id
        } catch (_: Exception) {
            null
        }

        if (eventId != null) {
            syncEventReminders(context, eventId, reminder, isPunted, eventStart)
        }
    }

    private fun syncEventReminders(
        context: Context,
        eventId: Long,
        reminder: String,
        isPunted: Boolean,
        eventStart: Long
    ) {
        try {
            val remindersUri = CalendarContract.Reminders.CONTENT_URI
            val deleteSelection = "${CalendarContract.Reminders.EVENT_ID} = ?"
            val deleteArgs = arrayOf(eventId.toString())
            context.contentResolver.delete(remindersUri, deleteSelection, deleteArgs)

            val notifyDurations = getNotifyDurationsForEvent(reminder, isPunted, eventStart)
            for ((leadMs, _) in notifyDurations) {
                insertCalendarReminder(context, remindersUri, eventId, leadMs)
            }
        } catch (_: Exception) {
        }
    }

    private fun insertCalendarReminder(
        context: Context,
        remindersUri: android.net.Uri,
        eventId: Long,
        leadMs: Long
    ) {
        val minutes = (leadMs / ONE_MINUTE_MS).toInt()
        if (minutes >= 0) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(remindersUri, reminderValues)
        }
    }

    private fun getNotifyDurationsForEvent(
        reminder: String,
        isPunted: Boolean,
        eventStart: Long
    ): List<Pair<Long, String>> {
        if (isPunted) {
            return CreateReminderReceiver.parseNotifyDurations(reminder, eventStart)
        }
        val default9AmMillis = java.util.Calendar.getInstance().apply {
            timeInMillis = eventStart
            set(java.util.Calendar.HOUR_OF_DAY, DEFAULT_ALL_DAY_HOUR)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return CreateReminderReceiver.parseNotifyDurations(reminder, default9AmMillis)
    }

    @Synchronized
    fun syncCalendarChangesToReminders(context: Context) {
        if (!isCalendarSyncEnabled(context) || !hasCalendarPermission(context) || isSyncing) return
        val calendarId = getOrCreateCalendarId(context)
        if (calendarId == -1L) return

        isSyncing = true
        try {
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val syncedCalendarKeys = (
                prefs.getStringSet(KEY_SYNCED_CALENDAR_KEYS, emptySet()) ?: emptySet()
            ).toMutableSet()
            val calendarEvents = queryCalendarEventsDetails(context.contentResolver, calendarId)
            if (calendarEvents.isNotEmpty()) {
                processCalendarDiffsAndImports(context, syncedCalendarKeys, calendarEvents)
            }
        } catch (_: Exception) {
        } finally {
            isSyncing = false
        }
    }

    private fun processCalendarDiffsAndImports(
        context: Context,
        syncedCalendarKeys: MutableSet<String>,
        calendarEvents: List<CalendarEventDetails>
    ) {
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedReminders = (prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()).toMutableList()
        var modified = false
        val editor = prefs.edit()
        val now = System.currentTimeMillis()

        val eventMapByLocation = calendarEvents.associateBy { it.locationKey }
        val eventMapByTitleKey = calendarEvents.associateBy { MainActivity.getCleanTrimmed(it.title) }

        // 1. Process existing reminders (check for deletions or move/start time changes)
        for (i in savedReminders.indices) {
            val reminder = savedReminders[i]
            if (reminder.contains("#done", ignoreCase = true)) continue

            val cleanKey = MainActivity.getCleanTrimmed(reminder)
            val matchedEvent = eventMapByLocation[cleanKey] ?: eventMapByTitleKey[cleanKey]

            if (syncedCalendarKeys.contains(cleanKey) && matchedEvent == null) {
                val recurringResult = CreateReminderReceiver.parseRecurrenceDuration(reminder, now)
                if (recurringResult != null) {
                    val snoozeMs: Long = recurringResult.first
                    val snoozeUntil: Long = now + snoozeMs
                    val cleanText = reminder.replace(Regex("(?i)\\s*#done\\b"), "").trim()
                    val cleanTrimmed = cleanText.lowercase()
                    savedReminders[i] = cleanText
                    editor.putLong("snooze_$cleanTrimmed", snoozeUntil)
                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] = snoozeUntil
                } else {
                    savedReminders[i] = "$reminder #done"
                }
                modified = true
            } else if (matchedEvent != null) {
                val calStart = matchedEvent.dtStart
                val currentSnooze = prefs.getLong("snooze_$cleanKey", 0L)
                if (calStart > now && Math.abs(calStart - currentSnooze) > ONE_MINUTE_MS) {
                    editor.putLong("snooze_$cleanKey", calStart)
                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanKey"] = calStart
                    modified = true
                }
            }
        }

        // 2. Process new external events created in calendar app
        val existingCleanKeys = savedReminders.map { MainActivity.getCleanTrimmed(it) }.toSet()

        for (event in calendarEvents) {
            val titleCleanKey = MainActivity.getCleanTrimmed(event.title)
            if (titleCleanKey.isEmpty()) continue

            val isKnown = existingCleanKeys.contains(event.locationKey) ||
                    existingCleanKeys.contains(titleCleanKey) ||
                    syncedCalendarKeys.contains(event.locationKey) ||
                    syncedCalendarKeys.contains(titleCleanKey)

            if (!isKnown) {
                val alarmMins = queryEventAlarmMinutes(context.contentResolver, event.id)
                val alarmTags = alarmMins.distinct().joinToString(" ") { "#every${formatMinutesToDuration(it)}" }
                val baseTitle = event.title.trim()
                val fullText = if (alarmTags.isNotEmpty()) "$baseTitle $alarmTags" else baseTitle

                val cleanKey = MainActivity.getCleanTrimmed(fullText)
                savedReminders.add(fullText)
                syncedCalendarKeys.add(cleanKey)
                syncedCalendarKeys.add(titleCleanKey)

                if (event.dtStart > now) {
                    editor.putLong("snooze_$cleanKey", event.dtStart)
                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanKey"] = event.dtStart
                }

                modified = true
            }
        }

        if (modified) {
            editor.putStringSet(KEY_REMINDERS, savedReminders.toSet())
            editor.putStringSet(KEY_SYNCED_CALENDAR_KEYS, syncedCalendarKeys)
            editor.commit()
            ReminderNotificationListenerService.instance?.showStatusNotification()
            TaskBackupManager.backupTasks(context)
        }
    }
}
