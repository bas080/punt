package com.bas080.notificationreminders.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLoggerTest {

    @Test
    fun testAppLoggerObjectNotNull() {
        assertNotNull(AppLogger)
    }

    @Test
    fun testBreadcrumbsRollingQueueCapacity() {
        val app = RuntimeEnvironment.getApplication()
        AppLogger.clearLogs(app)

        for (i in 1..60) {
            AppLogger.log(app, "TestTag", "Event #$i")
        }

        val breadcrumbs = AppLogger.getBreadcrumbs(50)
        assertEquals(50, breadcrumbs.size)
        assertTrue(breadcrumbs.first().contains("Event #11"))
        assertTrue(breadcrumbs.last().contains("Event #60"))
    }

    @Test
    fun testLogFileCreationAndClear() {
        val app = RuntimeEnvironment.getApplication()
        AppLogger.clearLogs(app)

        AppLogger.log(app, "LogTest", "Test log message")
        Thread.sleep(100)

        val logFile = AppLogger.getLogFile(app)
        assertTrue("Log file should exist after logging", logFile.exists())

        AppLogger.clearLogs(app)
        Thread.sleep(100)
        assertTrue("Log file should be deleted after clearLogs", !logFile.exists())
    }
}
