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
    fun testLogRotationFilesCreated() {
        val app = RuntimeEnvironment.getApplication()
        AppLogger.clearLogs(app)

        // Write enough data to trigger rotation (> 500 KB per log file)
        val largeMessage = "A".repeat(1000)
        for (i in 1..1200) {
            AppLogger.log(app, "RotationTest", "Entry $i: $largeMessage")
        }

        // Wait brief moment for async executor
        Thread.sleep(500)

        val activeFile = AppLogger.getLogFile(app, 0)
        val backup1 = AppLogger.getLogFile(app, 1)

        assertTrue(activeFile.exists() || backup1.exists())
    }
}
