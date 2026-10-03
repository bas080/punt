package com.bas080.notificationreminders.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.bas080.notificationreminders.utils.ReminderMatcher

class RemindersContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.bas080.notificationreminders.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/reminders")

        private const val REMINDERS = 1
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val IS_DONE_TRUE = 1
        private const val IS_DONE_FALSE = 0

        const val COLUMN_ID = "_id"
        const val COLUMN_TEXT = "text"
        const val COLUMN_SNOOZE_UNTIL = "snooze_until"
        const val COLUMN_IS_DONE = "is_done"

        val DEFAULT_PROJECTION = arrayOf(COLUMN_ID, COLUMN_TEXT, COLUMN_SNOOZE_UNTIL, COLUMN_IS_DONE)

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "reminders", REMINDERS)
        }

        fun notifyChange(context: Context) {
            try {
                context.contentResolver.notifyChange(CONTENT_URI, null)
                com.bas080.notificationreminders.utils.CalendarSyncManager.syncRemindersToCalendar(context)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        require(uriMatcher.match(uri) == REMINDERS) { "Unknown URI: $uri" }

        val prefs = ctx.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val rawSet = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
        val allReminders = rawSet.toList()

        val filterQuery = selection?.takeIf { it.isNotBlank() }
            ?: uri.getQueryParameter("q")
            ?: uri.getQueryParameter("query")
            ?: uri.getQueryParameter("filter")

        val filteredReminders = if (!filterQuery.isNullOrBlank()) {
            ReminderMatcher.filterSearchQueryTiered(allReminders, filterQuery)
        } else {
            allReminders
        }

        val columns = projection ?: DEFAULT_PROJECTION
        val cursor = MatrixCursor(columns)
        populateMatrixCursor(cursor, columns, filteredReminders, ctx)

        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    private fun populateMatrixCursor(
        cursor: MatrixCursor,
        columns: Array<out String>,
        reminders: List<String>,
        ctx: Context
    ) {
        val prefs = ctx.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        var idCounter = 1L
        val now = System.currentTimeMillis()

        for (reminder in reminders) {
            val trimmed = reminder.trim().lowercase()
            val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L)
            val isDone = if (reminder.contains("#done", ignoreCase = true)) IS_DONE_TRUE else IS_DONE_FALSE

            val rowBuilder = cursor.newRow()
            for (col in columns) {
                when (col) {
                    COLUMN_ID -> rowBuilder.add(idCounter)
                    COLUMN_TEXT -> rowBuilder.add(reminder)
                    COLUMN_SNOOZE_UNTIL -> rowBuilder.add(if (snoozeUntil > now) snoozeUntil else 0L)
                    COLUMN_IS_DONE -> rowBuilder.add(isDone)
                    else -> rowBuilder.add(null)
                }
            }
            idCounter++
        }
    }

    override fun getType(uri: Uri): String {
        require(uriMatcher.match(uri) == REMINDERS) { "Unknown URI: $uri" }
        return "vnd.android.cursor.dir/vnd.$AUTHORITY.reminders"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("RemindersContentProvider is read-only")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("RemindersContentProvider is read-only")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("RemindersContentProvider is read-only")
    }
}
