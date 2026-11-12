package com.bas080.notificationreminders.sync

import android.content.Context
import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.data.ReminderEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerSyncEndToEndTest {

    private lateinit var context: Context
    private var serverProcess: Process? = null
    private var testPort = 0
    private lateinit var serverUrl: String
    private val cliFile = run {
        val pathInRoot = java.io.File("dist/server/cli.js").canonicalFile
        if (pathInRoot.exists()) pathInRoot else java.io.File("../dist/server/cli.js").canonicalFile
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ServerSyncManager.disconnect()
        runBlocking { PuntDatabase.getInstance(context).reminderDao().deleteAll() }
        testPort = ServerSocket(0).use { it.localPort }
        serverUrl = "http://127.0.0.1:$testPort"
        val pb = ProcessBuilder("node", cliFile.canonicalPath, "serve", "--port", testPort.toString(), "--code", "4829")
        pb.redirectErrorStream(true)
        pb.directory(cliFile.parentFile?.parentFile)
        serverProcess = pb.start()
        assertTrue("Node server did not start on port $testPort", waitForServer())
    }

    @After
    fun tearDown() {
        ServerSyncManager.disconnect()
        serverProcess?.destroyForcibly()
        serverProcess?.waitFor(2, TimeUnit.SECONDS)
    }

    @Test
    fun testAndroidAppSyncWithServerAndCli() {
        val db = PuntDatabase.getInstance(context)
        runBlocking {
            db.reminderDao().deleteAll()
            db.reminderDao().insert(ReminderEntity(text = "Synced Android Todo", isDone = false))
        }

        ServerSyncManager.discoverAndConnect(context, serverUrl, "4829")
        assertTrue("Android sync manager did not connect", waitForConnection())

        var cliOutput = ""
        val deadline = System.currentTimeMillis() + 10000L
        while (System.currentTimeMillis() < deadline) {
            cliOutput = runCliTaskList()
            if (cliOutput.contains("Synced Android Todo")) break
            Thread.sleep(250L)
        }

        assertTrue("CLI did not receive Android task snapshot: $cliOutput", cliOutput.contains("Synced Android Todo"))
    }

    private fun waitForServer(): Boolean {
        val deadline = System.currentTimeMillis() + 10000L
        while (System.currentTimeMillis() < deadline && serverProcess?.isAlive == true) {
            val isReady = runCatching {
                val connection = URL("$serverUrl/api/ping").openConnection() as HttpURLConnection
                connection.connectTimeout = 500
                connection.readTimeout = 500
                try {
                    connection.responseCode == 200
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(false)
            if (isReady) return true
            Thread.sleep(50L)
        }
        return false
    }

    private fun waitForConnection(): Boolean {
        val deadline = System.currentTimeMillis() + 10000L
        while (System.currentTimeMillis() < deadline) {
            if (ServerSyncManager.isConnected()) return true
            Thread.sleep(50L)
        }
        return false
    }

    private fun runCliTaskList(): String {
        val cliProcess = ProcessBuilder(
            "node",
            cliFile.canonicalPath,
            "tasks",
            "list",
            "--port",
            testPort.toString()
        ).apply {
            directory(cliFile.parentFile?.parentFile)
            redirectErrorStream(true)
        }.start()
        val output = cliProcess.inputStream.bufferedReader().use { it.readText() }
        cliProcess.waitFor(5, TimeUnit.SECONDS)
        return output
    }
}
