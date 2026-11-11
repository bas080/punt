package com.bas080.notificationreminders.utils

import android.content.Context
import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.data.ReminderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TaskBackupManagerTest {

    private lateinit var context: Context
    private val PREFS_REMINDERS = "reminders_prefs"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val dao = PuntDatabase.getInstance(context).reminderDao()
        runBlocking { dao.deleteAll() }
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val backupFile = TaskBackupManager.getFilesBackupFile(context)
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }

    @Test
    fun testBackupAndRestoreTasks() {
        val snoozeTime = System.currentTimeMillis() + 3600000L
        val dao = PuntDatabase.getInstance(context).reminderDao()
        runBlocking {
            dao.insert(ReminderEntity(text = "Buy milk"))
            dao.insert(ReminderEntity(text = "Call mom", isDone = true))
            dao.insert(ReminderEntity(text = "Pay bills", snoozeUntil = snoozeTime))
        }

        TaskBackupManager.backupTasks(context)

        val backupFile = TaskBackupManager.getFilesBackupFile(context)
        assertTrue(backupFile.exists())
        val content = backupFile.readText()
        assertTrue(content.contains("Buy milk"))
        assertTrue(content.contains("Call mom #done"))
        assertTrue(content.contains("Pay bills"))

        // Clear legacy storage and database to simulate a fresh install.
        runBlocking { dao.deleteAll() }
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val restored = TaskBackupManager.restoreIfEmpty(context)
        assertTrue(restored)

        val restoredReminders = runBlocking { dao.getAllReminders() }
        assertTrue(restoredReminders.any { it.text == "Buy milk" && !it.isDone })
        assertTrue(restoredReminders.any { it.text == "Call mom" && it.isDone })
        assertTrue(restoredReminders.any { it.text == "Pay bills" && it.snoozeUntil != null })
    }

    @Test
    fun testRestoreDoesNotOverwriteExistingTasks() {
        val backupFile = TaskBackupManager.getFilesBackupFile(context)
        backupFile.parentFile?.mkdirs()
        backupFile.writeText("- [ ] Old backup task\n")

        val dao = PuntDatabase.getInstance(context).reminderDao()
        runBlocking { dao.insert(ReminderEntity(text = "Existing task")) }

        val restored = TaskBackupManager.restoreIfEmpty(context)
        assertFalse(restored)

        val reminders = runBlocking { dao.getAllReminders() }
        assertEquals(1, reminders.size)
        assertEquals("Existing task", reminders[0].text)
    }
}
