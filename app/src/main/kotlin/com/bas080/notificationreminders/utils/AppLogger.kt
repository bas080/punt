package com.bas080.notificationreminders.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLogger {
    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_SINGLE_FILE_SIZE_BYTES = 500 * 1024 // 500 KB per file
    private const val MAX_BACKUP_FILES = 3 // Up to 3 backup files (total ~2 MB)
    private const val MAX_BREADCRUMBS = 50
    private const val DEFAULT_MAX_LINES = 50

    private val breadcrumbs = java.util.ArrayDeque<String>()
    private val logExecutor = Executors.newSingleThreadExecutor()

    @Synchronized
    fun log(context: Context, tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logEntry = "$timestamp [$tag]: $message"

        if (breadcrumbs.size >= MAX_BREADCRUMBS) {
            breadcrumbs.removeFirst()
        }
        breadcrumbs.addLast(logEntry)

        val appContext = context.applicationContext
        logExecutor.execute {
            try {
                val activeFile = getLogFile(appContext, 0)
                if (activeFile.exists() && activeFile.length() >= MAX_SINGLE_FILE_SIZE_BYTES) {
                    rotateLogFiles(appContext)
                }
                activeFile.appendText("$logEntry\n")
            } catch (_: Exception) {
            }
        }
    }

    private fun rotateLogFiles(context: Context) {
        val oldestBackup = getLogFile(context, MAX_BACKUP_FILES)
        if (oldestBackup.exists()) {
            oldestBackup.delete()
        }

        for (i in (MAX_BACKUP_FILES - 1) downTo 1) {
            val src = getLogFile(context, i)
            if (src.exists()) {
                val dst = getLogFile(context, i + 1)
                src.renameTo(dst)
            }
        }

        val active = getLogFile(context, 0)
        if (active.exists()) {
            val firstBackup = getLogFile(context, 1)
            active.renameTo(firstBackup)
        }
    }

    @Synchronized
    fun getBreadcrumbs(maxCount: Int = MAX_BREADCRUMBS): List<String> {
        val count = maxCount.coerceAtMost(breadcrumbs.size)
        return breadcrumbs.toList().takeLast(count)
    }

    fun getLogs(context: Context, maxLines: Int = DEFAULT_MAX_LINES): String {
        return try {
            val allLines = mutableListOf<String>()
            for (i in MAX_BACKUP_FILES downTo 0) {
                val file = getLogFile(context, i)
                if (file.exists()) {
                    allLines.addAll(file.readLines())
                }
            }
            if (allLines.isNotEmpty()) {
                val recentLines = allLines.takeLast(maxLines)
                recentLines.joinToString("\n")
            } else {
                "No logs recorded."
            }
        } catch (_: Exception) {
            "Unable to read logs."
        }
    }

    @Synchronized
    fun clearLogs(context: Context) {
        breadcrumbs.clear()
        val appContext = context.applicationContext
        logExecutor.execute {
            try {
                for (i in 0..MAX_BACKUP_FILES) {
                    val file = getLogFile(appContext, i)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun getLogFile(context: Context, backupIndex: Int = 0): File {
        val fileName = if (backupIndex == 0) LOG_FILE_NAME else "app_logs.$backupIndex.txt"
        return File(context.filesDir, fileName)
    }
}
