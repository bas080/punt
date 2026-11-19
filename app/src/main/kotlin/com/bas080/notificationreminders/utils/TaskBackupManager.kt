package com.bas080.notificationreminders.utils

import android.content.Context
import android.os.Environment
import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.data.ReminderEntity
import kotlinx.coroutines.runBlocking
import java.io.File

object TaskBackupManager {

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
            val db = PuntDatabase.getInstance(context)
            val roomReminders = runBlocking { db.reminderDao().getAllReminders() }
            val list = roomReminders.map {
                if (it.isDone) "${it.text} #done" else it.text
            }
            val snoozeMap = roomReminders.mapNotNull { reminder ->
                reminder.snoozeUntil?.let { reminder.text.trim().lowercase() to it }
            }.toMap()

            if (list.isNotEmpty()) {
                val markdown = buildBackupMarkdown(snoozeMap, list)
                if (markdown.isNotBlank()) writeMarkdownToTargets(context, markdown)
            }
        } catch (_: Exception) {
            // Ignore overall failures silently
        }
    }

    private fun buildBackupMarkdown(snoozeMap: Map<String, Long>, remindersList: List<String>): String {
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
            val db = PuntDatabase.getInstance(context)
            val roomCount = runBlocking { db.reminderDao().getAllReminders().size }
            if (roomCount > 0) {
                false
            } else {
                importBackupIfAvailable(context)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun importBackupIfAvailable(context: Context): Boolean {
        val backupContent = readBackupFromCandidates(context) ?: return false
        val imported = MarkdownRemindersUtil.importFromMarkdown(backupContent)
        val hasItems = imported.isNotEmpty()
        if (hasItems) {
            applyImportedReminders(context, imported)
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

    private fun applyImportedReminders(
        context: Context,
        imported: List<ImportedReminder>
    ) {
        val entities = mutableListOf<ReminderEntity>()
        for (item in imported) {
            val isDone = item.isDone || item.text.contains(Regex("(?i)(?<=^|\\s)#done(?=\\s|$)"))
            val cleanText = item.text.replace(Regex("(?i)\\s*#done\\b"), "").trim()
            val snoozeMillis = item.snoozeUntilMillis
            entities.add(
                ReminderEntity(
                    text = cleanText,
                    snoozeUntil = snoozeMillis,
                    isDone = isDone
                )
            )
        }

        val db = PuntDatabase.getInstance(context)
        runBlocking {
            for (entity in entities) {
                db.reminderDao().insert(entity)
            }
        }
    }
}
