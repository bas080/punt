package com.bas080.notificationreminders.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class RoomPersistenceTest {

    private lateinit var context: Context
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val db = PuntDatabase.getInstance(context)
        dao = db.reminderDao()
        runBlocking {
            dao.deleteAll()
        }
    }

    @Test
    fun testRoomDaoInsertQueryUpdateDelete() = runBlocking {
        // Insert
        val id = dao.insert(ReminderEntity(text = "Buy apples", isDone = false))
        assertTrue(id > 0)

        // Query
        val reminder = dao.getReminderByText("Buy apples")
        assertNotNull(reminder)
        assertEquals("Buy apples", reminder?.text)
        assertFalse(reminder?.isDone ?: true)

        // Update Snooze
        val futureTime = System.currentTimeMillis() + 3600000L
        dao.updateSnooze("Buy apples", futureTime)
        val snoozed = dao.getReminderByText("Buy apples")
        assertEquals(futureTime, snoozed?.snoozeUntil)

        // Mark Done
        dao.markDone("Buy apples", true)
        val done = dao.getReminderByText("Buy apples")
        assertTrue(done?.isDone ?: false)

        // Delete
        dao.deleteByText("Buy apples")
        assertNull(dao.getReminderByText("Buy apples"))
    }

    @Test
    fun testRoomDaoActiveAndPuntedQueries() = runBlocking {
        dao.insert(ReminderEntity(text = "Active task 1", isDone = false, snoozeUntil = null))
        dao.insert(ReminderEntity(text = "Active task 2", isDone = false, snoozeUntil = System.currentTimeMillis() - 1000L))
        dao.insert(ReminderEntity(text = "Punted task", isDone = false, snoozeUntil = System.currentTimeMillis() + 3600000L))
        dao.insert(ReminderEntity(text = "Done task", isDone = true, snoozeUntil = null))

        val now = System.currentTimeMillis()
        val active = dao.getActiveReminders(now)
        assertEquals(2, active.size)
        assertTrue(active.any { it.text == "Active task 1" })
        assertTrue(active.any { it.text == "Active task 2" })

        val all = dao.getAllReminders()
        val punted = all.filter { !it.isDone && it.snoozeUntil != null && it.snoozeUntil!! > now }
        assertEquals(1, punted.size)
        assertEquals("Punted task", punted[0].text)
    }
}
