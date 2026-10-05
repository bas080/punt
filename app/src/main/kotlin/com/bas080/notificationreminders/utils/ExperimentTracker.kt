package com.bas080.notificationreminders.utils

import android.content.Context

object ExperimentTracker {
    private const val PREFS_NAME = "experiment_prefs"
    private const val MAX_VALID_INTERVAL_SEC = 86400L
    private const val MILLIS_PER_SECOND = 1000L

    @Synchronized
    fun trackEditInterval(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastTimestamp = prefs.getLong("last_edit_timestamp", 0L)

        if (lastTimestamp <= 0L) {
            prefs.edit().putLong("last_edit_timestamp", now).apply()
            return
        }

        val deltaSec = (now - lastTimestamp) / MILLIS_PER_SECOND
        if (deltaSec in 0..MAX_VALID_INTERVAL_SEC) {
            val currentCount = prefs.getInt("edit_interval_count", 0) + 1
            val currentSum = prefs.getLong("edit_interval_sum", 0L) + deltaSec
            val currentMin = prefs.getLong("edit_interval_min", Long.MAX_VALUE).let {
                if (it == Long.MAX_VALUE) deltaSec else kotlin.math.min(it, deltaSec)
            }
            val currentMax = prefs.getLong("edit_interval_max", 0L).let {
                kotlin.math.max(it, deltaSec)
            }

            prefs.edit()
                .putInt("edit_interval_count", currentCount)
                .putLong("edit_interval_sum", currentSum)
                .putLong("edit_interval_min", currentMin)
                .putLong("edit_interval_max", currentMax)
                .putLong("last_edit_timestamp", now)
                .apply()
        } else {
            prefs.edit().putLong("last_edit_timestamp", now).apply()
        }
    }

    @Synchronized
    fun trackCreation(context: Context, channel: String) {
        incrementCategoryCount(context, "creation_total_count", "creation_channel_$channel")
    }

    @Synchronized
    fun trackMatcherTier(context: Context, tier: String) {
        incrementCategoryCount(context, "matcher_total_count", "matcher_tier_$tier")
    }

    @Synchronized
    fun trackSnoozeChoice(context: Context, choiceType: String) {
        incrementCategoryCount(context, "snooze_total_count", "snooze_choice_$choiceType")
    }

    @Synchronized
    fun trackTaskLifecycle(context: Context, action: String) {
        incrementCategoryCount(context, "lifecycle_total_count", "lifecycle_action_$action")
    }

    private fun incrementCategoryCount(context: Context, totalKey: String, itemKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val total = prefs.getInt(totalKey, 0) + 1
        val item = prefs.getInt(itemKey, 0) + 1
        prefs.edit().putInt(totalKey, total).putInt(itemKey, item).apply()
    }

    @Synchronized
    fun trackSearchAndFilter(
        context: Context,
        filterState: String,
        tagCount: Int,
        tokenCount: Int,
        hasResults: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val resultKey = if (hasResults) "has_results" else "zero_results"
        val queryKey = "state_$filterState,tags_$tagCount,tokens_$tokenCount,$resultKey"

        val total = prefs.getInt("search_total_count", 0) + 1
        val queryCount = prefs.getInt("search_query_$queryKey", 0) + 1

        val savedQueryKeys = prefs.getStringSet("search_query_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        savedQueryKeys.add(queryKey)

        prefs.edit()
            .putInt("search_total_count", total)
            .putInt("search_query_$queryKey", queryCount)
            .putStringSet("search_query_keys", savedQueryKeys)
            .apply()
    }

    @Synchronized
    fun getFormattedExperimentMetrics(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sb = StringBuilder()

        val count = prefs.getInt("edit_interval_count", 0)
        if (count > 0) {
            val sum = prefs.getLong("edit_interval_sum", 0L)
            val min = prefs.getLong("edit_interval_min", 0L)
            val max = prefs.getLong("edit_interval_max", 0L)
            val avg = sum.toDouble() / count
            val avgStr = String.format(java.util.Locale.US, "%.1f", avg)
            sb.append("- **Reminder Edit Intervals**: n=$count, avg ${avgStr}s, min ${min}s, max ${max}s\n")
        }

        appendCategorySummary(
            prefs, sb, "creation", "Creation Channels",
            listOf("app_input", "notification_reply", "text_selection", "create_intent", "pick_notification")
        )
        appendCategorySummary(
            prefs, sb, "matcher", "Matcher Algorithm Tiers",
            listOf("tier_1_and_word", "tier_2_and_substring", "tier_3_or_word", "tier_4_or_substring", "no_match")
        )
        appendCategorySummary(
            prefs, sb, "snooze", "Snooze Choice Patterns",
            listOf("preset", "custom_valid", "custom_invalid")
        )

        val searchTotal = prefs.getInt("search_total_count", 0)
        if (searchTotal > 0) {
            val savedKeys = prefs.getStringSet("search_query_keys", emptySet()) ?: emptySet()
            val parts = savedKeys.mapNotNull { key ->
                val cnt = prefs.getInt("search_query_$key", 0)
                if (cnt > 0) "$key: $cnt" else null
            }
            sb.append("- **Search & Filter Usage**: total=$searchTotal (${parts.joinToString("; ")})\n")
        }

        appendCategorySummary(
            prefs, sb, "lifecycle", "Task Lifecycle Events",
            listOf("marked_done", "mark_done_undone", "deleted", "unpunted")
        )

        val result = sb.toString().trim()
        return if (result.isNotEmpty()) result else "No experiment metrics recorded yet."
    }

    private fun appendCategorySummary(
        prefs: android.content.SharedPreferences,
        sb: StringBuilder,
        prefix: String,
        label: String,
        items: List<String>
    ) {
        val total = prefs.getInt("${prefix}_total_count", 0)
        if (total > 0) {
            val actualPrefix = when (prefix) {
                "creation" -> "creation_channel_"
                "matcher" -> "matcher_tier_"
                "snooze" -> "snooze_choice_"
                else -> "lifecycle_action_"
            }
            val parts = items.mapNotNull { item ->
                val cnt = prefs.getInt("$actualPrefix$item", 0)
                if (cnt > 0) "$item: $cnt" else null
            }
            sb.append("- **$label**: total=$total (${parts.joinToString(", ")})\n")
        }
    }

    @Synchronized
    fun resetForTesting(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
}
