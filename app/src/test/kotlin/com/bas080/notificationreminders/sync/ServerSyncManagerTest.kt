package com.bas080.notificationreminders.sync

import android.content.Context
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
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
    fun testPairWithServerExchangesPinForSessionToken() {
        val receivedPin = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/pair") { exchange ->
                val request = exchange.requestBody.bufferedReader().use { it.readText() }
                receivedPin.set(JSONObject(request).optString("code"))
                val response = """{"success":true,"token":"session-token"}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = pairWithServer("http://127.0.0.1:${server.address.port}", "4829")
            assertTrue(result.isSuccess)
            assertEquals("4829", receivedPin.get())
            assertEquals("session-token", result.getOrNull())
        } finally {
            server.stop(0)
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
