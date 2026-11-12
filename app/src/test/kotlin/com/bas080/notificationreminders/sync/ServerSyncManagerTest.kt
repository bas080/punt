package com.bas080.notificationreminders.sync

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
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

    private fun startHttpServer(
        responseCode: Int,
        responseBody: String,
        receivedRequestBody: AtomicReference<String> = AtomicReference("")
    ): Pair<ServerSocket, Thread> {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            server.accept().use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                var contentLength = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(':').trim().toInt()
                    }
                }
                val requestBody = CharArray(contentLength)
                var bodyRead = 0
                while (bodyRead < contentLength) {
                    val count = reader.read(requestBody, bodyRead, contentLength - bodyRead)
                    if (count < 0) break
                    bodyRead += count
                }
                receivedRequestBody.set(String(requestBody, 0, bodyRead))

                val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
                val statusText = if (responseCode == 200) "OK" else "Service Unavailable"
                val headers = "HTTP/1.1 $responseCode $statusText\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                client.getOutputStream().apply {
                    write(headers.toByteArray(Charsets.UTF_8))
                    write(responseBytes)
                    flush()
                }
            }
        }.apply { start() }
        return server to thread
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testPairWithServerExchangesPinForSessionToken() {
        val receivedPin = AtomicReference<String>()
        val server = startHttpServer(200, """{"success":true,"token":"session-token"}""", receivedPin)

        try {
            val result = pairWithServer("http://127.0.0.1:${server.first.localPort}", "4829")
            assertTrue(result.isSuccess)
            assertEquals("4829", JSONObject(receivedPin.get()).optString("code"))
            assertEquals("session-token", result.getOrNull())
        } finally {
            server.first.close()
            server.second.join(1000)
        }
    }

    @Test
    fun testDisconnectWhenNotConnectedDoesNotThrow() {
        ServerSyncManager.disconnect()
        assertTrue(!ServerSyncManager.isConnected())
    }

    @Test
    fun testPingUrlPreservesHttpFailureStatus() {
        val server = startHttpServer(503, "")

        try {
            val result = ServerSyncManager.pingUrl("http://127.0.0.1:${server.first.localPort}")
            assertTrue(result.isFailure)
            assertEquals("HTTP response 503", result.exceptionOrNull()?.message)
        } finally {
            server.first.close()
            server.second.join(1000)
        }
    }
}
