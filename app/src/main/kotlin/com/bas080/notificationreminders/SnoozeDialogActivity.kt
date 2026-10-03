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
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val topChoices = ReminderNotificationListenerService.getTopSnoozeChoices(this).map { it.toString() }

        val choicesList = mutableListOf<String>()
        if (targets.size == 1) {
            val cleanTrimmed = targets[0].trim().lowercase()
            val lastChoice = prefs.getString("last_choice_$cleanTrimmed", null)
            if (!lastChoice.isNullOrBlank()) {
                choicesList.add(lastChoice)
                for (choice in topChoices) {
                    if (choice != lastChoice) {
                        choicesList.add(choice)
                    }
                }
            } else {
                choicesList.addAll(topChoices)
            }
        } else {
            choicesList.addAll(topChoices)
        }

        val title = if (targets.size > 1) "Punt All Reminders" else "Punt Reminder"

        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }

        var dialogRef: AlertDialog? = null

        val optionsList = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        for (choice in choicesList) {
            val itemTv = android.widget.TextView(this).apply {
                text = choice
                setTextAppearance(android.R.style.TextAppearance_Medium)
                setTextColor(androidx.core.content.ContextCompat.getColor(this@SnoozeDialogActivity, R.color.text_primary))
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                setOnClickListener {
                    dialogRef?.dismiss()
                    applySnoozeDuration(targets, choice)
                }
            }
            optionsList.addView(itemTv)
        }
        layout.addView(optionsList)

        val input = EditText(this).apply {
            id = R.id.import_input
            hint = CreateReminderReceiver.getSnoozeCustomHint(this@SnoozeDialogActivity)
            setSingleLine(true)
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
        }
        layout.addView(input)

        val dialog = AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(title)
            .setView(layout)
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
            .create()

        dialogRef = dialog
        dialog.show()

        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn?.isEnabled = false

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                positiveBtn?.isEnabled = hasText
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
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
            editor.putString("last_choice_$trimmed", durationChoice)
        }
        editor.apply()

        ReminderNotificationListenerService.instance?.showStatusNotification()

        com.bas080.notificationreminders.utils.AppLogger.log(this, "SnoozeDialogActivity", "Punted $durationLabel via notification swipe dialog")
        val toastText = getString(R.string.toast_reminder_snoozed_duration, durationLabel)
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
        finish()
    }
}
