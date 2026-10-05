package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger

class ProcessTextActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val selectedText = extractSelectedText(intent)

        if (!selectedText.isNullOrEmpty()) {
            AppLogger.log(this, "ProcessTextActivity", "Created reminder from system text selection/share")
            com.bas080.notificationreminders.utils.ExperimentTracker.trackCreation(this, "text_selection")
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
            savedSet.add(selectedText)
            prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()

            ReminderNotificationListenerService.instance?.postMatchNotification(selectedText)
            ReminderNotificationListenerService.instance?.showStatusNotification()
                ?: ReminderNotificationListenerService.startService(this)

            Toast.makeText(this, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
        } else if (selectedText != null) {
            Toast.makeText(this, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractSelectedText(intent: Intent): String? {
        return when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                } else null
            }
            else -> null
        }?.trim()
    }
}
