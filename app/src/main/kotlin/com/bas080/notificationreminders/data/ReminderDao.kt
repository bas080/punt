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

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE text = :text LIMIT 1")
    suspend fun getReminderByText(text: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>): List<Long>

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders WHERE text = :text")
    suspend fun deleteByText(text: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
