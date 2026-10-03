package com.bas080.notificationreminders

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTest {

    @Test
    fun testGlobalCrashHandlerSavesTraceAndLaunchesCrashReportActivity() {
        val application = RuntimeEnvironment.getApplication() as App
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull("Global uncaught exception handler should be registered", defaultHandler)

        val testException = RuntimeException("Test application uncaught exception")
        defaultHandler!!.uncaughtException(Thread.currentThread(), testException)

        val prefs = application.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        val savedTrace = prefs.getString(App.KEY_CRASH_TRACE, null)
        assertNotNull("Crash trace should be saved in preferences", savedTrace)
        assertTrue("Saved trace should contain exception message", savedTrace!!.contains("Test application uncaught exception"))

        val nextStartedActivity = shadowOf(application).nextStartedActivity
        assertNotNull("CrashReportActivity intent should be started", nextStartedActivity)
        assertEquals(CrashReportActivity::class.java.name, nextStartedActivity.component?.className)
        assertEquals(savedTrace, nextStartedActivity.getStringExtra(CrashReportActivity.EXTRA_CRASH_TRACE))
    }
}
