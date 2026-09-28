@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_FILE_SIZE_BYTES = 100 * 1024 // 100 KB max log size
    private const val MAX_BREADCRUMBS = 50

    private val breadcrumbs = java.util.ArrayDeque<String>()

    @Synchronized
    fun log(context: Context, tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logEntry = "$timestamp [$tag]: $message"

        if (breadcrumbs.size >= MAX_BREADCRUMBS) {
            breadcrumbs.removeFirst()
        }
        breadcrumbs.addLast(logEntry)

        try {
            val file = getLogFile(context)
            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                file.delete()
            }
            file.appendText("$logEntry\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun getBreadcrumbs(maxCount: Int = MAX_BREADCRUMBS): List<String> {
        val count = maxCount.coerceAtMost(breadcrumbs.size)
        return breadcrumbs.toList().takeLast(count)
    }

    fun getLogs(context: Context, maxLines: Int = 50): String {
        return try {
            val file = getLogFile(context)
            if (file.exists()) {
                val lines = file.readLines()
                val recentLines = lines.takeLast(maxLines)
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
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }
}
