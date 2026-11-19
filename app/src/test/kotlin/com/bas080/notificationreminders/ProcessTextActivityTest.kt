package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessTextActivityTest {

    @org.junit.Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        kotlinx.coroutines.runBlocking {
            com.bas080.notificationreminders.data.PuntDatabase.getInstance(context).reminderDao().deleteAll()
        }
    }

    @Test
    fun testProcessTextActionCreatesReminder() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT, "Buy milk today")
            type = "text/plain"
        }

        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent)
        controller.create()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(context).reminderDao()
        val stored = kotlinx.coroutines.runBlocking { dao.getReminderByText("Buy milk today") }
        assertTrue("Reminder should be stored in Room", stored != null)

        assertTrue("Activity should be finishing", controller.get().isFinishing)
    }

    @Test
    fun testActionSendCreatesReminder() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "Call doctor tomorrow")
            type = "text/plain"
        }

        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent)
        controller.create()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(context).reminderDao()
        val stored = kotlinx.coroutines.runBlocking { dao.getReminderByText("Call doctor tomorrow") }
        assertTrue("Reminder should be stored in Room", stored != null)

        assertTrue("Activity should be finishing", controller.get().isFinishing)
    }


    @Test
    fun testNullIntentOrOtherActionAndOnNewIntent() {
        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java)
        val activity = controller.get()

        controller.create()
        assertTrue(activity.isFinishing)

        // Test ACTION_SEND with non text/plain type
        val imageIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, "Should be ignored")
        }
        Robolectric.buildActivity(ProcessTextActivity::class.java, imageIntent).create()

        // Test CharSequence extra on ACTION_SEND
        val charSeqIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, StringBuilder("CharSeq text") as CharSequence)
        }
        Robolectric.buildActivity(ProcessTextActivity::class.java, charSeqIntent).create()
        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testProcessTextEmptyShowsFailureToast() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT, "   ")
            type = "text/plain"
        }

        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent)
        controller.create()

        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
        assertTrue("Activity should be finishing", controller.get().isFinishing)
    }
}
