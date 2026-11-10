package com.bas080.notificationreminders.sync

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerSyncManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testGenerateAndRetrievePairingCode() {
        val code = ServerSyncManager.generatePairingCode(context)
        assertEquals(4, code.length)
        assertTrue(code.all { it.isDigit() })

        val retrieved = ServerSyncManager.getPairingCode(context)
        assertEquals(code, retrieved)
    }

    @Test
    fun testDisconnectWhenNotConnectedDoesNotThrow() {
        ServerSyncManager.disconnect()
        assertTrue(!ServerSyncManager.isConnected())
    }
}
