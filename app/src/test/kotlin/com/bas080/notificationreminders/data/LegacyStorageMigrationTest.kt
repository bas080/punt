package com.bas080.notificationreminders.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class LegacyStorageMigrationTest {

    private lateinit var context: Context
    private lateinit var db: PuntDatabase

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, PuntDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testMigratesRemindersAndSnoozesFromPrefs() = runBlocking {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val now = System.currentTimeMillis()
        val puntTime = now + 3600000L

        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Buy milk #groceries", "Call mom", "Finish report #done"))
            .putLong("snooze_Call mom", puntTime)
            .commit()

        assertEquals(3, prefs.getStringSet("key_reminders_list", null)?.size)

        LegacyStorageMigration.migrateIfNeeded(context, db)

        assertTrue(prefs.getBoolean(LegacyStorageMigration.KEY_LEGACY_MIGRATION_DONE, false))

        val dao = db.reminderDao()
        val all = dao.getAllReminders()
        assertEquals(3, all.size)

        val active = all.first { it.text == "Buy milk #groceries" }
        assertFalse(active.isDone)
        assertNull(active.snoozeUntil)

        val punted = all.first { it.text == "Call mom" }
        assertFalse(punted.isDone)
        assertEquals(puntTime, punted.snoozeUntil)

        val completed = all.first { it.text == "Finish report" }
        assertTrue(completed.isDone)
        assertFalse(completed.text.contains("#done"))
    }

    @Test
    fun testMigrationRunsOnlyOnce() = runBlocking {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Existing task"))
            .commit()

        LegacyStorageMigration.migrateIfNeeded(context, db)
        assertEquals(1, db.reminderDao().getAllReminders().size)

        // Add another legacy item without unsetting migration done
        prefs.edit().putStringSet("key_reminders_list", setOf("Existing task", "Another task")).commit()
        LegacyStorageMigration.migrateIfNeeded(context, db)

        // Should not migrate again because flag is true
        assertEquals(1, db.reminderDao().getAllReminders().size)
    }

    @Test
    fun testMigrationWithEmptyLegacyPrefs() = runBlocking {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        LegacyStorageMigration.migrateIfNeeded(context, db)

        assertTrue(prefs.getBoolean(LegacyStorageMigration.KEY_LEGACY_MIGRATION_DONE, false))
        assertTrue(db.reminderDao().getAllReminders().isEmpty())
    }
}
