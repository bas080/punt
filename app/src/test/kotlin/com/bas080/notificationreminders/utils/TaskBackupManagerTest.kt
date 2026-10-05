package com.bas080.notificationreminders.utils

import android.content.Context
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
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val backupFile = TaskBackupManager.getFilesBackupFile(context)
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }

    @Test
    fun testBackupAndRestoreTasks() {
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val initialSet = setOf("Buy milk", "Call mom #done", "Pay bills")
        prefs.edit().putStringSet("key_reminders", initialSet).commit()

        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit().putLong("snooze_pay bills", snoozeTime).commit()

        TaskBackupManager.backupTasks(context)

        val backupFile = TaskBackupManager.getFilesBackupFile(context)
        assertTrue(backupFile.exists())
        val content = backupFile.readText()
        assertTrue(content.contains("Buy milk"))
        assertTrue(content.contains("Call mom #done"))
        assertTrue(content.contains("Pay bills"))

        // Clear preferences to simulate fresh app launch/install under new package name
        prefs.edit().clear().commit()
        val emptySet = prefs.getStringSet("key_reminders", null)
        assertTrue(emptySet.isNullOrEmpty())

        val restored = TaskBackupManager.restoreIfEmpty(context)
        assertTrue(restored)

        val restoredSet = prefs.getStringSet("key_reminders", null)
        assertTrue(restoredSet != null && restoredSet.contains("Buy milk"))
        assertTrue(restoredSet != null && restoredSet.contains("Call mom #done"))
        assertTrue(restoredSet != null && restoredSet.contains("Pay bills"))

        val restoredSnooze = prefs.getLong("snooze_pay bills", 0L)
        assertTrue(restoredSnooze > 0L)
    }

    @Test
    fun testRestoreDoesNotOverwriteExistingTasks() {
        val backupFile = TaskBackupManager.getFilesBackupFile(context)
        backupFile.parentFile?.mkdirs()
        backupFile.writeText("- [ ] Old backup task\n")

        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders", setOf("Existing task")).commit()

        val restored = TaskBackupManager.restoreIfEmpty(context)
        assertFalse(restored)

        val set = prefs.getStringSet("key_reminders", null)
        assertEquals(1, set?.size)
        assertTrue(set?.contains("Existing task") == true)
    }
}
