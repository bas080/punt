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
import java.io.BufferedReader
import java.io.InputStreamReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerSyncEndToEndTest {

    private lateinit var context: Context
    private var serverProcess: Process? = null
    private val testPort = 7895
    private val cliFile = run {
        val pathInRoot = java.io.File("dist/server/cli.js").canonicalFile
        if (pathInRoot.exists()) pathInRoot else java.io.File("../dist/server/cli.js").canonicalFile
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val pb = ProcessBuilder("node", cliFile.canonicalPath, "serve", "--port", testPort.toString(), "--code", "4829")
        pb.redirectErrorStream(true)
        pb.directory(cliFile.parentFile?.parentFile)
        serverProcess = pb.start()
        Thread.sleep(1500)
    }

    @After
    fun tearDown() {
        ServerSyncManager.disconnect()
        serverProcess?.destroyForcibly()
    }

    @Test
    fun testAndroidAppSyncWithServerAndCli() {
        val db = PuntDatabase.getInstance(context)
        runBlocking {
            db.reminderDao().deleteAll()
            db.reminderDao().insert(ReminderEntity(text = "Synced Android Todo", isDone = false))
        }

        ServerSyncManager.discoverAndConnect(context, "http://localhost:$testPort", "4829")
        Thread.sleep(1000)

        ServerSyncManager.pushTasksDump(context)
        Thread.sleep(1000)

        // Query server using CLI binary
        val cliPb = ProcessBuilder("node", cliFile.canonicalPath, "tasks", "list", "--port", testPort.toString())
        cliPb.directory(cliFile.parentFile?.parentFile)
        cliPb.redirectErrorStream(true)
        val cliProc = cliPb.start()
        val reader = BufferedReader(InputStreamReader(cliProc.inputStream))
        val output = reader.readText()
        cliProc.waitFor()

        assertTrue("CLI output should contain task pushed from Android app: $output", output.contains("Synced Android Todo"))
    }
}
