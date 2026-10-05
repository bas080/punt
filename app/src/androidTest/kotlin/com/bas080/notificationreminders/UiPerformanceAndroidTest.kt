package com.bas080.notificationreminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class UiPerformanceAndroidTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)

        val activeList = (1..200).map { "Active Task $it #tag${it % 5}" }
        prefs.edit().clear()
            .putStringSet("key_reminders_list", activeList.toSet())
            .commit()
    }

    @Test
    fun testRealtimeSearchUiSnappinessOnEmulator() {
        val elapsedNs = measureNanoTime {
            onView(withId(R.id.search_reminder_input)).perform(typeText("Active Task 10"))
        }

        val elapsedMs = elapsedNs / 1_000_000.0
        println("=== Instrumented Emulator Search UI Benchmark ===")
        println("Realtime search response latency: %.3f ms".format(elapsedMs))

        // Performance Assertion: Realtime search interaction on emulator must complete in under 500ms
        assertTrue("Search response on emulator was too slow (${elapsedMs}ms)", elapsedMs < 500.0)
    }

    @Test
    fun testAddReminderUiSnappinessOnEmulator() {
        onView(withId(R.id.search_reminder_input)).perform(typeText("Benchmark Reminder Item"))

        val elapsedNs = measureNanoTime {
            onView(withId(R.id.btn_add_reminder)).perform(click())
        }

        val elapsedMs = elapsedNs / 1_000_000.0
        println("=== Instrumented Emulator Add Reminder UI Benchmark ===")
        println("Add reminder UI response latency: %.3f ms".format(elapsedMs))

        // Performance Assertion: Adding reminder on emulator must complete in under 500ms
        assertTrue("Adding reminder on emulator was too slow (${elapsedMs}ms)", elapsedMs < 500.0)
    }
}
