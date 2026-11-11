package com.bas080.notificationreminders.sync

import android.content.Context
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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
    fun testDiscoverAndConnectFallback() {
        ServerSyncManager.discoverAndConnect(context, "", "1234") { _, url, _ ->
            assertNotNull(url)
            assertTrue(url.startsWith("http://"))
        }
    }

    @Test
    fun testDisconnectWhenNotConnectedDoesNotThrow() {
        ServerSyncManager.disconnect()
        assertTrue(!ServerSyncManager.isConnected())
    }

    @Test
    fun testPingUrlPreservesHttpFailureStatus() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/ping") { exchange ->
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
            }
            start()
        }

        try {
            val result = ServerSyncManager.pingUrl("http://127.0.0.1:${server.address.port}")
            assertTrue(result.isFailure)
            assertEquals("HTTP response 503", result.exceptionOrNull()?.message)
        } finally {
            server.stop(0)
        }
    }
}
