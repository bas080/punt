package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmActivityTest {

    @Test
    fun testAlarmActivityDisplaysReminderTextAndGuideForOneOff() {
        val intent = Intent(RuntimeEnvironment.getApplication(), AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REMINDER_TEXT, "Take medication #alarm")
        }

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, intent).setup()
        val activity = controller.get()

        val reminderTv = activity.findViewById<TextView>(R.id.txt_alarm_reminder_text)
        val guideRightTv = activity.findViewById<TextView>(R.id.txt_guide_right)

        assertEquals("Take medication #alarm", reminderTv.text.toString())
        assertEquals("Swipe right to mark done →", guideRightTv.text.toString())
    }

    @Test
    fun testAlarmActivityDisplaysGuideForRecurringTask() {
        val intent = Intent(RuntimeEnvironment.getApplication(), AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REMINDER_TEXT, "Take medication #every 1d #alarm")
        }

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, intent).setup()
        val activity = controller.get()

        val guideRightTv = activity.findViewById<TextView>(R.id.txt_guide_right)
        assertEquals("Swipe right to punt to next recurring →", guideRightTv.text.toString())
    }

    @Test
    fun testSwipeRightMarksOneOffTaskDone() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Take medication #alarm")).commit()

        val intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REMINDER_TEXT, "Take medication #alarm")
        }

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, intent).setup()
        val activity = controller.get()

        val swipeRightMethod = AlarmActivity::class.java.getDeclaredMethod("onSwipeRight")
        swipeRightMethod.isAccessible = true
        swipeRightMethod.invoke(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.contains("Take medication #alarm #done"))
        assertEquals("Reminder marked done", ShadowToast.getTextOfLatestToast())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun testSwipeLeftOpensSnoozePickerAndAppliesChoice() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REMINDER_TEXT, "Take medication #alarm")
        }

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, intent).setup()
        val activity = controller.get()

        val swipeLeftMethod = AlarmActivity::class.java.getDeclaredMethod("onSwipeLeft")
        swipeLeftMethod.isAccessible = true
        swipeLeftMethod.invoke(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze options dialog should be displayed", dialog)

        val inputEditText = dialog!!.findViewById<android.widget.EditText>(R.id.import_input)
        assertNotNull(inputEditText)

        val parentLayout = inputEditText!!.parent as android.view.ViewGroup
        val optionsList = parentLayout.getChildAt(0) as android.view.ViewGroup
        val firstOptionTv = optionsList.getChildAt(0) as TextView

        firstOptionTv.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = prefs.getLong("snooze_take medication #alarm", 0L)
        assertTrue("Snooze timestamp should be set", snoozeTime > System.currentTimeMillis())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun testSwipeRightAdvancesRecurringTask() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Take medication #every 1d #alarm")).commit()

        val intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REMINDER_TEXT, "Take medication #every 1d #alarm")
        }

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, intent).setup()
        val activity = controller.get()

        val swipeRightMethod = AlarmActivity::class.java.getDeclaredMethod("onSwipeRight")
        swipeRightMethod.isAccessible = true
        swipeRightMethod.invoke(activity)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue(savedSet.contains("Take medication #every 1d #alarm"))
        val snoozeTime = prefs.getLong("snooze_take medication #every 1d #alarm", 0L)
        assertTrue("Recurring snooze timestamp should be set", snoozeTime > System.currentTimeMillis())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun testFlingGestureDetector() {
        val intent = Intent(RuntimeEnvironment.getApplication(), AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REMINDER_TEXT, "Take medication #alarm")
        }

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, intent).setup()
        val activity = controller.get()

        val card = activity.findViewById<View>(R.id.card_alarm_reminder)
        assertNotNull(card)

        val e1 = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_DOWN, 500f, 500f, 0)
        val e2Right = android.view.MotionEvent.obtain(0L, 100L, android.view.MotionEvent.ACTION_UP, 800f, 500f, 0)
        card.dispatchTouchEvent(e1)
        card.dispatchTouchEvent(e2Right)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun testBlankReminderTextFinishesActivity() {
        val intent = Intent(RuntimeEnvironment.getApplication(), AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REMINDER_TEXT, "   ")
        }

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, intent).setup()
        val activity = controller.get()

        assertTrue(activity.isFinishing)
    }
}
