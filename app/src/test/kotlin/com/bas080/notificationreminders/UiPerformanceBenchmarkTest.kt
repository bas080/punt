package com.bas080.notificationreminders

import android.content.Context
import com.bas080.notificationreminders.utils.ReminderMatcher
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiPerformanceBenchmarkTest {

    private fun generateRemindersList(activeCount: Int, snoozedCount: Int): Pair<List<String>, Map<String, Long>> {
        val activeList = (1..activeCount).map { "Active Task $it #tag${it % 5}" }
        val snoozedList = (1..snoozedCount).map { "Snoozed Task $it #tag${it % 5}" }

        val futureTime = System.currentTimeMillis() + 3600000L
        val snoozeMap = snoozedList.associate { item ->
            item.lowercase() to futureTime
        }

        return (activeList + snoozedList) to snoozeMap
    }

    @Test
    fun testSearchFilteringUiStateUpdateLatency() {
        val (allReminders, snoozeMap) = generateRemindersList(1000, 1000)

        val queries = listOf("Active", "Task 10", "#tag2", "Snoozed", "nonexistentquery")

        // Warmup JIT
        for (query in queries) {
            val filtered = ReminderMatcher.filterSearchQueryTiered(allReminders, query)
            filtered.partition { item ->
                val clean = item.trim().lowercase()
                (snoozeMap[clean] ?: 0L) <= System.currentTimeMillis()
            }
        }

        val timings = mutableListOf<Long>()
        for (query in queries) {
            val elapsedNs = measureNanoTime {
                val filtered = ReminderMatcher.filterSearchQueryTiered(allReminders, query)
                filtered.partition { item ->
                    val clean = item.trim().lowercase()
                    (snoozeMap[clean] ?: 0L) <= System.currentTimeMillis()
                }
            }
            timings.add(elapsedNs)
        }

        val avgMs = (timings.average() / 1_000_000.0)
        val maxMs = (timings.maxOrNull() ?: 0L) / 1_000_000.0

        println("=== JVM UI State Search Filtering Benchmark (2,000 items) ===")
        println("Avg Search State Update Latency: %.3f ms, Max: %.3f ms".format(avgMs, maxMs))

        // Performance Assertion: State update and filtering on 2,000 items must be < 30ms
        assertTrue("UI state search update was too slow (${avgMs}ms)", avgMs < 30.0)
    }

    @Test
    fun testActivityInitializationAndStateLoadPerformance() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)

        val activeList = (1..1000).map { "Task $it #tag${it % 5}" }
        prefs.edit().clear().putStringSet("key_reminders_list", activeList.toSet()).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)

        val elapsedNs = measureNanoTime {
            controller.setup()
        }

        val elapsedMs = elapsedNs / 1_000_000.0

        println("=== JVM Activity Launch & List Load Benchmark (1,000 items) ===")
        println("Activity initialization execution latency: %.3f ms".format(elapsedMs))

        // Performance Assertion: Activity setup with 1,000 items must complete in < 600ms
        assertTrue("Activity setup took too long (${elapsedMs}ms)", elapsedMs < 600.0)
    }

    @Test
    fun testAddAndSharedPreferencesCommitPerformance() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)

        val existingSet = (1..1000).map { "Task $it" }.toSet()
        prefs.edit().clear().putStringSet("key_reminders_list", existingSet).commit()

        val elapsedNs = measureNanoTime {
            val updatedSet = existingSet + "New Added Reminder Item"
            prefs.edit().putStringSet("key_reminders_list", updatedSet).commit()
        }

        val elapsedMs = elapsedNs / 1_000_000.0

        println("=== JVM Storage Persistence Benchmark (1,000 items) ===")
        println("Preferences commit execution time: %.3f ms".format(elapsedMs))

        // Performance Assertion: Persisting 1,000 items to SharedPreferences must execute in < 30ms
        assertTrue("Storage commit took too long (${elapsedMs}ms)", elapsedMs < 30.0)
    }
}
