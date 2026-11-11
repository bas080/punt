package com.bas080.notificationreminders

import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.data.ReminderEntity
import com.bas080.notificationreminders.utils.ReminderMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiPerformanceBenchmarkTest {

    private val dao by lazy { PuntDatabase.getInstance(org.robolectric.RuntimeEnvironment.getApplication()).reminderDao() }

    @Before
    fun setUp() {
        runBlocking { dao.deleteAll() }
    }

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
    fun testReminderDaoLoadPerformance() = runBlocking {
        for (index in 1..1000) {
            dao.insert(ReminderEntity(text = "Task $index #tag${index % 5}"))
        }

        val elapsedNs = measureNanoTime { runBlocking { dao.getAllReminders() } }
        val elapsedMs = elapsedNs / 1_000_000.0

        println("=== JVM Room Reminder Load Benchmark (1,000 items) ===")
        println("ReminderDao load latency: %.3f ms".format(elapsedMs))
        assertTrue("ReminderDao load took too long (${elapsedMs}ms)", elapsedMs < 5000.0)
    }

    @Test
    fun testReminderDaoInsertPerformance() = runBlocking {
        for (index in 1..1000) {
            dao.insert(ReminderEntity(text = "Task $index"))
        }
        val elapsedNs = measureNanoTime {
            runBlocking { dao.insert(ReminderEntity(text = "New Added Reminder Item")) }
        }

        val elapsedMs = elapsedNs / 1_000_000.0

        println("=== JVM Room Storage Benchmark (1,000 items) ===")
        println("ReminderDao insert execution time: %.3f ms".format(elapsedMs))

        assertTrue("ReminderDao insert took too long (${elapsedMs}ms)", elapsedMs < 5000.0)
    }
}
