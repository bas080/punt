package com.bas080.notificationreminders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.ImageView
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FabQuadrantScreenshotTest {

    private lateinit var outputDir: File

    @Before
    fun setUp() {
        outputDir = File("build/outputs/fab_screenshots")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        assertTrue("Output dir must exist", outputDir.exists() && outputDir.isDirectory)
    }

    @After
    fun tearDown() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testFabQuadrant_BottomRight() {
        captureAndVerifyFabPosition("bottom_right", 0.85f, 0.85f)
    }

    @Test
    fun testFabQuadrant_BottomLeft() {
        captureAndVerifyFabPosition("bottom_left", 0.15f, 0.85f)
    }

    @Test
    fun testFabQuadrant_TopRight() {
        captureAndVerifyFabPosition("top_right", 0.85f, 0.15f)
    }

    @Test
    fun testFabQuadrant_TopLeft() {
        captureAndVerifyFabPosition("top_left", 0.15f, 0.15f)
    }

    @Test
    fun testFabQuadrant_ExtremeLeftEdge() {
        captureAndVerifyFabPosition("extreme_left", 0.05f, 0.85f)
    }

    @Test
    fun testFabQuadrant_ExtremeRightEdge() {
        captureAndVerifyFabPosition("extreme_right", 0.95f, 0.85f)
    }

    @Test
    fun testFabQuadrant_ExtremeTopEdge() {
        captureAndVerifyFabPosition("extreme_top", 0.5f, 0.05f)
    }

    private fun captureAndVerifyFabPosition(name: String, relX: Float, relY: Float) {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putStringSet(
                "key_reminders_list",
                setOf(
                    "Buy groceries #groceries",
                    "Call dentist at 3 PM #health",
                    "Prepare presentation #work",
                    "Swim #fit #every1700",
                    "Finish Cy login registration #tax"
                )
            )
            .putFloat("key_fab_rel_x", relX)
            .putFloat("key_fab_rel_y", relY)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val fabMenu = activity.findViewById<ImageView>(R.id.fab_menu)
        val fabPillMenuContainer = activity.findViewById<View>(R.id.fab_pill_menu_container)

        fabMenu.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("FAB pill menu should be visible after click", fabPillMenuContainer.visibility == View.VISIBLE)

        val outputFile = File(outputDir, "fab_$name.png")
        renderAndSaveView(activity.window.decorView, outputFile, 375, 667)
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
