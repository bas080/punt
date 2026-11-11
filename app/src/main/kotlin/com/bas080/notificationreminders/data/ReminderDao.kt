package com.bas080.notificationreminders.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY created_at DESC")
    fun getAllRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY created_at DESC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE text = :text LIMIT 1")
    suspend fun getReminderByText(text: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE is_done = 0 AND (snooze_until IS NULL OR snooze_until <= :now)")
    suspend fun getActiveReminders(now: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET snooze_until = :snoozeUntil, updated_at = :updatedAt WHERE text = :text")
    suspend fun updateSnooze(text: String, snoozeUntil: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET is_done = :isDone, updated_at = :updatedAt WHERE text = :text")
    suspend fun markDone(text: String, isDone: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE text = :text")
    suspend fun deleteByText(text: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
