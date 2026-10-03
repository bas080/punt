@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreateReminderActivity : AppCompatActivity() {

    companion object {
        const val ACTION_CREATE_REMINDER = "android.intent.action.CREATE_REMINDER"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_TIME = "android.intent.extra.TIME"

        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"

        fun formatReminderString(title: String, text: String): String {
            val trimmedTitle = title.trim()
            val trimmedText = text.trim()
            return when {
                trimmedTitle.isNotEmpty() && trimmedText.isNotEmpty() -> "$trimmedTitle: $trimmedText"
                trimmedTitle.isNotEmpty() -> trimmedTitle
                trimmedText.isNotEmpty() -> trimmedText
                else -> ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleExtra = intent.getCharSequenceExtra(EXTRA_TITLE)?.toString()
            ?: intent.getStringExtra(EXTRA_TITLE)
            ?: ""
        val textExtra = intent.getCharSequenceExtra(EXTRA_TEXT)?.toString()
            ?: intent.getStringExtra(EXTRA_TEXT)
            ?: ""
        val timeExtra = intent.getLongExtra(EXTRA_TIME, 0L)

        AppLogger.log(this, "CreateReminderActivity", "Received ACTION_CREATE_REMINDER intent")

        showCreateReminderDialog(titleExtra, textExtra, timeExtra)
    }

    private fun showCreateReminderDialog(initialTitle: String, initialText: String, targetTimeMillis: Long) {
        val density = resources.displayMetrics.density
        val paddingPx = (16 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)
        }

        val etTitle = EditText(this).apply {
            id = R.id.import_input
            hint = "Title"
            setSingleLine(true)
            setText(initialTitle)
        }
        container.addView(etTitle)

        val etText = EditText(this).apply {
            hint = "Notes / Description"
            setLines(3)
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setText(initialText)
        }
        container.addView(etText)

        val now = System.currentTimeMillis()
        if (targetTimeMillis > now) {
            val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy 'at' HH:mm", Locale.US)
            val formattedTime = dateFormat.format(Date(targetTimeMillis))
            val txtTime = TextView(this).apply {
                text = "Scheduled: $formattedTime"
                textSize = 12f
                setTextColor(resources.getColor(R.color.accent, theme))
                setPadding(0, (8 * density).toInt(), 0, 0)
            }
            container.addView(txtTime)
        }

        AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.add_reminder)
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val finalTitle = etTitle.text.toString()
                val finalText = etText.text.toString()
                val reminderString = formatReminderString(finalTitle, finalText)

                if (reminderString.isEmpty()) {
                    Toast.makeText(this, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_CANCELED)
                    finish()
                } else {
                    saveReminder(reminderString, targetTimeMillis)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                AppLogger.log(this, "CreateReminderActivity", "Cancelled reminder creation")
                setResult(RESULT_CANCELED)
                finish()
            }
            .setOnCancelListener {
                AppLogger.log(this, "CreateReminderActivity", "Cancelled reminder creation")
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }

    private fun saveReminder(reminderString: String, targetTimeMillis: Long) {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        savedSet.add(reminderString)

        val now = System.currentTimeMillis()
        val editor = prefs.edit().putStringSet(KEY_REMINDERS, savedSet)

        if (targetTimeMillis > now) {
            val trimmed = reminderString.trim().lowercase()
            ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] = targetTimeMillis
            editor.putLong("snooze_$trimmed", targetTimeMillis)
            AppLogger.log(this, "CreateReminderActivity", "Created scheduled reminder via ACTION_CREATE_REMINDER")
        } else {
            ReminderNotificationListenerService.instance?.postMatchNotification(reminderString)
            AppLogger.log(this, "CreateReminderActivity", "Created immediate reminder via ACTION_CREATE_REMINDER")
        }

        editor.apply()

        ReminderNotificationListenerService.instance?.showStatusNotification()
            ?: ReminderNotificationListenerService.startService(this)

        Toast.makeText(this, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}
