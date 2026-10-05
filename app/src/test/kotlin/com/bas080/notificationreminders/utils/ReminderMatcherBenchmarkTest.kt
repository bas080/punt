package com.bas080.notificationreminders.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class ReminderMatcherBenchmarkTest {

    private fun generateReminders(count: Int): List<String> {
        val categories = listOf("work", "home", "groceries", "ideas", "urgent", "personal", "finance", "projects")
        val verbs = listOf("Buy", "Review", "Clean", "Call", "Email", "Prepare", "Schedule", "Fix", "Update", "Organize")
        val nouns = listOf("report", "milk", "garage", "john", "meeting", "presentation", "car", "taxes", "laptop", "server")

        return List(count) { index ->
            val verb = verbs[index % verbs.size]
            val noun = nouns[(index / verbs.size) % nouns.size]
            val category = categories[index % categories.size]
            "$verb $noun item $index #$category"
        }
    }

    @Test
    fun testTieredSearchPerformanceUnder5000Items() {
        val reminders = generateReminders(5000)
        val queries = listOf("groceries", "Buy milk", "Review report #work", "urgent", "nonexistentquery123")

        val iterations = 10
        // Warmup
        for (q in queries) {
            ReminderMatcher.filterSearchQueryTiered(reminders, q)
        }

        val timings = mutableListOf<Long>()
        for (i in 1..iterations) {
            for (q in queries) {
                val elapsedNs = measureNanoTime {
                    ReminderMatcher.filterSearchQueryTiered(reminders, q)
                }
                timings.add(elapsedNs)
            }
        }

        val avgMs = (timings.average() / 1_000_000.0)
        val maxMs = (timings.maxOrNull() ?: 0L) / 1_000_000.0

        println("=== ReminderMatcher Benchmark (5,000 items) ===")
        println("Average filtering time: %.3f ms".format(avgMs))
        println("Max filtering time: %.3f ms".format(maxMs))

        // Performance Assertion: Average search filtering across 5,000 items must be < 50ms
        assertTrue("Search filtering took too long (avg: ${avgMs}ms, threshold: 50.0ms)", avgMs < 50.0)
    }

    @Test
    fun testWordExtractionPerformanceUnder10000Strings() {
        val strings = List(10000) { "Call John about the quarter 4 financial report #work #urgent" }

        // Warmup
        strings.take(100).forEach { ReminderMatcher.extractNonCommonWords(it) }

        val elapsedNs = measureNanoTime {
            for (str in strings) {
                ReminderMatcher.extractNonCommonWords(str)
            }
        }

        val totalMs = elapsedNs / 1_000_000.0
        val perItemMs = totalMs / strings.size

        println("=== Word Extraction Benchmark (10,000 strings) ===")
        println("Total time: %.3f ms, Per item: %.5f ms".format(totalMs, perItemMs))

        // Performance Assertion: Extracting words for 10,000 strings must finish in < 200ms total
        assertTrue("Word extraction took too long (${totalMs}ms, threshold: 200.0ms)", totalMs < 200.0)
    }
}
