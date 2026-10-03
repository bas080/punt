@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.PickNotificationActivity
import com.bas080.notificationreminders.R
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.utils.ReminderMatcher
import java.util.concurrent.ConcurrentHashMap

class ReminderNotificationListenerService : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "notification_reminders_status_channel"
        const val MATCH_CHANNEL_ID = "notification_reminders_match_channel"
        const val NOTIFICATION_ID = 1001
        const val SUMMARY_NOTIFICATION_ID = 1000
        const val GROUP_KEY_REMINDERS = "com.bas080.notificationreminders.REMINDER_MATCHES"
        const val ACTION_CREATE_REMINDER = "com.bas080.notificationreminders.ACTION_CREATE_REMINDER"
        const val ACTION_DONE_REMINDER = "com.bas080.notificationreminders.ACTION_DONE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.bas080.notificationreminders.ACTION_SNOOZE_REMINDER"
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val KEY_SNOOZE_REPLY = "key_snooze_reply"
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val COOL_DOWN_MS = 10 * 60 * 1000L // 10 minutes cool-down per notification match

        var instance: ReminderNotificationListenerService? = null
        val lastTriggeredMap = ConcurrentHashMap<String, Long>()
        val activePostedReminders = ConcurrentHashMap.newKeySet<String>()

        fun startService(context: Context) {
            try {
                val intent = Intent(context, ReminderNotificationListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun getNotificationIdForReminder(reminder: String): Int {
            val hash = reminder.trim().lowercase().hashCode() and 0x7fffffff
            return if (hash == NOTIFICATION_ID || hash == SUMMARY_NOTIFICATION_ID) 1002 else if (hash == 0) 1003 else hash
        }

        fun getTopSnoozeChoices(context: Context): Array<CharSequence> {
            val defaultChoices = listOf("15m", "1h", "4h", "24h", "1w")
            val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)
            val allEntries = prefs.all

            val now = System.currentTimeMillis()

            val rawChoices = if (allEntries.isNotEmpty()) {
                allEntries.entries
                    .filter { !it.key.startsWith("count_") }
                    .mapNotNull { entry ->
                        val timestamp = (entry.value as? Number)?.toLong() ?: 0L
                        if (timestamp > 0L) {
                            val choice = entry.key
                            val canonicalChoice = CreateReminderReceiver.canonicalizeSnoozeChoice(choice) ?: choice
                            val rawCount = prefs.getLong("count_$choice", 0L)
                            val count = if (rawCount > 0L) rawCount else 1L
                            Triple(canonicalChoice, timestamp, count)
                        } else null
                    }
            } else emptyList()

            val groupedChoices = rawChoices
                .groupBy { it.first }
                .map { (canonicalChoice, list) ->
                    val maxTimestamp = list.maxOf { it.second }
                    val totalCount = list.sumOf { it.third }
                    Triple(canonicalChoice, maxTimestamp, totalCount)
                }

            val topRecent = groupedChoices
                .sortedByDescending { it.second }
                .take(4)
                .map { it.first }

            val topCommon = groupedChoices
                .sortedWith(compareByDescending<Triple<String, Long, Long>> { it.third }.thenByDescending { it.second })
                .take(6)
                .map { it.first }

            val combined = mutableListOf<String>()
            val addedDurationMs = mutableSetOf<Long>()

            fun tryAdd(choice: String) {
                val canonical = CreateReminderReceiver.canonicalizeSnoozeChoice(choice) ?: choice
                val durationMs = CreateReminderReceiver.parseSnoozeDuration(canonical, now)?.first
                if (!combined.contains(canonical)) {
                    if (durationMs != null && addedDurationMs.contains(durationMs)) {
                        return
                    }
                    combined.add(canonical)
                    if (durationMs != null) {
                        addedDurationMs.add(durationMs)
                    }
                }
            }

            for (choice in topRecent) tryAdd(choice)
            for (choice in topCommon) tryAdd(choice)
            for (defaultChoice in defaultChoices) tryAdd(defaultChoice)

            combined.sortBy { choice ->
                CreateReminderReceiver.parseSnoozeDuration(choice, now)?.first ?: Long.MAX_VALUE
            }

            return Array(combined.size) { combined[it] }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        showStatusNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showStatusNotification()
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        com.bas080.notificationreminders.utils.AppLogger.log(this, "NotificationListener", "Listener connected")
        showStatusNotification()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullContent = "$title $text"

        val sbnKey = sbn.key ?: "${sbn.packageName}_${sbn.id}"
        checkAndTriggerReminderMatch(fullContent, sbnKey)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val sbnKey = sbn.key ?: "${sbn.packageName}_${sbn.id}"
        lastTriggeredMap.keys.removeIf { it.startsWith(sbnKey) }
    }

    private fun checkAndTriggerReminderMatch(fullContent: String, sbnKey: String) {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedReminders = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
        val now = System.currentTimeMillis()

        val commonWordsStr = getString(R.string.common_words)
        val commonWordsSet = ReminderMatcher.parseCommonWords(commonWordsStr)

        for (reminder in savedReminders) {
            if (reminder.contains("#done", ignoreCase = true)) {
                continue
            }

            val trimmed = reminder.trim()
            val lower = trimmed.lowercase()
            val trackingKey = "${sbnKey}_$lower"
            val lastTime = lastTriggeredMap[trackingKey] ?: 0L
            val snoozeUntil = prefs.getLong("snooze_$lower", 0L).let {
                if (it > 0L) it else (lastTriggeredMap["snooze_$lower"] ?: 0L)
            }

            val isSnoozed = (snoozeUntil > 0L && now < snoozeUntil)
            if (isSnoozed) {
                // Snooze overrules notification match; do not show notification while snoozed
                continue
            }

            val isSnoozeExpired = (snoozeUntil > 0L && now >= snoozeUntil)
            if (isSnoozeExpired) {
                prefs.edit().remove("snooze_$lower").apply()
                lastTriggeredMap.remove("snooze_$lower")
            }

            val isWordMatch = ReminderMatcher.matches(reminder, fullContent, commonWordsSet)

            if (isWordMatch) {
                if (now - lastTime >= COOL_DOWN_MS) {
                    lastTriggeredMap[trackingKey] = now
                    postMatchNotification(trimmed, isHighPriority = true)
                    break
                }
            } else if (isSnoozeExpired) {
                lastTriggeredMap[trackingKey] = now
                postMatchNotification(trimmed, isHighPriority = false)
                break
            }
        }
    }

    fun postMatchNotification(matchedReminder: String, @Suppress("UNUSED_PARAMETER") isHighPriority: Boolean = true) {
        try {
            com.bas080.notificationreminders.utils.AppLogger.log(this, "NotificationListener", "Updating persistent status notification for reminder")
            activePostedReminders.add(matchedReminder)
            showStatusNotification()
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val descriptionText = "Status notification for Punt"
            val statusChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = descriptionText
            }

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val matchChannel = NotificationChannel(
                MATCH_CHANNEL_ID,
                "Reminder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for matched reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setSound(soundUri, audioAttributes)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(matchChannel)
        }
    }

    fun showStatusNotification() {
        try {
            if (baseContext == null) return
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val savedReminders = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
            val now = System.currentTimeMillis()
            val activeRemindersList = mutableListOf<String>()
            var activeCount = 0
            var snoozedCount = 0

            for (reminder in savedReminders) {
                if (reminder.contains("#done", ignoreCase = true)) {
                    continue
                }
                val trimmed = reminder.trim()
                val lower = trimmed.lowercase()
                val snoozeUntil = prefs.getLong("snooze_$lower", 0L).let {
                    if (it > 0L) it else (lastTriggeredMap["snooze_$lower"] ?: 0L)
                }
                if (snoozeUntil > now) {
                    snoozedCount++
                } else {
                    activeCount++
                    activeRemindersList.add(trimmed)
                }
            }

            val statusText = if (activeCount == 0 && snoozedCount == 0) {
                getString(R.string.no_active_reminders)
            } else {
                getString(R.string.reminders_summary_combined, activeCount, snoozedCount)
            }

            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(getString(R.string.add_reminder))
                .build()

            val addReminderIntent = Intent(this, CreateReminderReceiver::class.java).apply {
                action = ACTION_CREATE_REMINDER
            }
            val broadcastFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val addReminderPendingIntent: PendingIntent = PendingIntent.getBroadcast(
                this,
                1,
                addReminderIntent,
                broadcastFlags
            )

            val fromTextAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification_reminder,
                getString(R.string.from_text),
                addReminderPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()

            val fromNotifIntent = Intent(this, PickNotificationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fromNotifPendingIntent = PendingIntent.getActivity(
                this,
                2,
                fromNotifIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val fromNotifAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification_reminder,
                getString(R.string.from_notification),
                fromNotifPendingIntent
            ).build()

            val openMainIntent = Intent(this, com.bas080.notificationreminders.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openMainPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openMainIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle(getString(R.string.add_reminder))
                .setContentText(statusText)
                .setContentIntent(openMainPendingIntent)
                .setNumber(activeCount)
                .setOngoing(true)
                .addAction(fromTextAction)
                .addAction(fromNotifAction)
                .setGroup(GROUP_KEY_REMINDERS)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            if (activeRemindersList.isNotEmpty()) {
                val inboxStyle = NotificationCompat.InboxStyle()
                for (item in activeRemindersList) {
                    inboxStyle.addLine(item)
                }
                inboxStyle.setSummaryText(statusText)
                builder.setStyle(inboxStyle)
            }

            val notification = builder.build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }
    }
}
