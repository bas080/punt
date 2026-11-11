package com.bas080.notificationreminders.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.data.ReminderEntity
import com.bas080.notificationreminders.utils.ReminderMatcher
import kotlinx.coroutines.runBlocking

class RemindersContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.bas080.notificationreminders.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/reminders")

        private const val REMINDERS = 1
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

        val dao = PuntDatabase.getInstance(ctx).reminderDao()
        val allReminders = runBlocking { dao.getAllReminders() }

        val filterQuery = selection?.takeIf { it.isNotBlank() }
            ?: uri.getQueryParameter("q")
            ?: uri.getQueryParameter("query")
            ?: uri.getQueryParameter("filter")

        val filteredReminders = if (!filterQuery.isNullOrBlank()) {
            val matchingTexts = ReminderMatcher.filterSearchQueryTiered(
                allReminders.map { reminder -> reminder.displayText() },
                filterQuery
            ).toSet()
            allReminders.filter { reminder -> reminder.displayText() in matchingTexts }
        } else {
            allReminders
        }

        val columns = projection ?: DEFAULT_PROJECTION
        val cursor = MatrixCursor(columns)
        populateMatrixCursor(cursor, columns, filteredReminders)

        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    private fun populateMatrixCursor(
        cursor: MatrixCursor,
        columns: Array<out String>,
        reminders: List<ReminderEntity>
    ) {
        val now = System.currentTimeMillis()

        for (reminder in reminders) {
            val rowBuilder = cursor.newRow()
            for (col in columns) {
                when (col) {
                    COLUMN_ID -> rowBuilder.add(reminder.id)
                    COLUMN_TEXT -> rowBuilder.add(reminder.displayText())
                    COLUMN_SNOOZE_UNTIL -> rowBuilder.add(reminder.snoozeUntil?.takeIf { it > now } ?: 0L)
                    COLUMN_IS_DONE -> rowBuilder.add(if (reminder.isDone) IS_DONE_TRUE else IS_DONE_FALSE)
                    else -> rowBuilder.add(null)
                }
            }
        }
    }

    private fun ReminderEntity.displayText(): String = if (isDone) "$text #done" else text

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
