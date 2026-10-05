@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.bas080.notificationreminders.utils.AppLogger

class CrashReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CRASH_TRACE = "extra_crash_trace"
        const val EXTRA_IS_FEEDBACK = "extra_is_feedback"
        private const val REPORT_EMAIL = "bas080@hotmail.com"

        fun getDiagnosticMetadata(context: Context): String {
            val mi = android.app.ActivityManager.MemoryInfo()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            activityManager?.getMemoryInfo(mi)
            val availMemMB = mi.availMem / (1024 * 1024)
            val totalMemMB = mi.totalMem / (1024 * 1024)

            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val availStorageMB = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)

            return "- App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                   "- Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                   "- Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                   "- Free Memory: ${availMemMB} MB / ${totalMemMB} MB\n" +
                   "- Available Storage: ${availStorageMB} MB\n"
        }

        fun buildFormattedReport(
            context: Context,
            crashTrace: String,
            userComment: String,
            includeLogs: Boolean,
            isFeedback: Boolean = false
        ): String {
            val deviceInfo = getDiagnosticMetadata(context)
            return StringBuilder().apply {
                if (isFeedback) {
                    append("## Feedback\n\n")
                    append("### User Comment\n")
                    val commentText = userComment.trim()
                    if (commentText.isNotEmpty()) {
                        append(commentText)
                    } else {
                        append("None provided.")
                    }
                    append("\n\n")

                    append("### Device Info\n")
                    append(deviceInfo)
                    append("\n")
                } else {
                    append("## Crash Report\n\n")
                    append("### User Comment\n")
                    val commentText = userComment.trim()
                    if (commentText.isNotEmpty()) {
                        append(commentText)
                    } else {
                        append("None provided.")
                    }
                    append("\n\n")

                    append("### Device Info\n")
                    append(deviceInfo)
                    append("\n")

                    append("### Stack Trace\n")
                    append("```\n")
                    append(crashTrace)
                    append("\n```\n")
                }

                val expMetrics = com.bas080.notificationreminders.utils.ExperimentTracker.getFormattedExperimentMetrics(context)
                if (expMetrics.isNotBlank()) {
                    append("### Experiment Metrics\n")
                    append(expMetrics)
                    append("\n\n")
                }

                val breadcrumbs = AppLogger.getBreadcrumbs()
                if (breadcrumbs.isNotEmpty()) {
                    append("\n### Breadcrumbs (Recent User Actions)\n")
                    append("```\n")
                    append(breadcrumbs.joinToString("\n"))
                    append("\n```\n")
                }

                if (includeLogs) {
                    val logs = AppLogger.getLogs(context)
                    if (logs.isNotBlank()) {
                        append("\n### Application Logs\n")
                        append("```\n")
                        append(logs)
                        append("\n```\n")
                    }
                }
            }.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val isFeedback = intent.getBooleanExtra(EXTRA_IS_FEEDBACK, false)

        val prefs = getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = intent.getStringExtra(EXTRA_CRASH_TRACE)
            ?: prefs.getString(App.KEY_CRASH_TRACE, null)
            ?: "No stack trace available."

        val txtTitle = findViewById<TextView>(R.id.crash_title)
        val txtMessage = findViewById<TextView>(R.id.crash_message)
        val etUserComment = findViewById<EditText>(R.id.et_user_comment)
        val scrollStackTrace = findViewById<android.view.View>(R.id.scroll_stack_trace)
        val btnDontSend = findViewById<TextView>(R.id.btn_dont_send)
        val btnRestartApp = findViewById<TextView>(R.id.btn_restart_app)

        findViewById<TextView>(R.id.crash_stack_trace).text = crashTrace

        if (isFeedback) {
            txtTitle.setText(R.string.feedback_report_title)
            txtMessage.setText(R.string.feedback_report_description)
            etUserComment.setHint(R.string.feedback_user_comment_hint)
            scrollStackTrace.visibility = android.view.View.GONE
            btnDontSend?.visibility = android.view.View.GONE
            btnRestartApp?.visibility = android.view.View.GONE
        }

        val cbIncludeLogs = findViewById<CheckBox>(R.id.cb_include_logs)

        if (btnDontSend != null) {
            markAsButtonAccessibility(btnDontSend)
            btnDontSend.setOnClickListener {
                prefs.edit().remove(App.KEY_CRASH_TRACE).apply()
                restartApp()
            }
        }

        val btnCopyReport = findViewById<TextView>(R.id.btn_copy_report)
        markAsButtonAccessibility(btnCopyReport)
        btnCopyReport.setOnClickListener {
            val comment = etUserComment.text.toString()
            val includeLogs = cbIncludeLogs.isChecked
            val report = buildFormattedReport(this, crashTrace, comment, includeLogs, isFeedback)

            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(if (isFeedback) "Feedback" else "Crash Report", report)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.toast_report_copied, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.toast_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }

        val btnSendReport = findViewById<TextView>(R.id.btn_send_report)
        markAsButtonAccessibility(btnSendReport)
        btnSendReport.setOnClickListener {
            val comment = etUserComment.text.toString()
            val includeLogs = cbIncludeLogs.isChecked
            val report = buildFormattedReport(this, crashTrace, comment, includeLogs, isFeedback)
            val subject = if (isFeedback) "Punt Feedback" else "Punt Crash Report"
            sendEmail(report, subject)
        }

        if (btnRestartApp != null) {
            markAsButtonAccessibility(btnRestartApp)
            btnRestartApp.setOnClickListener {
                restartApp()
            }
        }
    }

    private fun markAsButtonAccessibility(view: android.view.View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
    }

    private fun sendEmail(reportText: String, subject: String = "Punt Crash Report") {
        val mailtoUri = "mailto:$REPORT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(reportText)}"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(mailtoUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        try {
            startActivity(Intent.createChooser(intent, subject))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_email_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}
