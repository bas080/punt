@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.services.ReminderNotificationListenerService

class SnoozeDialogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        const val EXTRA_REMINDER_LIST = "extra_reminder_list"
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT)
        val reminderList = intent.getStringArrayExtra(EXTRA_REMINDER_LIST)

        val targets = when {
            reminderList != null && reminderList.isNotEmpty() -> reminderList.toList()
            !reminderText.isNullOrBlank() -> listOf(reminderText)
            else -> emptyList()
        }

        if (targets.isEmpty()) {
            finish()
            return
        }

        showSnoozeOptionsDialog(targets)
    }

    private fun showSnoozeOptionsDialog(targets: List<String>) {
        val topChoices = ReminderNotificationListenerService.getTopSnoozeChoices(this).map { it.toString() }
        val durations = (topChoices + "Custom...").toTypedArray()

        val title = if (targets.size > 1) "Punt All Reminders" else "Punt Reminder"

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(title)
            .setItems(durations) { _, which ->
                if (which in 0 until durations.size - 1) {
                    applySnoozeDuration(targets, durations[which])
                } else {
                    showCustomSnoozeInputDialog(targets)
                }
            }
            .setOnCancelListener {
                finish()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .show()
    }

    private fun showCustomSnoozeInputDialog(targets: List<String>) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            id = R.id.import_input
            hint = CreateReminderReceiver.getSnoozeCustomHint(this@SnoozeDialogActivity)
            setSingleLine(true)
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(if (targets.size > 1) "Punt All Reminders" else "Punt Reminder")
            .setView(input)
            .setPositiveButton(R.string.snooze) { _, _ ->
                val customInput = input.text.toString().trim()
                applySnoozeDuration(targets, customInput)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private fun applySnoozeDuration(targets: List<String>, durationChoice: String) {
        val parseResult = CreateReminderReceiver.parseSnoozeDuration(durationChoice)
        if (parseResult == null) {
            Toast.makeText(this, R.string.toast_invalid_snooze_input, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val canonicalChoice = CreateReminderReceiver.canonicalizeSnoozeChoice(durationChoice)
        if (canonicalChoice != null) {
            val freqPrefs = getSharedPreferences(PREFS_SNOOZE_FREQ, Context.MODE_PRIVATE)
            val currentCount = freqPrefs.getLong("count_$canonicalChoice", 0L)
            freqPrefs.edit()
                .putLong(canonicalChoice, System.currentTimeMillis())
                .putLong("count_$canonicalChoice", currentCount + 1L)
                .apply()
        }

        val (snoozeMs, durationLabel) = parseResult
        val snoozeUntil = System.currentTimeMillis() + snoozeMs

        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        for (target in targets) {
            val trimmed = target.trim().lowercase()
            ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] = snoozeUntil
            ReminderNotificationListenerService.activePostedReminders.remove(target)
            editor.putLong("snooze_$trimmed", snoozeUntil)
        }
        editor.apply()

        ReminderNotificationListenerService.instance?.showStatusNotification()

        com.bas080.notificationreminders.utils.AppLogger.log(this, "SnoozeDialogActivity", "Punted $durationLabel via notification swipe dialog")
        val toastText = getString(R.string.toast_reminder_snoozed_duration, durationLabel)
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
        finish()
    }
}
