package com.bas080.notificationreminders.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.R
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger
import com.bas080.notificationreminders.utils.SnoozeParser

class CreateReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"

        fun canonicalizeSingleSnoozeChoice(raw: String): String? =
            SnoozeParser.canonicalizeSingleSnoozeChoice(raw)

        fun canonicalizeSnoozeChoice(input: String?): String? =
            SnoozeParser.canonicalizeSnoozeChoice(input)

        fun getSnoozeCustomHint(context: Context): String =
            SnoozeParser.getSnoozeCustomHint(context)

        fun parseSingleSnoozeDuration(input: String?, nowMillis: Long = System.currentTimeMillis()): Pair<Long, String>? =
            SnoozeParser.parseSingleSnoozeDuration(input, nowMillis)

        fun parseSnoozeDuration(input: String?, nowMillis: Long = System.currentTimeMillis()): Pair<Long, String>? =
            SnoozeParser.parseSnoozeDuration(input, nowMillis)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderNotificationListenerService.ACTION_CREATE_REMINDER -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                if (results != null) {
                    val reminderText = results.getCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY)?.toString()?.trim()
                    if (!reminderText.isNullOrEmpty()) {
                        AppLogger.log(context, "CreateReminderReceiver", "Created reminder from notification reply")
                        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
                        savedSet.add(reminderText)
                        prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()

                        ReminderNotificationListenerService.instance?.postMatchNotification(reminderText)
                        ReminderNotificationListenerService.instance?.showStatusNotification()
                            ?: ReminderNotificationListenerService.startService(context)
                        com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                        Toast.makeText(context, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ReminderNotificationListenerService.ACTION_DONE_REMINDER -> {
                val reminderText = intent.getStringExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT)
                if (!reminderText.isNullOrEmpty()) {
                    AppLogger.log(context, "CreateReminderReceiver", "Marked reminder done from notification action")
                    val trimmed = reminderText.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")

                    val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()

                    if (savedSet.contains(reminderText)) {
                        savedSet.remove(reminderText)
                        val doneText = if (reminderText.contains("#done", ignoreCase = true)) {
                            reminderText
                        } else {
                            "$reminderText #done"
                        }
                        savedSet.add(doneText)
                        prefs.edit().putStringSet(KEY_REMINDERS, savedSet).remove("snooze_$trimmed").apply()
                    }

                    val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)

                    ReminderNotificationListenerService.instance?.showStatusNotification()
                        ?: ReminderNotificationListenerService.startService(context)

                    com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                    Toast.makeText(context, R.string.toast_reminder_done, Toast.LENGTH_SHORT).show()
                }
            }
            ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER -> {
                val reminderText = intent.getStringExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT)
                if (!reminderText.isNullOrEmpty()) {
                    val remoteResults = RemoteInput.getResultsFromIntent(intent)
                    val chosenDurationStr = remoteResults?.getCharSequence(ReminderNotificationListenerService.KEY_SNOOZE_REPLY)?.toString()

                    val parseResult = SnoozeParser.parseSnoozeDuration(chosenDurationStr)
                    if (parseResult == null) {
                        Toast.makeText(context, R.string.toast_invalid_snooze_input, Toast.LENGTH_SHORT).show()
                        return
                    }

                    val canonicalChoice = SnoozeParser.canonicalizeSnoozeChoice(chosenDurationStr)
                    if (canonicalChoice != null) {
                        val freqPrefs = context.getSharedPreferences(PREFS_SNOOZE_FREQ, Context.MODE_PRIVATE)
                        val currentCount = freqPrefs.getLong("count_$canonicalChoice", 0L)
                        freqPrefs.edit()
                            .putLong(canonicalChoice, System.currentTimeMillis())
                            .putLong("count_$canonicalChoice", currentCount + 1L)
                            .apply()
                    }

                    val (snoozeMs, durationLabel) = parseResult
                    val snoozeUntil = System.currentTimeMillis() + snoozeMs
                    val trimmed = reminderText.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] = snoozeUntil

                    val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    prefs.edit().putLong("snooze_$trimmed", snoozeUntil).apply()

                    val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)

                    val toastText = context.getString(R.string.toast_reminder_snoozed_duration, durationLabel)
                    com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
