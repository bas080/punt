package com.bas080.notificationreminders

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bas080.notificationreminders.utils.AppLogger
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ScreenshotGeneratorAndroidTest {

    private lateinit var phoneDir: File
    private lateinit var tabletDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val baseDir = context.getExternalFilesDir("screenshots") ?: File(context.filesDir, "screenshots")

        phoneDir = File(baseDir, "phoneScreenshots")
        tabletDir = File(baseDir, "tenInchScreenshots")

        if (!phoneDir.exists()) phoneDir.mkdirs()
        if (!tabletDir.exists()) tabletDir.mkdirs()
    }

    @After
    fun tearDown() {
        PickNotificationActivity.mockActiveNotifications = null
    }

    @Test
    fun generateEmulatorScreenshots() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val args = InstrumentationRegistry.getArguments()
        val shouldGenerate = args.getString("generate.screenshots") == "true"
        if (!shouldGenerate) {
            println("Skipping emulator screenshot generation because generate.screenshots is not set.")
            return
        }

        capture1Overview(context)
        capture2Punted(context)
        capture3FilterDialog(context)
        capture4NotificationPicker(context)
        capture5AboutAndLogs(context)
        capture6KeyboardEditing(context)
    }

    @Suppress("DEPRECATION")
    private fun prepareWindow(scenario: ActivityScenario<*>) {
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
            // Dialog was not present
        }
    }

    private fun capture1Overview(context: Context) {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet(
            "key_reminders_list",
            setOf("Buy groceries #groceries", "Call dentist at 3 PM #health", "Prepare presentation #work", "Review quarterly goals #work")
        ).commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            prepareWindow(scenario)
            scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    saveViewScreenshots(activity.window.decorView, "1")
                }
            }
        } finally {
            scenario.close()
        }
    }

    private fun capture2Punted(context: Context) {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Call dentist at 3 PM #health", "Prepare presentation #work", "Pay electricity bill #home", "Review budget #finance"))
            .putLong("snooze_pay electricity bill #home", now + 2 * 3600 * 1000L)
            .putLong("snooze_review budget #finance", now + 24 * 3600 * 1000L)
            .commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            prepareWindow(scenario)
            scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    saveViewScreenshots(activity.window.decorView, "2")
                }
            }
        } finally {
            scenario.close()
        }
    }

    private fun capture3FilterDialog(context: Context) {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet(
            "key_reminders_list",
            setOf("Buy groceries #groceries", "Call dentist #health", "Pay electric bill #home", "Prepare slides #work", "Review budget #finance")
        ).commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            prepareWindow(scenario)
            onView(withId(R.id.btn_tags_filter)).perform(click())
            scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    saveViewScreenshots(activity.window.decorView, "3")
                }
            }
        } finally {
            scenario.close()
        }
    }

    private fun capture4NotificationPicker(context: Context) {
        val sbn1 = createMockSbn(context, "com.whatsapp", "WhatsApp", "Meeting with design team at 2 PM")
        val sbn2 = createMockSbn(context, "com.android.calendar", "Calendar", "Doctor's Appointment at 4 PM")
        val sbn3 = createMockSbn(context, "com.google.android.gm", "Gmail", "Flight confirmation for Friday")
        val sbn4 = createMockSbn(context, "com.slack", "Slack", "Code review request for pull request")

        PickNotificationActivity.mockActiveNotifications = arrayOf(sbn1, sbn2, sbn3, sbn4)

        val scenario = ActivityScenario.launch(PickNotificationActivity::class.java)
        try {
            prepareWindow(scenario)
            scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    saveViewScreenshots(activity.window.decorView, "4")
                }
            }
        } finally {
            scenario.close()
        }
    }

    private fun capture5AboutAndLogs(context: Context) {
        AppLogger.clearLogs(context)
        AppLogger.log(context, "Application", "Application started successfully")
        AppLogger.log(context, "NotificationListener", "Listener connected and monitoring notifications")
        AppLogger.log(context, "Matcher", "Matched reminder: 'Buy groceries'")
        AppLogger.log(context, "MainActivity", "Punted reminder for 2 hours")

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            prepareWindow(scenario)
            onView(withId(R.id.btn_nav_about)).perform(click())
            scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    saveViewScreenshots(activity.window.decorView, "5")
                }
            }
        } finally {
            scenario.close()
        }
    }

    private fun capture6KeyboardEditing(context: Context) {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet(
            "key_reminders_list",
            setOf("Buy groceries #groceries", "Call dentist at 3 PM #health", "Prepare presentation #work")
        ).commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            prepareWindow(scenario)
            onView(withId(R.id.search_reminder_input)).perform(typeText("Buy groceries and milk #groceries"))
            scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    saveViewScreenshots(activity.window.decorView, "6")
                }
            }
        } finally {
            scenario.close()
        }
    }

    private fun saveViewScreenshots(view: View, name: String) {
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        val phoneBitmap = Bitmap.createScaledBitmap(bitmap, 375, 667, true)
        val phoneFile = File(phoneDir, "$name.png")
        FileOutputStream(phoneFile).use { out ->
            phoneBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val tabletBitmap = Bitmap.createScaledBitmap(bitmap, 1024, 768, true)
        val tabletFile = File(tabletDir, "$name.png")
        FileOutputStream(tabletFile).use { out ->
            tabletBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue(phoneFile.exists() && phoneFile.length() > 0)
        assertTrue(tabletFile.exists() && tabletFile.length() > 0)
    }

    private fun createMockSbn(context: Context, packageName: String, title: String, text: String): StatusBarNotification {
        val extras = Bundle().apply {
            putCharSequence("android.title", title)
            putCharSequence("android.text", text)
        }
        @Suppress("DEPRECATION")
        val notification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        return StatusBarNotification(
            packageName,
            packageName,
            1,
            "tag",
            1000,
            1000,
            1,
            notification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )
    }
}
