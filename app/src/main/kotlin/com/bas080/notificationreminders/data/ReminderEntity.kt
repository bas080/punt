package com.bas080.notificationreminders.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "snooze_until")
    val snoozeUntil: Long? = null,
    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false,
    @ColumnInfo(name = "recurrence_interval")
    val recurrenceInterval: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
