@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders

import android.app.Application
import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter

class NotificationRemindersApplication : Application() {

    companion object {
        const val PREFS_NAME = "crash_prefs"
        const val KEY_CRASH_TRACE = "key_crash_trace"
    }

    override fun onCreate() {
        super.onCreate()
        com.bas080.notificationreminders.utils.AppLogger.log(this, "Application", "Application initialized")
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = saveCrashTrace(throwable)
            com.bas080.notificationreminders.utils.AppLogger.log(this, "CrashHandler", "Uncaught crash saved: ${throwable.message}")

            try {
                val intent = Intent(this, CrashReportActivity::class.java).apply {
                    putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, stackTrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                val isTest = try { Class.forName("org.robolectric.Robolectric"); true } catch (_: Exception) { false }
                if (!isTest) {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            } catch (_: Exception) {
                try {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:bas080@hotmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, "Punt Crash Report")
                        putExtra(Intent.EXTRA_TEXT, "Punt encountered an error:\n\n```\n$stackTrace\n```")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(Intent.createChooser(emailIntent, "Send Crash Report").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }
        }
    }

    private fun saveCrashTrace(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CRASH_TRACE, stackTrace).commit()
        return stackTrace
    }
}
