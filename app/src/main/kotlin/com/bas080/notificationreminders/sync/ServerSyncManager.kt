package com.bas080.notificationreminders.sync

import android.content.Context
import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.data.ReminderDao
import com.bas080.notificationreminders.data.ReminderEntity
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.utils.AppLogger
import com.bas080.notificationreminders.utils.TaskBackupManager
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object ServerSyncManager {

    private const val DEFAULT_PUNT_MS = 3600000L
    private const val PING_TIMEOUT_MS = 300
    private const val HTTP_OK = 200
    private const val MAX_SCAN = 30
    const val DEFAULT_PORT = 7868

    private var socket: Socket? = null

    fun getLocalIpAddress(): String? = runCatching {
        val addrs = java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { !it.isLoopback && it.isUp }
            .flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            .map { it.hostAddress }

        addrs.firstOrNull { it?.startsWith("192.168.") == true }
            ?: addrs.firstOrNull { it?.startsWith("10.") == true && !it.startsWith("10.0.2.") }
            ?: addrs.firstOrNull { it?.startsWith("172.") == true }
            ?: addrs.firstOrNull()
    }.getOrNull()

    fun discoverAndConnect(
        context: Context,
        customEndpoint: String?,
        pin: String?,
        onStatus: ((String) -> Unit)? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val raw = customEndpoint?.trim()
        if (!raw.isNullOrBlank()) {
            val s = if (!raw.startsWith("http://") && !raw.startsWith("https://")) "http://$raw" else raw
            val port = runCatching { java.net.URI(s).port }.getOrDefault(-1)
            val target = if (port == -1) "$s:$DEFAULT_PORT" else s
            onStatus?.invoke("Connecting to $target...")
            CoroutineScope(Dispatchers.IO).launch {
                val ok = pingUrl(target)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (ok) connect(context, target, pin)
                    onResult?.invoke(ok, target)
                }
            }
        } else {
            onStatus?.invoke("Scanning local network...")
            val ip = getLocalIpAddress()
            val fallback = if (ip != null) "http://$ip:$DEFAULT_PORT" else "http://10.0.2.2:$DEFAULT_PORT"
            CoroutineScope(Dispatchers.IO).launch {
                val prefix = ip?.substringBeforeLast(".")
                val list = mutableListOf(fallback)
                if (prefix != null) {
                    list.add("http://$prefix.1:$DEFAULT_PORT")
                    for (i in 2..MAX_SCAN) list.add("http://$prefix.$i:$DEFAULT_PORT")
                }
                val found = list.firstOrNull { pingUrl(it) } ?: fallback
                val ok = pingUrl(found)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (ok) connect(context, found, pin)
                    onResult?.invoke(ok, found)
                }
            }
        }
    }

    private fun pingUrl(urlStr: String): Boolean = runCatching {
        val conn = (java.net.URL("$urlStr/api/ping").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = PING_TIMEOUT_MS
            readTimeout = PING_TIMEOUT_MS
            requestMethod = "GET"
        }
        val ok = conn.responseCode == HTTP_OK
        conn.disconnect()
        ok
    }.getOrDefault(false)

    fun isConnected(): Boolean = socket?.connected() == true

    fun connect(context: Context, serverUrl: String, token: String? = null) {
        disconnect()
        try {
            val version = "0.0.7"
            val opts = IO.Options().apply {
                query = "client=phone&version=$version" + (if (!token.isNullOrBlank()) "&token=$token" else "")
                transports = arrayOf("websocket", "polling")
                reconnection = true
            }
            socket = IO.socket(serverUrl, opts).apply {
                on(Socket.EVENT_CONNECT) {
                    AppLogger.log(context, "ServerSyncManager", "Connected to Punt server via Socket.IO")
                    pushTasksDump(context)
                }
                on("mutation_request") { args ->
                    if (args.isNotEmpty() && args[0] is JSONObject) {
                        handleMutationRequest(context, args[0] as JSONObject)
                    }
                }
                on(Socket.EVENT_DISCONNECT) {
                    AppLogger.log(context, "ServerSyncManager", "Disconnected from Punt server")
                }
                connect()
            }
        } catch (_: java.net.URISyntaxException) {
            AppLogger.log(context, "ServerSyncManager", "Invalid server URI")
        }
    }

    fun disconnect() {
        runCatching { socket?.disconnect() }
        socket = null
    }

    fun pushTasksDump(context: Context) {
        val s = socket ?: return
        if (!s.connected()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PuntDatabase.getInstance(context)
                val reminders = db.reminderDao().getAllReminders()
                val jsonArray = serializeReminders(reminders)
                val payload = JSONObject().apply {
                    put("version", "0.0.7")
                    put("tasks", jsonArray)
                }
                s.emit("tasks_dump", payload)
            } catch (_: Exception) {}
        }
    }

    private fun serializeReminders(reminders: List<ReminderEntity>): JSONArray = JSONArray().apply {
        for (r in reminders) {
            put(
                JSONObject().apply {
                    put("id", r.id)
                    put("text", r.text)
                    put("createdAt", r.createdAt)
                    if (r.snoozeUntil != null) put("snoozeUntil", r.snoozeUntil)
                    put("isDone", r.isDone)
                    if (r.recurrenceInterval != null) put("recurrenceInterval", r.recurrenceInterval)
                    put("updatedAt", r.updatedAt)
                }
            )
        }
    }

    private suspend fun applyAction(dao: ReminderDao, action: String, payload: JSONObject?) {
        val target = payload?.optString("target") ?: ""
        val id = target.toLongOrNull()
        val entity = if (id != null) dao.getAllReminders().find { it.id == id } else dao.getReminderByText(target)

        when (action) {
            "create" -> {
                val text = payload?.optString("text")?.trim().orEmpty()
                if (text.isNotBlank()) {
                    val recurrence = CreateReminderReceiver.parseRecurrenceDuration(text)?.second
                    dao.insert(ReminderEntity(text = text, recurrenceInterval = recurrence))
                }
            }
            "done" -> if (entity != null) {
                dao.update(entity.copy(isDone = true, updatedAt = System.currentTimeMillis()))
            }
            "punt" -> if (entity != null) {
                val durationStr = payload?.optString("duration") ?: "1h"
                val snoozeMs = CreateReminderReceiver.parseSnoozeDuration(durationStr)?.first ?: DEFAULT_PUNT_MS
                val snoozeUntil = System.currentTimeMillis() + snoozeMs
                dao.update(entity.copy(snoozeUntil = snoozeUntil, updatedAt = System.currentTimeMillis()))
            }
            "delete" -> if (entity != null) {
                dao.deleteByText(entity.text)
            }
        }
    }

    private fun handleMutationRequest(context: Context, request: JSONObject) {
        val s = socket ?: return
        val mutationId = request.optString("mutationId")
        val action = request.optString("action")
        val payload = request.optJSONObject("payload")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PuntDatabase.getInstance(context)
                applyAction(db.reminderDao(), action, payload)
                val reminders = db.reminderDao().getAllReminders()
                val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                val set = mutableSetOf<String>()
                for (r in reminders) {
                    val text = if (r.isDone) "${r.text} #done" else r.text
                    set.add(text)
                    val cleanLower = r.text.lowercase()
                    if (r.snoozeUntil != null && r.snoozeUntil > 0L) {
                        editor.putLong("snooze_$cleanLower", r.snoozeUntil)
                    } else {
                        editor.remove("snooze_$cleanLower")
                    }
                }
                editor.putStringSet("key_reminders_list", set).apply()
                com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                TaskBackupManager.backupTasks(context)
                val jsonArray = serializeReminders(db.reminderDao().getAllReminders())

                val response = JSONObject().apply {
                    put("mutationId", mutationId)
                    put("success", true)
                    put("tasks", jsonArray)
                }
                s.emit("mutation_response", response)
            } catch (_: org.json.JSONException) {
                val errorResponse = JSONObject().apply {
                    put("mutationId", mutationId)
                    put("success", false)
                    put("error", "Mutation failed")
                }
                s.emit("mutation_response", errorResponse)
            }
        }
    }
}
