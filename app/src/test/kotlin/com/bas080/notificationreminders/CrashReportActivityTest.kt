package com.bas080.notificationreminders

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReportActivityTest {

    @Test
    fun testCrashReportViewsExistAndShowInitialData() {
        val trace = "java.lang.NullPointerException: Test crash trace"
        val intent = Intent(RuntimeEnvironment.getApplication(), CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, trace)
        }

        val controller = Robolectric.buildActivity(CrashReportActivity::class.java, intent).setup()
        val activity = controller.get()

        val txtStackTrace = activity.findViewById<TextView>(R.id.crash_stack_trace)
        val etUserComment = activity.findViewById<EditText>(R.id.et_user_comment)
        val cbIncludeLogs = activity.findViewById<CheckBox>(R.id.cb_include_logs)
        val btnCopyReport = activity.findViewById<TextView>(R.id.btn_copy_report)
        val btnSendReport = activity.findViewById<TextView>(R.id.btn_send_report)
        val btnRestartApp = activity.findViewById<TextView>(R.id.btn_restart_app)

        assertNotNull(txtStackTrace)
        assertNotNull(etUserComment)
        assertNotNull(cbIncludeLogs)
        assertNotNull(btnCopyReport)
        assertNotNull(btnSendReport)
        assertNotNull(btnRestartApp)

        assertEquals(trace, txtStackTrace.text.toString())
        assertTrue(cbIncludeLogs.isChecked)
    }

    @Test
    fun testCopyReportCopiesFormattedTextToClipboardAndShowsToast() {
        val trace = "java.lang.IllegalStateException: Custom crash trace"
        val intent = Intent(RuntimeEnvironment.getApplication(), CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, trace)
        }

        val controller = Robolectric.buildActivity(CrashReportActivity::class.java, intent).setup()
        val activity = controller.get()

        val etUserComment = activity.findViewById<EditText>(R.id.et_user_comment)
        etUserComment.setText("Crash happened while tapping snooze")

        val btnCopyReport = activity.findViewById<TextView>(R.id.btn_copy_report)
        btnCopyReport.performClick()

        assertEquals("Report copied to clipboard", ShadowToast.getTextOfLatestToast())

        val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboardManager.primaryClip
        assertNotNull("Primary clip should not be null", clip)
        assertTrue("Clip item count should be > 0", clip!!.itemCount > 0)

        val copiedText = clip.getItemAt(0).text.toString()
        assertTrue("Copied text should include crash header", copiedText.contains("## Crash Report"))
        assertTrue("Copied text should include user comment", copiedText.contains("Crash happened while tapping snooze"))
        assertTrue("Copied text should include stack trace", copiedText.contains(trace))
    }

    @Test
    fun testSendReportLaunchesChooserIntent() {
        val trace = "java.lang.RuntimeException: Test exception"
        val intent = Intent(RuntimeEnvironment.getApplication(), CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, trace)
        }

        val controller = Robolectric.buildActivity(CrashReportActivity::class.java, intent).setup()
        val activity = controller.get()

        val etUserComment = activity.findViewById<EditText>(R.id.et_user_comment)
        etUserComment.setText("User comment for send report test")

        val btnSendReport = activity.findViewById<TextView>(R.id.btn_send_report)
        btnSendReport.performClick()

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull("An intent should be started", startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent.action)

        @Suppress("DEPRECATION")
        val targetIntent = startedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("Target chooser intent should exist", targetIntent)
        assertEquals(Intent.ACTION_SENDTO, targetIntent!!.action)
        assertTrue(targetIntent.data.toString().startsWith("mailto:bas080@hotmail.com?subject="))
        assertTrue(targetIntent.data.toString().contains("body="))
        assertEquals("Punt Crash Report", targetIntent.getStringExtra(Intent.EXTRA_SUBJECT))

        val body = targetIntent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        assertTrue(body.contains("User comment for send report test"))
        assertTrue(body.contains(trace))
    }

    @Test
    fun testRestartAppLaunchesMainActivityAndFinishes() {
        val controller = Robolectric.buildActivity(CrashReportActivity::class.java).setup()
        val activity = controller.get()

        val btnRestartApp = activity.findViewById<TextView>(R.id.btn_restart_app)
        btnRestartApp.performClick()

        assertTrue("CrashReportActivity should be finished", activity.isFinishing)

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull("MainActivity intent should be started", startedIntent)
        assertEquals(MainActivity::class.java.name, startedIntent.component?.className)

        val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals("Intent flags should include NEW_TASK and CLEAR_TASK", expectedFlags, startedIntent.flags and expectedFlags)
    }

    @Test
    fun testDontSendRemovesPersistedCrashTraceAndRestartsApp() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(App.KEY_CRASH_TRACE, "Sample crash stack").commit()

        val controller = Robolectric.buildActivity(CrashReportActivity::class.java).setup()
        val activity = controller.get()

        val btnDontSend = activity.findViewById<TextView>(R.id.btn_dont_send)
        assertNotNull(btnDontSend)
        btnDontSend.performClick()

        val savedTrace = prefs.getString(App.KEY_CRASH_TRACE, null)
        org.junit.Assert.assertNull("Crash trace should be removed from prefs when DON'T SEND is clicked", savedTrace)
        assertTrue("CrashReportActivity should be finished", activity.isFinishing)
    }

    @Test
    fun testFeedbackModeUIAndReportFormatting() {
        val intent = Intent(RuntimeEnvironment.getApplication(), CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_IS_FEEDBACK, true)
        }

        val controller = Robolectric.buildActivity(CrashReportActivity::class.java, intent).setup()
        val activity = controller.get()

        val txtTitle = activity.findViewById<TextView>(R.id.crash_title)
        val scrollStackTrace = activity.findViewById<android.view.View>(R.id.scroll_stack_trace)
        val btnDontSend = activity.findViewById<TextView>(R.id.btn_dont_send)
        val btnRestartApp = activity.findViewById<TextView>(R.id.btn_restart_app)

        assertEquals("FEEDBACK", txtTitle.text.toString())
        assertEquals(android.view.View.GONE, scrollStackTrace.visibility)
        assertEquals(android.view.View.GONE, btnDontSend.visibility)
        assertEquals(android.view.View.GONE, btnRestartApp.visibility)

        val report = CrashReportActivity.buildFormattedReport(
            context = activity,
            crashTrace = "Should not appear",
            userComment = "Great app!",
            includeLogs = false,
            isFeedback = true
        )

        assertTrue(report.contains("## Feedback"))
        assertTrue(report.contains("Great app!"))
        org.junit.Assert.assertFalse(report.contains("Should not appear"))

        val btnSendReport = activity.findViewById<TextView>(R.id.btn_send_report)
        btnSendReport.performClick()

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        @Suppress("DEPRECATION")
        val targetIntent = startedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals("Punt Feedback", targetIntent!!.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun testBuildFormattedReportHelper() {
        val context = RuntimeEnvironment.getApplication()
        com.bas080.notificationreminders.utils.AppLogger.log(context, "TestTag", "User navigated to Tasks view")

        val report = CrashReportActivity.buildFormattedReport(
            context = context,
            crashTrace = "SampleTraceException",
            userComment = "  Some comment  ",
            includeLogs = false
        )

        assertTrue(report.contains("## Crash Report"))
        assertTrue(report.contains("Some comment"))
        assertTrue(report.contains("SampleTraceException"))
        assertTrue(report.contains("### Device Info"))
        assertTrue(report.contains("Free Memory:"))
        assertTrue(report.contains("Available Storage:"))
        assertTrue(report.contains("### Breadcrumbs (Recent User Actions)"))
        assertTrue(report.contains("User navigated to Tasks view"))
    }

    @Test
    fun testReportIncludesExperimentDetailsSection() {
        val context = RuntimeEnvironment.getApplication()
        com.bas080.notificationreminders.utils.AppLogger.clearLogs(context)
        com.bas080.notificationreminders.utils.ExperimentTracker.resetForTesting(context)

        com.bas080.notificationreminders.utils.ExperimentTracker.trackCreation(context, "notification_reply")

        val report = CrashReportActivity.buildFormattedReport(
            context = context,
            crashTrace = "TestTrace",
            userComment = "Feedback with experiment data",
            includeLogs = true,
            isFeedback = true
        )

        assertTrue("Report should contain ### Experiment Metrics section", report.contains("### Experiment Metrics"))
        assertTrue("Report should contain Creation Channels summary", report.contains("Creation Channels") && report.contains("notification_reply: 1"))
    }

    @Test
    fun testSendEmailFallbackWhenNoHandlerAvailable() {
        val controller = Robolectric.buildActivity(CrashReportActivity::class.java).setup()
        val activity = controller.get()

        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)

        val btnSendReport = activity.findViewById<TextView>(R.id.btn_send_report)
        btnSendReport.performClick()

        val toastText = ShadowToast.getTextOfLatestToast()
        assertTrue("Fallback toast should be shown when no mail app available", toastText == "No email app found to send report" || toastText == "Report copied to clipboard")
    }

    @Test
    fun testGlobalUncaughtExceptionHandlerSavesCrashTraceToPrefs() {
        val app = RuntimeEnvironment.getApplication() as App
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(handler)

        val testException = java.lang.RuntimeException("Global crash handler test exception")
        try {
            handler!!.uncaughtException(Thread.currentThread(), testException)
        } catch (_: SecurityException) {
            // Expected process exit attempt in test sandbox
        } catch (_: Exception) {
        }

        val prefs = app.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        val savedTrace = prefs.getString(App.KEY_CRASH_TRACE, null)
        assertNotNull("Crash trace should be saved in prefs", savedTrace)
        assertTrue(savedTrace!!.contains("Global crash handler test exception"))
    }
}
