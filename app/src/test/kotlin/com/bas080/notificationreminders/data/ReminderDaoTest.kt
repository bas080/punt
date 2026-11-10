package com.bas080.notificationreminders.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderDaoTest {

    private lateinit var db: PuntDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun createDb() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, PuntDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reminderDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetReminder() = runBlocking {
        val reminder = ReminderEntity(text = "Test reminder #work")
        val id = dao.insert(reminder)
        assertTrue(id > 0)

        val retrieved = dao.getReminderById(id)
        assertNotNull(retrieved)
        assertEquals("Test reminder #work", retrieved?.text)
        assertEquals(false, retrieved?.isDone)
        assertNull(retrieved?.snoozeUntil)
    }

    @Test
    fun testUpdateReminderDoneAndPunt() = runBlocking {
        val reminder = ReminderEntity(text = "Recurring #every 1d")
        val id = dao.insert(reminder)

        val now = System.currentTimeMillis()
        val puntTime = now + 86400000L
        val updated = dao.getReminderById(id)!!.copy(
            isDone = true,
            snoozeUntil = puntTime
        )
        dao.update(updated)

        val retrieved = dao.getReminderById(id)
        assertEquals(true, retrieved?.isDone)
        assertEquals(puntTime, retrieved?.snoozeUntil)
    }

    @Test
    fun testDeleteReminder() = runBlocking {
        val reminder = ReminderEntity(text = "To delete")
        val id = dao.insert(reminder)
        assertNotNull(dao.getReminderById(id))

        dao.deleteById(id)
        assertNull(dao.getReminderById(id))
    }

    @Test
    fun testGetAllReminders() = runBlocking {
        dao.insert(ReminderEntity(text = "First", createdAt = 1000L))
        dao.insert(ReminderEntity(text = "Second", createdAt = 2000L))

        val all = dao.getAllReminders()
        assertEquals(2, all.size)
        assertEquals("Second", all[0].text)
        assertEquals("First", all[1].text)
    }
}
