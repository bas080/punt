package com.bas080.notificationreminders.providers

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.data.ReminderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemindersContentProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val dao = PuntDatabase.getInstance(context).reminderDao()
        runBlocking {
            dao.deleteAll()
            dao.insert(ReminderEntity(text = "Buy groceries #work", snoozeUntil = System.currentTimeMillis() + 3600000L))
            dao.insert(ReminderEntity(text = "Fix bike"))
            dao.insert(ReminderEntity(text = "Submit report #work", isDone = true))
        }

        Robolectric.setupContentProvider(RemindersContentProvider::class.java)
    }

    @Test
    fun testQueryAllReminders() {
        val resolver = context.contentResolver
        val cursor = resolver.query(RemindersContentProvider.CONTENT_URI, null, null, null, null)

        assertNotNull(cursor)
        assertEquals(3, cursor!!.count)

        val textIndex = cursor.getColumnIndex("text")
        val isDoneIndex = cursor.getColumnIndex("is_done")
        val snoozeIndex = cursor.getColumnIndex("snooze_until")

        val texts = mutableListOf<String>()
        while (cursor.moveToNext()) {
            texts.add(cursor.getString(textIndex))
            val isDone = cursor.getInt(isDoneIndex)
            if (cursor.getString(textIndex).contains("#done")) {
                assertEquals(1, isDone)
            } else {
                assertEquals(0, isDone)
            }
            if (cursor.getString(textIndex) == "Buy groceries #work") {
                assertTrue(cursor.getLong(snoozeIndex) > System.currentTimeMillis())
            }
        }
        cursor.close()

        assertTrue(texts.contains("Buy groceries #work"))
        assertTrue(texts.contains("Fix bike"))
        assertTrue(texts.contains("Submit report #work #done"))
    }

    @Test
    fun testQueryFilteredRemindersBySelection() {
        val resolver = context.contentResolver
        val cursor = resolver.query(RemindersContentProvider.CONTENT_URI, null, "#work", null, null)

        assertNotNull(cursor)
        val textIndex = cursor!!.getColumnIndex("text")

        val texts = mutableListOf<String>()
        while (cursor.moveToNext()) {
            texts.add(cursor.getString(textIndex))
        }
        cursor.close()

        assertEquals(2, texts.size)
        assertTrue(texts.contains("Buy groceries #work"))
        assertTrue(texts.contains("Submit report #work #done"))
    }

    @Test
    fun testQueryFilteredRemindersByUriQueryParameter() {
        val resolver = context.contentResolver
        val filteredUri = RemindersContentProvider.CONTENT_URI.buildUpon()
            .appendQueryParameter("q", "bike")
            .build()

        val cursor = resolver.query(filteredUri, null, null, null, null)

        assertNotNull(cursor)
        assertEquals(1, cursor!!.count)

        cursor.moveToFirst()
        val textIndex = cursor.getColumnIndex("text")
        assertEquals("Fix bike", cursor.getString(textIndex))
        cursor.close()
    }

    @Test
    fun testReadOnlyOperationsThrowUnsupportedOperationException() {
        val resolver = context.contentResolver

        try {
            resolver.insert(RemindersContentProvider.CONTENT_URI, ContentValues().apply { put("text", "New") })
            fail("insert should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertEquals("RemindersContentProvider is read-only", e.message)
        }

        try {
            resolver.delete(RemindersContentProvider.CONTENT_URI, null, null)
            fail("delete should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertEquals("RemindersContentProvider is read-only", e.message)
        }

        try {
            resolver.update(RemindersContentProvider.CONTENT_URI, ContentValues().apply { put("text", "Update") }, null, null)
            fail("update should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertEquals("RemindersContentProvider is read-only", e.message)
        }
    }

    @Test
    fun testContentObserverReceivesNotificationOnChange() {
        val resolver = context.contentResolver
        var changeNotified = false

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changeNotified = true
            }
        }

        resolver.registerContentObserver(RemindersContentProvider.CONTENT_URI, true, observer)

        RemindersContentProvider.notifyChange(context)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("ContentObserver should be notified when data changes", changeNotified)

        resolver.unregisterContentObserver(observer)
    }
}
