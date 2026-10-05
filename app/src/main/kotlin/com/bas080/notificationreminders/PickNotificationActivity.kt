@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bas080.notificationreminders.services.ReminderNotificationListenerService

class PickNotificationActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        var mockActiveNotifications: Array<StatusBarNotification>? = null

        fun formatNotificationText(title: String, text: String): String {
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

        val notificationsList = getActiveNotificationsList()

        if (notificationsList.isEmpty()) {
            AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
                .setTitle(getString(R.string.select_notification))
                .setMessage(getString(R.string.no_active_notifications))
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        } else {
            val itemsArray = notificationsList.toTypedArray()
            val textPrimaryColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
            val adapter = object : android.widget.ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                itemsArray
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = super.getView(position, convertView, parent)
                    if (view is android.widget.TextView) {
                        view.setTextColor(textPrimaryColor)
                    }
                    return view
                }
            }

            AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
                .setTitle(getString(R.string.select_notification))
                .setAdapter(adapter) { _, which ->
                    val selectedText = itemsArray[which]
                    saveReminder(selectedText)
                    finish()
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    private fun getActiveNotificationsList(): List<String> {
        val activeSbns = mockActiveNotifications ?: try {
            ReminderNotificationListenerService.instance?.activeNotifications
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val results = mutableListOf<String>()
        for (sbn in activeSbns) {
            if (sbn.packageName == packageName) continue
            val extras = sbn.notification?.extras ?: continue
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val formatted = formatNotificationText(title, text)
            if (formatted.isNotEmpty() && !results.contains(formatted)) {
                results.add(formatted)
            }
        }
        return results
    }

    private fun saveReminder(reminderText: String) {
        com.bas080.notificationreminders.utils.AppLogger.log(this, "PickNotificationActivity", "Selected notification as reminder")
        com.bas080.notificationreminders.utils.ExperimentTracker.trackCreation(this, "pick_notification")
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        savedSet.add(reminderText)
        prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()

        ReminderNotificationListenerService.instance?.postMatchNotification(reminderText)
        ReminderNotificationListenerService.startService(this)
        Toast.makeText(this, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
    }
}
