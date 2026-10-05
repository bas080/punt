package com.bas080.notificationreminders.utils

import org.junit.Assert.assertEquals
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
        ExperimentTracker.resetForTesting()
    }

    @Test
    fun testCreationChannelThresholdSummary() {
        val app = RuntimeEnvironment.getApplication()

        for (i in 1..9) {
            ExperimentTracker.trackCreation(app, "app_input")
        }

        var breadcrumbs = AppLogger.getBreadcrumbs()
        assertTrue("No summary before threshold reached", breadcrumbs.none { it.contains("Experiment:CreationChannel") })

        ExperimentTracker.trackCreation(app, "notification_reply")
        breadcrumbs = AppLogger.getBreadcrumbs()
        assertTrue("Summary logged when threshold (10) reached", breadcrumbs.any {
            it.contains("Experiment:CreationChannel") && it.contains("app_input: 9") && it.contains("notification_reply: 1")
        })
    }

    @Test
    fun testMatcherTierThresholdSummary() {
        val app = RuntimeEnvironment.getApplication()

        for (i in 1..10) {
            val tier = if (i <= 6) "tier_1_and_word" else "tier_2_and_substring"
            ExperimentTracker.trackMatcherTier(app, tier)
        }

        val breadcrumbs = AppLogger.getBreadcrumbs()
        assertTrue("Matcher tier summary logged when threshold reached", breadcrumbs.any {
            it.contains("Experiment:MatcherTier") && it.contains("tier_1_and_word: 6")
        })
    }

    @Test
    fun testSnoozeChoiceThresholdSummary() {
        val app = RuntimeEnvironment.getApplication()

        for (i in 1..10) {
            val choice = if (i <= 7) "preset" else "custom_valid"
            ExperimentTracker.trackSnoozeChoice(app, choice)
        }

        val breadcrumbs = AppLogger.getBreadcrumbs()
        assertTrue("Snooze choice summary logged when threshold reached", breadcrumbs.any {
            it.contains("Experiment:SnoozeChoice") && it.contains("preset: 7")
        })
    }

    @Test
    fun testSearchAndFilterThresholdSummary() {
        val app = RuntimeEnvironment.getApplication()

        for (i in 1..10) {
            ExperimentTracker.trackSearchAndFilter(app, "ALL", 1, 2, true)
        }

        val breadcrumbs = AppLogger.getBreadcrumbs()
        assertTrue("Search and filter summary logged when threshold reached", breadcrumbs.any {
            it.contains("Experiment:SearchAndFilter") && it.contains("state_ALL,tags_1,tokens_2,has_results")
        })
    }

    @Test
    fun testTaskLifecycleThresholdSummary() {
        val app = RuntimeEnvironment.getApplication()

        for (i in 1..10) {
            val action = if (i <= 5) "marked_done" else "deleted"
            ExperimentTracker.trackTaskLifecycle(app, action)
        }

        val breadcrumbs = AppLogger.getBreadcrumbs()
        assertTrue("Task lifecycle summary logged when threshold reached", breadcrumbs.any {
            it.contains("Experiment:TaskLifecycle") && it.contains("marked_done: 5") && it.contains("deleted: 5")
        })
    }
}
