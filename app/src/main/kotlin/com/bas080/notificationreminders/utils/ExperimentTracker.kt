package com.bas080.notificationreminders.utils

import android.content.Context

object ExperimentTracker {
    const val THRESHOLD_EDIT_INTERVALS = 10
    const val THRESHOLD_CREATION_CHANNELS = 10
    const val THRESHOLD_MATCHER_TIERS = 10
    const val THRESHOLD_SNOOZE_CHOICES = 10
    const val THRESHOLD_SEARCH_FILTER = 10
    const val THRESHOLD_TASK_LIFECYCLE = 10

    private const val MAX_VALID_INTERVAL_SEC = 86400L
    private const val MILLIS_PER_SECOND = 1000L

    private var lastEditTimestampMs = 0L
    private val editIntervals = mutableListOf<Long>()

    private val creationChannelCounts = mutableMapOf<String, Int>()
    private var creationTotalCount = 0

    private val matcherTierCounts = mutableMapOf<String, Int>()
    private var matcherTotalCount = 0

    private val snoozeChoiceCounts = mutableMapOf<String, Int>()
    private var snoozeTotalCount = 0

    private val searchFilterCounts = mutableMapOf<String, Int>()
    private var searchFilterTotalCount = 0

    private val taskLifecycleCounts = mutableMapOf<String, Int>()
    private var taskLifecycleTotalCount = 0

    @Synchronized
    fun trackEditInterval(context: Context) {
        val now = System.currentTimeMillis()
        if (lastEditTimestampMs > 0L) {
            val deltaSeconds = (now - lastEditTimestampMs) / MILLIS_PER_SECOND
            if (deltaSeconds in 0..MAX_VALID_INTERVAL_SEC) {
                editIntervals.add(deltaSeconds)
            }
        }
        lastEditTimestampMs = now

        if (editIntervals.size >= THRESHOLD_EDIT_INTERVALS) {
            val avg = editIntervals.average()
            val min = editIntervals.minOrNull() ?: 0L
            val max = editIntervals.maxOrNull() ?: 0L
            val count = editIntervals.size
            editIntervals.clear()

            val avgStr = String.format(java.util.Locale.US, "%.1f", avg)
            val msg = "Reminder edit intervals summary over $count edits: avg ${avgStr}s, min ${min}s, max ${max}s"
            AppLogger.log(context, "Experiment:EditInterval", msg)
        }
    }

    @Synchronized
    fun trackCreation(context: Context, channel: String) {
        creationChannelCounts[channel] = (creationChannelCounts[channel] ?: 0) + 1
        creationTotalCount++

        if (creationTotalCount >= THRESHOLD_CREATION_CHANNELS) {
            val summary = creationChannelCounts.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            val msg = "Creation channels summary over $creationTotalCount items ($summary)"
            AppLogger.log(context, "Experiment:CreationChannel", msg)
            creationTotalCount = 0
            creationChannelCounts.clear()
        }
    }

    @Synchronized
    fun trackMatcherTier(context: Context, tier: String) {
        matcherTierCounts[tier] = (matcherTierCounts[tier] ?: 0) + 1
        matcherTotalCount++

        if (matcherTotalCount >= THRESHOLD_MATCHER_TIERS) {
            val summary = matcherTierCounts.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            val msg = "Matcher tiers summary over $matcherTotalCount evaluations ($summary)"
            AppLogger.log(context, "Experiment:MatcherTier", msg)
            matcherTotalCount = 0
            matcherTierCounts.clear()
        }
    }

    @Synchronized
    fun trackSnoozeChoice(context: Context, choiceType: String) {
        snoozeChoiceCounts[choiceType] = (snoozeChoiceCounts[choiceType] ?: 0) + 1
        snoozeTotalCount++

        if (snoozeTotalCount >= THRESHOLD_SNOOZE_CHOICES) {
            val summary = snoozeChoiceCounts.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            val msg = "Snooze choices summary over $snoozeTotalCount actions ($summary)"
            AppLogger.log(context, "Experiment:SnoozeChoice", msg)
            snoozeTotalCount = 0
            snoozeChoiceCounts.clear()
        }
    }

    @Synchronized
    fun trackSearchAndFilter(
        context: Context,
        filterState: String,
        tagCount: Int,
        tokenCount: Int,
        hasResults: Boolean
    ) {
        val resultKey = if (hasResults) "has_results" else "zero_results"
        val key = "state_$filterState,tags_$tagCount,tokens_$tokenCount,$resultKey"
        searchFilterCounts[key] = (searchFilterCounts[key] ?: 0) + 1
        searchFilterTotalCount++

        if (searchFilterTotalCount >= THRESHOLD_SEARCH_FILTER) {
            val summary = searchFilterCounts.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            val msg = "Search/filter summary over $searchFilterTotalCount queries ($summary)"
            AppLogger.log(context, "Experiment:SearchAndFilter", msg)
            searchFilterTotalCount = 0
            searchFilterCounts.clear()
        }
    }

    @Synchronized
    fun trackTaskLifecycle(context: Context, action: String) {
        taskLifecycleCounts[action] = (taskLifecycleCounts[action] ?: 0) + 1
        taskLifecycleTotalCount++

        if (taskLifecycleTotalCount >= THRESHOLD_TASK_LIFECYCLE) {
            val summary = taskLifecycleCounts.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            val msg = "Task lifecycle summary over $taskLifecycleTotalCount actions ($summary)"
            AppLogger.log(context, "Experiment:TaskLifecycle", msg)
            taskLifecycleTotalCount = 0
            taskLifecycleCounts.clear()
        }
    }

    @Synchronized
    fun resetForTesting() {
        lastEditTimestampMs = 0L
        editIntervals.clear()
        creationChannelCounts.clear()
        creationTotalCount = 0
        matcherTierCounts.clear()
        matcherTotalCount = 0
        snoozeChoiceCounts.clear()
        snoozeTotalCount = 0
        searchFilterCounts.clear()
        searchFilterTotalCount = 0
        taskLifecycleCounts.clear()
        taskLifecycleTotalCount = 0
    }
}
