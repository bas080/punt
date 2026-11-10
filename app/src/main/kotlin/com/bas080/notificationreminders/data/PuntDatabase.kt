package com.bas080.notificationreminders.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReminderEntity::class], version = 1, exportSchema = false)
abstract class PuntDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: PuntDatabase? = null

        fun getInstance(context: Context): PuntDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PuntDatabase::class.java,
                    "punt_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
