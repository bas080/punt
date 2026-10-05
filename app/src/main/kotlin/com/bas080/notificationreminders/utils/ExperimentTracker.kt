package com.bas080.notificationreminders.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExperimentTracker {
    private const val PREFS_NAME = "experiment_prefs"
    private const val MAX_VALID_INTERVAL_SEC = 86400L
    private const val MILLIS_PER_SECOND = 1000L

    @Synchronized
    fun trackCrash(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!prefs.contains("start_date_crashes")) {
            editor.putString("start_date_crashes", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        }
        val currentCount = prefs.getInt("crash_total_count", 0) + 1
        editor.putInt("crash_total_count", currentCount).apply()
    }

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
            val editor = prefs.edit()
            if (!prefs.contains("start_date_edit_interval")) {
                editor.putString("start_date_edit_interval", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            }
            val currentCount = prefs.getInt("edit_interval_count", 0) + 1
            val currentSum = prefs.getLong("edit_interval_sum", 0L) + deltaSec
            val currentMin = prefs.getLong("edit_interval_min", Long.MAX_VALUE).let {
                if (it == Long.MAX_VALUE) deltaSec else kotlin.math.min(it, deltaSec)
            }
            val currentMax = prefs.getLong("edit_interval_max", 0L).let {
                kotlin.math.max(it, deltaSec)
            }

            editor.putInt("edit_interval_count", currentCount)
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!prefs.contains("start_date_creation")) {
            editor.putString("start_date_creation", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        }
        val total = prefs.getInt("creation_total_count", 0) + 1
        val item = prefs.getInt("creation_channel_$channel", 0) + 1
        editor.putInt("creation_total_count", total).putInt("creation_channel_$channel", item).apply()
    }

    @Synchronized
    fun trackMatcherTier(context: Context, tier: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!prefs.contains("start_date_matcher")) {
            editor.putString("start_date_matcher", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        }
        val total = prefs.getInt("matcher_total_count", 0) + 1
        val item = prefs.getInt("matcher_tier_$tier", 0) + 1
        editor.putInt("matcher_total_count", total).putInt("matcher_tier_$tier", item).apply()
    }

    @Synchronized
    fun trackSnoozeChoice(context: Context, choiceType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!prefs.contains("start_date_snooze")) {
            editor.putString("start_date_snooze", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        }
        val total = prefs.getInt("snooze_total_count", 0) + 1
        val item = prefs.getInt("snooze_choice_$choiceType", 0) + 1
        editor.putInt("snooze_total_count", total).putInt("snooze_choice_$choiceType", item).apply()
    }

    @Synchronized
    fun trackTaskLifecycle(context: Context, action: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!prefs.contains("start_date_lifecycle")) {
            editor.putString("start_date_lifecycle", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        }
        val total = prefs.getInt("lifecycle_total_count", 0) + 1
        val item = prefs.getInt("lifecycle_action_$action", 0) + 1
        editor.putInt("lifecycle_total_count", total).putInt("lifecycle_action_$action", item).apply()
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
        val editor = prefs.edit()
        if (!prefs.contains("start_date_search")) {
            editor.putString("start_date_search", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        }
        val resultKey = if (hasResults) "has_results" else "zero_results"
        val queryKey = "state_$filterState,tags_$tagCount,tokens_$tokenCount,$resultKey"

        val total = prefs.getInt("search_total_count", 0) + 1
        val queryCount = prefs.getInt("search_query_$queryKey", 0) + 1

        val savedQueryKeys = prefs.getStringSet("search_query_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        savedQueryKeys.add(queryKey)

        editor.putInt("search_total_count", total)
            .putInt("search_query_$queryKey", queryCount)
            .putStringSet("search_query_keys", savedQueryKeys)
            .apply()
    }

    @Synchronized
    fun getFormattedExperimentMetrics(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sb = StringBuilder()

        val editCount = prefs.getInt("edit_interval_count", 0)
        if (editCount > 0) {
            val date = prefs.getString("start_date_edit_interval", "")
            val sum = prefs.getLong("edit_interval_sum", 0L)
            val min = prefs.getLong("edit_interval_min", 0L)
            val max = prefs.getLong("edit_interval_max", 0L)
            val avgStr = String.format(Locale.US, "%.1f", sum.toDouble() / editCount)
            sb.append("- **Reminder Edit Intervals** (since $date): ")
                .append("n=$editCount, avg ${avgStr}s, min ${min}s, max ${max}s\n")
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
            val date = prefs.getString("start_date_search", "")
            val savedKeys = prefs.getStringSet("search_query_keys", emptySet()) ?: emptySet()
            val parts = savedKeys.mapNotNull { key ->
                val cnt = prefs.getInt("search_query_$key", 0)
                if (cnt > 0) "$key: $cnt" else null
            }
            sb.append("- **Search & Filter Usage** (since $date): ")
                .append("total=$searchTotal (${parts.joinToString("; ")})\n")
        }

        appendCategorySummary(
            prefs, sb, "lifecycle", "Task Lifecycle Events",
            listOf("marked_done", "mark_done_undone", "deleted", "unpunted")
        )

        val crashTotal = prefs.getInt("crash_total_count", 0)
        if (crashTotal > 0) {
            val date = prefs.getString("start_date_crashes", "")
            sb.append("- **Application Crashes** (since $date): total=$crashTotal\n")
        }

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
            val date = prefs.getString("start_date_$prefix", "")
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
            sb.append("- **$label** (since $date): total=$total (${parts.joinToString(", ")})\n")
        }
    }

    @Synchronized
    fun resetForTesting(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
}
