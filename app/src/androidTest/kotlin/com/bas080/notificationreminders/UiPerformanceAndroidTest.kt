package com.bas080.notificationreminders

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class UiPerformanceAndroidTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)

        val activeList = (1..200).map { "Active Task $it #tag${it % 5}" }
        prefs.edit().clear()
            .putStringSet("key_reminders_list", activeList.toSet())
            .commit()
    }

    @Suppress("DEPRECATION")
    private fun prepareActivity(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        try {
            onView(withText("Cancel")).perform(click())
        } catch (_: Exception) {
            // Dialog was not shown
        }
    }

    @Test
    fun testRealtimeSearchUiSnappinessOnEmulator() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            prepareActivity(scenario)

            val elapsedNs = measureNanoTime {
                onView(withId(R.id.search_reminder_input)).perform(typeText("Active Task 10"))
            }

            val elapsedMs = elapsedNs / 1_000_000.0
            println("=== Instrumented Emulator Search UI Benchmark ===")
            println("Realtime search response latency: %.3f ms".format(elapsedMs))

            // Performance Assertion: Realtime search typing on SwiftShader emulator must complete in under 8,000ms
            assertTrue("Search response on emulator was too slow (${elapsedMs}ms)", elapsedMs < 8000.0)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun testAddReminderUiSnappinessOnEmulator() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            prepareActivity(scenario)

            onView(withId(R.id.search_reminder_input)).perform(typeText("Benchmark Reminder Item"))

            val elapsedNs = measureNanoTime {
                onView(withId(R.id.btn_add_reminder)).perform(click())
            }

            val elapsedMs = elapsedNs / 1_000_000.0
            println("=== Instrumented Emulator Add Reminder UI Benchmark ===")
            println("Add reminder UI response latency: %.3f ms".format(elapsedMs))

            // Performance Assertion: Adding reminder on SwiftShader emulator must complete in under 1,500ms
            assertTrue("Adding reminder on emulator was too slow (${elapsedMs}ms)", elapsedMs < 1500.0)
        } finally {
            scenario.close()
        }
    }
}
