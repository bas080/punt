package com.bas080.notificationreminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bas080.notificationreminders.utils.CalendarSyncManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarSyncAndroidTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testCalendarSyncPreferencesAndDurationFormatting() {
        CalendarSyncManager.setCalendarSyncEnabled(context, true)
        assertEquals(true, CalendarSyncManager.isCalendarSyncEnabled(context))

        CalendarSyncManager.setCalendarSyncEnabled(context, false)
        assertEquals(false, CalendarSyncManager.isCalendarSyncEnabled(context))

        assertEquals("15m", CalendarSyncManager.formatMinutesToDuration(15))
        assertEquals("1h", CalendarSyncManager.formatMinutesToDuration(60))
        assertEquals("1d", CalendarSyncManager.formatMinutesToDuration(1440))
    }

    @Test
    fun testCalendarEventDetailsDataClass() {
        val details = CalendarSyncManager.CalendarEventDetails(
            id = 1L,
            title = "Test Event",
            locationKey = "test event",
            dtStart = 1000L,
            syncData1 = "test event",
            description = "Desc",
            allDay = false
        )

        assertEquals(1L, details.id)
        assertEquals("Test Event", details.title)
        assertEquals("test event", details.locationKey)
        assertEquals("test event", details.syncData1)
        assertNotNull(details)
    }
}
