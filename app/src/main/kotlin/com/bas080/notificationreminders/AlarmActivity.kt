@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bas080.notificationreminders.databinding.ActivityAlarmBinding
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger

class AlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"
    }

    private lateinit var binding: ActivityAlarmBinding
    private var reminderText: String = ""
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT) ?: ""
        if (reminderText.isBlank()) {
            finish()
            return
        }

        AppLogger.log(this, "AlarmActivity", "AlarmActivity started for: $reminderText")

        setupUI()
        startAlarmAudio()
        setupSwipeGestures()
    }

    private fun setupUI() {
        val isRecurring = CreateReminderReceiver.parseRecurrenceDuration(reminderText) != null
        binding.txtAlarmReminderText.text = reminderText

        val cleanTrimmed = MainActivity.getCleanTrimmed(reminderText)
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val snoozeUntil = prefs.getLong("snooze_$cleanTrimmed", 0L)
        if (snoozeUntil > 0L) {
            binding.txtAlarmStatus.text = "Punted • until ${MainActivity.formatSnoozeUntil(snoozeUntil)}"
        } else {
            binding.txtAlarmStatus.text = "Alarm Active"
        }

        if (isRecurring) {
            binding.txtGuideRight.text = "Swipe right to punt to next recurring →"
        } else {
            binding.txtGuideRight.text = "Swipe right to mark done →"
        }
    }

    private fun startAlarmAudio() {
        try {
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
        } catch (e: Exception) {
            AppLogger.log(this, "AlarmActivity", "Failed to play alarm audio: ${e.message}")
        }
    }

    private fun stopAlarmAudio() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {}
    }

    private fun setupSwipeGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        return true
                    }
                }
                return false
            }
        })

        binding.cardAlarmReminder.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun onSwipeLeft() {
        stopAlarmAudio()
        showSnoozeOptionsDialog()
    }

    private fun onSwipeRight() {
        stopAlarmAudio()
        markReminderDoneOrAdvanceRecurring()
        finish()
    }

    private fun showSnoozeOptionsDialog() {
        val choicesList = ReminderNotificationListenerService.getTopSnoozeChoices(this, reminderText).map { it.toString() }

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
            val itemTv = TextView(this).apply {
                text = choice
                setTextAppearance(android.R.style.TextAppearance_Medium)
                setTextColor(ContextCompat.getColor(this@AlarmActivity, R.color.text_primary))
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                setOnClickListener {
                    dialogRef?.dismiss()
                    applySnoozeDuration(choice)
                    finish()
                }
            }
            optionsList.addView(itemTv)
        }
        layout.addView(optionsList)

        val input = EditText(this).apply {
            id = R.id.import_input
            hint = CreateReminderReceiver.getSnoozeCustomHint(this@AlarmActivity)
            setSingleLine(true)
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
        }
        layout.addView(input)

        val dialog = AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.snooze_dialog_title)
            .setView(layout)
            .setPositiveButton(R.string.snooze) { _, _ ->
                val customInput = input.text.toString().trim()
                applySnoozeDuration(customInput)
                finish()
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

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                positiveBtn?.isEnabled = hasText
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applySnoozeDuration(durationChoice: String) {
        val parseResult = CreateReminderReceiver.parseSnoozeDuration(durationChoice)
        if (parseResult == null) {
            Toast.makeText(this, R.string.toast_invalid_snooze_input, Toast.LENGTH_SHORT).show()
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
        val cleanTrimmed = MainActivity.getCleanTrimmed(reminderText)

        ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] = snoozeUntil
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putLong("snooze_$cleanTrimmed", snoozeUntil).apply()
        ReminderNotificationListenerService.recordTaskSnoozeChoice(this, reminderText, durationChoice)

        val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(notificationId)

        ReminderNotificationListenerService.instance?.showStatusNotification()
        com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(this)
        com.bas080.notificationreminders.utils.TaskBackupManager.backupTasks(this)

        Toast.makeText(this, getString(R.string.toast_reminder_snoozed_duration, durationLabel), Toast.LENGTH_SHORT).show()
    }

    private fun markReminderDoneOrAdvanceRecurring() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()

        val recurringResult = CreateReminderReceiver.parseRecurrenceDuration(reminderText)
        if (recurringResult != null) {
            val (snoozeMs, durationLabel) = recurringResult
            val snoozeUntil = System.currentTimeMillis() + snoozeMs
            val cleanText = reminderText.replace(Regex("(?i)\\s*#done\\b"), "").trim()
            val cleanTrimmed = cleanText.lowercase()

            if (savedSet.contains(reminderText)) {
                savedSet.remove(reminderText)
                savedSet.add(cleanText)
            } else {
                savedSet.add(cleanText)
            }

            prefs.edit()
                .putStringSet(KEY_REMINDERS, savedSet)
                .putLong("snooze_$cleanTrimmed", snoozeUntil)
                .apply()
            ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] = snoozeUntil

            val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)

            ReminderNotificationListenerService.instance?.showStatusNotification()
            com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(this)
            com.bas080.notificationreminders.utils.TaskBackupManager.backupTasks(this)

            Toast.makeText(this, getString(R.string.toast_reminder_snoozed_duration, durationLabel), Toast.LENGTH_SHORT).show()
        } else {
            if (savedSet.contains(reminderText)) {
                savedSet.remove(reminderText)
                val doneText = if (reminderText.contains("#done", ignoreCase = true)) {
                    reminderText
                } else {
                    "$reminderText #done"
                }
                savedSet.add(doneText)
                prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()
            }

            val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)

            ReminderNotificationListenerService.instance?.showStatusNotification()
            com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(this)
            com.bas080.notificationreminders.utils.TaskBackupManager.backupTasks(this)

            Toast.makeText(this, R.string.toast_reminder_done, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        stopAlarmAudio()
        super.onDestroy()
    }
}
