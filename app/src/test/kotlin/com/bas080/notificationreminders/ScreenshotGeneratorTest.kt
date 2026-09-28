package com.bas080.notificationreminders

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import com.bas080.notificationreminders.utils.AppLogger
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGeneratorTest {

    private lateinit var phoneScreenshotsDir: File
    private lateinit var tabletScreenshotsDir: File

    @Before
    fun setUp() {
        var baseDir = File("fastlane/metadata/android/en-US/images")
        if (!baseDir.exists()) {
            baseDir = File("../fastlane/metadata/android/en-US/images")
        }

        phoneScreenshotsDir = File(baseDir, "phoneScreenshots")
        if (!phoneScreenshotsDir.exists()) {
            phoneScreenshotsDir.mkdirs()
        }

        tabletScreenshotsDir = File(baseDir, "tenInchScreenshots")
        if (!tabletScreenshotsDir.exists()) {
            tabletScreenshotsDir.mkdirs()
        }

        assertTrue("phoneScreenshots directory must exist", phoneScreenshotsDir.exists() && phoneScreenshotsDir.isDirectory)
        assertTrue("tenInchScreenshots directory must exist", tabletScreenshotsDir.exists() && tabletScreenshotsDir.isDirectory)
    }

    @After
    fun tearDown() {
        PickNotificationActivity.mockActiveNotifications = null
    }

    @Test
    fun captureFeatureScreenshots() {
        val shouldGenerate = System.getenv("GENERATE_SCREENSHOTS") == "true" ||
                System.getProperty("generate.screenshots") == "true"
        if (!shouldGenerate) {
            println("Skipping Fastlane screenshot generation because GENERATE_SCREENSHOTS is not set.")
            return
        }
        captureScreenshot1Overview()
        captureScreenshot2Punted()
        captureScreenshot3FilterDialog()
        captureScreenshot4NotificationDrawer()
        captureScreenshot5AboutAndLogs()
        captureScreenshot6KeyboardEditing()
    }

    private fun captureScreenshot1Overview() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet(
            "key_reminders_list",
            setOf("Buy groceries #groceries", "Call dentist at 3 PM #health", "Prepare presentation #work", "Review quarterly goals #work")
        ).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val decorView = activity.window.decorView
        renderAndSaveView(decorView, File(phoneScreenshotsDir, "1.png"), 375, 667)
        renderAndSaveView(decorView, File(tabletScreenshotsDir, "1.png"), 1024, 768)
    }

    private fun captureScreenshot2Punted() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Call dentist at 3 PM #health", "Prepare presentation #work", "Pay electricity bill #home", "Review budget #finance"))
            .putLong("snooze_pay electricity bill #home", now + 2 * 3600 * 1000L)
            .putLong("snooze_review budget #finance", now + 24 * 3600 * 1000L)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val decorView = activity.window.decorView
        renderAndSaveView(decorView, File(phoneScreenshotsDir, "2.png"), 375, 667)
        renderAndSaveView(decorView, File(tabletScreenshotsDir, "2.png"), 1024, 768)
    }

    private fun captureScreenshot3FilterDialog() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet(
            "key_reminders_list",
            setOf("Buy groceries #groceries", "Call dentist #health", "Pay electric bill #home", "Prepare slides #work", "Review budget #finance")
        ).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnFilter = activity.findViewById<View>(R.id.btn_tags_filter)
        btnFilter?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView
        renderAndSaveView(viewToRender, File(phoneScreenshotsDir, "3.png"), 375, 667)
        renderAndSaveView(viewToRender, File(tabletScreenshotsDir, "3.png"), 1024, 768)
    }

    private fun captureScreenshot4NotificationDrawer() {
        val sbn1 = createMockSbn("com.whatsapp", "WhatsApp", "Meeting with design team at 2 PM")
        val sbn2 = createMockSbn("com.android.calendar", "Calendar", "Doctor's Appointment at 4 PM")
        val sbn3 = createMockSbn("com.google.android.gm", "Gmail", "Flight confirmation for Friday")
        val sbn4 = createMockSbn("com.slack", "Slack", "Code review request for pull request")

        PickNotificationActivity.mockActiveNotifications = arrayOf(sbn1, sbn2, sbn3, sbn4)

        val controller = Robolectric.buildActivity(PickNotificationActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView

        renderAndSaveView(viewToRender, File(phoneScreenshotsDir, "4.png"), 375, 667)
        renderAndSaveView(viewToRender, File(tabletScreenshotsDir, "4.png"), 1024, 768)
    }

    private fun captureScreenshot5AboutAndLogs() {
        val app = RuntimeEnvironment.getApplication()
        AppLogger.clearLogs(app)
        AppLogger.log(app, "Application", "Application started successfully")
        AppLogger.log(app, "NotificationListener", "Listener connected and monitoring notifications")
        AppLogger.log(app, "Matcher", "Matched reminder: 'Buy groceries'")
        AppLogger.log(app, "MainActivity", "Punted reminder for 2 hours")

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnAbout = activity.findViewById<TextView>(R.id.btn_nav_about)
        btnAbout?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val decorView = activity.window.decorView
        renderAndSaveView(decorView, File(phoneScreenshotsDir, "5.png"), 375, 667)
        renderAndSaveView(decorView, File(tabletScreenshotsDir, "5.png"), 1024, 768)
    }

    private fun captureScreenshot6KeyboardEditing() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet(
            "key_reminders_list",
            setOf("Buy groceries #groceries", "Call dentist at 3 PM #health", "Prepare presentation #work")
        ).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.reminders_list)
        val viewHolder = recyclerView?.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ItemViewHolder
        viewHolder?.reminderInput?.requestFocus()
        viewHolder?.reminderInput?.setText("Buy groceries and milk #groceries")
        viewHolder?.reminderInput?.setSelection(13)

        shadowOf(Looper.getMainLooper()).idle()

        val decorView = activity.window.decorView
        renderAndSaveView(decorView, File(phoneScreenshotsDir, "6.png"), 375, 667)
        renderAndSaveView(decorView, File(tabletScreenshotsDir, "6.png"), 1024, 768)
    }


    private fun createMockSbn(packageName: String, title: String, text: String): StatusBarNotification {
        val context = RuntimeEnvironment.getApplication()
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

    private fun renderAndSaveView(view: View, outputFile: File, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot ${outputFile.name} should exist", outputFile.exists())
        assertTrue("Screenshot ${outputFile.name} should not be empty", outputFile.length() > 0)
    }
}
