package com.bas080.notificationreminders.data

import android.content.Context
import androidx.room.withTransaction
import com.bas080.notificationreminders.receivers.CreateReminderReceiver

object LegacyStorageMigration {
    const val KEY_LEGACY_MIGRATION_DONE = "key_legacy_migration_done"
    private const val PREFS_REMINDERS = "reminders_prefs"

    suspend fun migrateIfNeeded(context: Context, database: PuntDatabase = PuntDatabase.getInstance(context)) {
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_MIGRATION_DONE, false)) {
            return
        }

        val rawSet = prefs.getStringSet("key_reminders_list", null)
            ?: prefs.getStringSet("key_reminders", null)

        val reminderKeys = if (rawSet != null && rawSet.isNotEmpty()) {
            rawSet.toList()
        } else {
            prefs.all.keys.filter { key ->
                !key.startsWith("snooze_") &&
                    !key.startsWith("task_history_") &&
                    !key.startsWith("last_choice_") &&
                    !key.startsWith("count_") &&
                    !key.startsWith("key_")
            }
        }

        if (reminderKeys.isEmpty()) {
            prefs.edit().putBoolean(KEY_LEGACY_MIGRATION_DONE, true).apply()
            return
        }

        val dao = database.reminderDao()
        val entities = reminderKeys.map { key ->
            val cleanKey = key.replace(Regex("(?i)\\s+#done"), "")
            val isDone = key.contains(Regex("(?i)(?<=^|\\s)#done(?=\\s|$)")) || key.endsWith("#done")
            val snoozeUntil = prefs.getLong("snooze_$cleanKey", 0L).takeIf { it > 0L }
            val recurrence = CreateReminderReceiver.parseRecurrenceDuration(cleanKey)?.second

            ReminderEntity(
                text = cleanKey,
                createdAt = (prefs.all[key] as? Long) ?: (System.currentTimeMillis() - 1000L),
                snoozeUntil = snoozeUntil,
                isDone = isDone,
                recurrenceInterval = recurrence
            )
        }

        database.withTransaction {
            for (entity in entities) {
                dao.insert(entity)
            }
        }
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATION_DONE, true).apply()
    }
}
