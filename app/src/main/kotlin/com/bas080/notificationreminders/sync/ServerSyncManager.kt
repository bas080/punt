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

private const val HTTP_OK = 200
private const val PAIR_TIMEOUT_MS = 3000
private const val MAX_SCAN = 30

private fun Throwable.describe(): String = message?.let { "${javaClass.simpleName}: $it" } ?: javaClass.simpleName

private data class ServerSearchResult(val url: String?, val failure: String?)

private fun scanForServer(ip: String?, fallback: String): ServerSearchResult {
    val prefix = ip?.substringBeforeLast(".")
    val candidates = mutableListOf(fallback)
    if (prefix != null) {
        candidates.add("http://$prefix.1:${ServerSyncManager.DEFAULT_PORT}")
        for (index in 2..MAX_SCAN) candidates.add("http://$prefix.$index:${ServerSyncManager.DEFAULT_PORT}")
    }

    var failure: String? = null
    for (candidate in candidates) {
        val result = ServerSyncManager.pingUrl(candidate)
        if (result.isSuccess) return ServerSearchResult(candidate, null)
        failure = "$candidate: ${result.exceptionOrNull()?.describe()}"
    }
    return ServerSearchResult(null, failure)
}

private fun connectToCustomEndpoint(
    context: Context,
    endpoint: String,
    pin: String?,
    onResult: ((Boolean, String, String?) -> Unit)?
) {
    CoroutineScope(Dispatchers.IO).launch {
        val result = ServerSyncManager.pingUrl(endpoint).fold(
            onSuccess = { pairWithServer(endpoint, pin) },
            onFailure = { Result.failure(it) }
        )
        if (result.isSuccess) ServerSyncManager.connect(context, endpoint, result.getOrNull())
        onResult?.let { callback ->
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                callback(result.isSuccess, endpoint, result.exceptionOrNull()?.describe())
            }
        }
    }
}

internal fun pairWithServer(url: String, pin: String?): Result<String> {
    if (pin.isNullOrBlank()) return Result.failure(IllegalArgumentException("Pairing PIN is required"))

    var connection: java.net.HttpURLConnection? = null
    return runCatching {
        val pairingUrl = "${url.trimEnd('/')}/api/pair"
        connection = (java.net.URL(pairingUrl).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = PAIR_TIMEOUT_MS
            readTimeout = PAIR_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val requestBody = JSONObject().put("code", pin).toString().toByteArray(Charsets.UTF_8)
        connection!!.outputStream.use { it.write(requestBody) }
        val responseCode = connection!!.responseCode
        val responseStream = if (responseCode == HTTP_OK) connection!!.inputStream else connection!!.errorStream
        val responseBody = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val response = runCatching { JSONObject(responseBody) }.getOrNull()
        val token = response?.optString("token").orEmpty()
        val failure = when {
            responseCode != HTTP_OK -> {
                val reason = response?.optString("error").orEmpty().ifBlank { "Request failed" }
                "HTTP response $responseCode: $reason"
            }
            response == null -> "Invalid pairing response"
            !response.optBoolean("success") -> response.optString("error").ifBlank { "Pairing failed" }
            token.isBlank() -> "Pairing response did not contain a session token"
            else -> null
        }
        if (failure != null) throw java.io.IOException(failure)
        token
    }.also {
        connection?.disconnect()
    }
}

object ServerSyncManager {

    private const val DEFAULT_PUNT_MS = 3600000L
    private const val PING_TIMEOUT_MS = 300
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
        onResult: ((Boolean, String, String?) -> Unit)? = null
    ) {
        val raw = customEndpoint?.trim()
        if (!raw.isNullOrBlank()) {
            val schemeUrl = if (!raw.startsWith("http://") && !raw.startsWith("https://")) "http://$raw" else raw
            val url = schemeUrl.trimEnd('/')
            val port = runCatching { java.net.URI(url).port }.getOrDefault(-1)
            val target = if (port == -1) "$url:$DEFAULT_PORT" else url
            onStatus?.invoke("Connecting to $target...")
            connectToCustomEndpoint(context, target, pin, onResult)
            return
        }

        onStatus?.invoke("Scanning local network...")
        val ip = getLocalIpAddress()
        val fallback = if (ip != null) "http://$ip:$DEFAULT_PORT" else "http://10.0.2.2:$DEFAULT_PORT"
        CoroutineScope(Dispatchers.IO).launch {
            val search = scanForServer(ip, fallback)
            val pairing = search.url?.let { pairWithServer(it, pin) }
            if (search.url == null) {
                onResult?.let { callback ->
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        callback(false, fallback, search.failure ?: "No server responded")
                    }
                }
            } else {
                val result = pairing ?: Result.failure(IllegalStateException("Pairing did not run"))
                if (result.isSuccess) connect(context, search.url, result.getOrNull())
                onResult?.let { callback ->
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        callback(result.isSuccess, search.url, result.exceptionOrNull()?.describe())
                    }
                }
            }
        }
    }

    internal fun pingUrl(urlStr: String): Result<Unit> {
        var connection: java.net.HttpURLConnection? = null
        return runCatching {
            val pingUrl = "${urlStr.trimEnd('/')}/api/ping"
            connection = (java.net.URL(pingUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = PING_TIMEOUT_MS
                readTimeout = PING_TIMEOUT_MS
                requestMethod = "GET"
            }
            val responseCode = connection!!.responseCode
            if (responseCode != HTTP_OK) throw java.io.IOException("HTTP response $responseCode")
        }.also {
            connection?.disconnect()
        }
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun connect(context: Context, serverUrl: String, sessionToken: String? = null) {
        disconnect()
        try {
            val version = "0.0.7"
            val opts = IO.Options().apply {
                query = "client=phone&version=$version" +
                    (if (!sessionToken.isNullOrBlank()) "&token=$sessionToken" else "")
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
        val currentSocket = socket ?: return
        if (!currentSocket.connected()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PuntDatabase.getInstance(context)
                val reminders = db.reminderDao().getAllReminders()
                val jsonArray = serializeReminders(reminders)
                val payload = JSONObject().apply {
                    put("version", "0.0.7")
                    put("tasks", jsonArray)
                }
                currentSocket.emit("tasks_dump", payload)
            } catch (_: Exception) {}
        }
    }

    private fun serializeReminders(reminders: List<ReminderEntity>): JSONArray = JSONArray().apply {
        for (reminder in reminders) {
            put(
                JSONObject().apply {
                    put("id", reminder.id)
                    put("text", reminder.text)
                    put("createdAt", reminder.createdAt)
                    if (reminder.snoozeUntil != null) put("snoozeUntil", reminder.snoozeUntil)
                    put("isDone", reminder.isDone)
                    if (reminder.recurrenceInterval != null) put("recurrenceInterval", reminder.recurrenceInterval)
                    put("updatedAt", reminder.updatedAt)
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
                val duration = payload?.optString("duration") ?: "1h"
                val snoozeMs = CreateReminderReceiver.parseSnoozeDuration(duration)?.first ?: DEFAULT_PUNT_MS
                val snoozeUntil = System.currentTimeMillis() + snoozeMs
                dao.update(entity.copy(snoozeUntil = snoozeUntil, updatedAt = System.currentTimeMillis()))
            }
            "delete" -> if (entity != null) dao.deleteByText(entity.text)
        }
    }

    private fun handleMutationRequest(context: Context, request: JSONObject) {
        val currentSocket = socket ?: return
        val mutationId = request.optString("mutationId")
        val action = request.optString("action")
        val payload = request.optJSONObject("payload")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = PuntDatabase.getInstance(context).reminderDao()
                applyAction(dao, action, payload)
                val reminders = dao.getAllReminders()
                val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                val reminderSet = mutableSetOf<String>()
                for (reminder in reminders) {
                    reminderSet.add(if (reminder.isDone) "${reminder.text} #done" else reminder.text)
                    val key = reminder.text.lowercase()
                    if (reminder.snoozeUntil != null && reminder.snoozeUntil > 0L) {
                        editor.putLong("snooze_$key", reminder.snoozeUntil)
                    } else {
                        editor.remove("snooze_$key")
                    }
                }
                editor.putStringSet("key_reminders_list", reminderSet).apply()
                com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                TaskBackupManager.backupTasks(context)
                val response = JSONObject().apply {
                    put("mutationId", mutationId)
                    put("success", true)
                    put("tasks", serializeReminders(dao.getAllReminders()))
                }
                currentSocket.emit("mutation_response", response)
            } catch (_: org.json.JSONException) {
                val errorResponse = JSONObject().apply {
                    put("mutationId", mutationId)
                    put("success", false)
                    put("error", "Mutation failed")
                }
                currentSocket.emit("mutation_response", errorResponse)
            }
        }
    }
}
