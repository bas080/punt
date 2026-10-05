package com.bas080.notificationreminders.utils

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExperimentTrackerTest {

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        AppLogger.clearLogs(app)
        ExperimentTracker.resetForTesting(app)
    }

    @Test
    fun testCrashTrackingAndStartDates() {
        val app = RuntimeEnvironment.getApplication()

        ExperimentTracker.trackCrash(app)
        ExperimentTracker.trackCrash(app)

        val metrics = ExperimentTracker.getFormattedExperimentMetrics(app)
        assertTrue("Metrics should contain Application Crashes", metrics.contains("Application Crashes"))
        assertTrue("Metrics should contain start date", metrics.contains("(since "))
        assertTrue("Metrics should contain total=2 crashes", metrics.contains("total=2"))
    }

    @Test
    fun testEditIntervalRollingMetrics() {
        val app = RuntimeEnvironment.getApplication()

        ExperimentTracker.trackEditInterval(app)
        Thread.sleep(10)
        ExperimentTracker.trackEditInterval(app)

        val metrics = ExperimentTracker.getFormattedExperimentMetrics(app)
        assertTrue("Metrics should contain Edit Intervals with n=1", metrics.contains("Reminder Edit Intervals") && metrics.contains("n=1"))
    }

    @Test
    fun testCreationChannelContinuousTracking() {
        val app = RuntimeEnvironment.getApplication()

        ExperimentTracker.trackCreation(app, "app_input")
        ExperimentTracker.trackCreation(app, "notification_reply")

        val metrics = ExperimentTracker.getFormattedExperimentMetrics(app)
        assertTrue("Metrics should contain Creation Channels with total=2", metrics.contains("Creation Channels") && metrics.contains("total=2") && metrics.contains("app_input: 1"))
    }

    @Test
    fun testMatcherTierContinuousTracking() {
        val app = RuntimeEnvironment.getApplication()

        ExperimentTracker.trackMatcherTier(app, "tier_1_and_word")

        val metrics = ExperimentTracker.getFormattedExperimentMetrics(app)
        assertTrue("Metrics should contain Matcher Tiers with total=1", metrics.contains("Matcher Algorithm Tiers") && metrics.contains("tier_1_and_word: 1"))
    }

    @Test
    fun testSnoozeChoiceContinuousTracking() {
        val app = RuntimeEnvironment.getApplication()

        ExperimentTracker.trackSnoozeChoice(app, "preset")

        val metrics = ExperimentTracker.getFormattedExperimentMetrics(app)
        assertTrue("Metrics should contain Snooze Choices with total=1", metrics.contains("Snooze Choice Patterns") && metrics.contains("preset: 1"))
    }

    @Test
    fun testTaskLifecycleContinuousTracking() {
        val app = RuntimeEnvironment.getApplication()

        ExperimentTracker.trackTaskLifecycle(app, "marked_done")

        val metrics = ExperimentTracker.getFormattedExperimentMetrics(app)
        assertTrue("Metrics should contain Task Lifecycle Events", metrics.contains("Task Lifecycle Events") && metrics.contains("marked_done: 1"))
    }
}
