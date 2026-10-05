package com.bas080.notificationreminders.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

object TaskBackupManager {

    private const val PREFS_REMINDERS = "reminders_prefs"
    private const val KEY_REMINDERS = "key_reminders"
    const val BACKUP_FILE_NAME = "punt_tasks_backup.md"

    fun getDownloadsBackupFile(): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) File(downloadsDir, BACKUP_FILE_NAME) else null
        } catch (_: Exception) {
            null
        }
    }

    fun getFilesBackupFile(context: Context): File {
        return File(context.filesDir, BACKUP_FILE_NAME)
    }

    fun getExternalFilesBackupFile(context: Context): File? {
        return try {
            context.getExternalFilesDir(null)?.let { File(it, BACKUP_FILE_NAME) }
        } catch (_: Exception) {
            null
        }
    }

    fun backupTasks(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val reminderSet = prefs.getStringSet(KEY_REMINDERS, null) ?: emptySet()
            if (reminderSet.isNotEmpty()) {
                val markdown = buildBackupMarkdown(prefs, reminderSet.toList())
                if (markdown.isNotBlank()) {
                    writeMarkdownToTargets(context, markdown)
                }
            }
        } catch (_: Exception) {
            // Ignore overall failures silently
        }
    }

    private fun buildBackupMarkdown(prefs: SharedPreferences, remindersList: List<String>): String {
        val snoozeMap = mutableMapOf<String, Long>()
        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            if (key.startsWith("snooze_") && value is Long) {
                snoozeMap[key.removePrefix("snooze_")] = value
            }
        }
        return MarkdownRemindersUtil.exportToMarkdown(
            reminders = remindersList,
            snoozeMap = snoozeMap,
            includePuntInfo = true
        )
    }

    private fun writeMarkdownToTargets(context: Context, markdown: String) {
        val targets = listOfNotNull(
            getDownloadsBackupFile(),
            getFilesBackupFile(context),
            getExternalFilesBackupFile(context)
        )
        for (file in targets) {
            try {
                file.parentFile?.mkdirs()
                file.writeText(markdown)
            } catch (_: Exception) {
                // Ignore write failures for individual targets
            }
        }
    }

    fun restoreIfEmpty(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val existingSet = prefs.getStringSet(KEY_REMINDERS, null)
            if (!existingSet.isNullOrEmpty()) {
                false
            } else {
                importBackupIfAvailable(context, prefs)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun importBackupIfAvailable(context: Context, prefs: SharedPreferences): Boolean {
        val backupContent = readBackupFromCandidates(context) ?: return false
        val imported = MarkdownRemindersUtil.importFromMarkdown(backupContent)
        val hasItems = imported.isNotEmpty()
        if (hasItems) {
            applyImportedReminders(prefs, imported)
        }
        return hasItems
    }

    private fun readBackupFromCandidates(context: Context): String? {
        val candidateFiles = listOfNotNull(
            getFilesBackupFile(context),
            getExternalFilesBackupFile(context),
            getDownloadsBackupFile(),
            File("/sdcard/Download/$BACKUP_FILE_NAME"),
            File("/sdcard/Android/data/com.bas080.notificationreminders/files/$BACKUP_FILE_NAME")
        )

        for (file in candidateFiles) {
            if (file.exists() && file.canRead()) {
                val text = file.readText()
                if (text.isNotBlank()) {
                    return text
                }
            }
        }
        return null
    }

    private fun applyImportedReminders(prefs: SharedPreferences, imported: List<ImportedReminder>) {
        val editor = prefs.edit()
        val newSet = mutableSetOf<String>()
        for (item in imported) {
            newSet.add(item.text)
            val snoozeMillis = item.snoozeUntilMillis
            if (snoozeMillis != null && snoozeMillis > 0L) {
                val cleanKey = item.text.replace(Regex("(?i)\\s*#done\\b"), "").trim().lowercase()
                editor.putLong("snooze_$cleanKey", snoozeMillis)
            }
        }
        editor.putStringSet(KEY_REMINDERS, newSet)
        editor.apply()
    }
}
