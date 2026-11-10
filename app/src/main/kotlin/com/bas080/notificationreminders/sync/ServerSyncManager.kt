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

    private const val PREFS_SERVER = "server_sync_prefs"
    private const val KEY_CODE = "key_pairing_code"
    private const val KEY_CODE_EXPIRY = "key_pairing_code_expiry"
    private const val EXPIRY_DURATION_MS = 15 * 60 * 1000L
    private const val CODE_MIN = 1000
    private const val CODE_MAX = 9999
    private const val DEFAULT_PUNT_MS = 3600000L

    private var socket: Socket? = null

    fun generatePairingCode(context: Context): String {
        val code = (CODE_MIN..CODE_MAX).random().toString()
        val expiry = System.currentTimeMillis() + EXPIRY_DURATION_MS
        val prefs = context.getSharedPreferences(PREFS_SERVER, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CODE, code)
            .putLong(KEY_CODE_EXPIRY, expiry)
            .apply()
        return code
    }

    fun getPairingCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_SERVER, Context.MODE_PRIVATE)
        val expiry = prefs.getLong(KEY_CODE_EXPIRY, 0L)
        if (System.currentTimeMillis() > expiry) {
            return null
        }
        return prefs.getString(KEY_CODE, null)
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun connect(context: Context, serverUrl: String, token: String? = null) {
        disconnect()
        try {
            val opts = IO.Options().apply {
                query = "client=phone" + (if (!token.isNullOrBlank()) "&token=$token" else "")
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
        try {
            socket?.disconnect()
            socket = null
        } catch (_: Exception) {}
    }

    fun pushTasksDump(context: Context) {
        val s = socket ?: return
        if (!s.connected()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PuntDatabase.getInstance(context)
                val reminders = db.reminderDao().getAllReminders()
                val jsonArray = serializeReminders(reminders)
                s.emit("tasks_dump", jsonArray)
            } catch (_: Exception) {}
        }
    }

    private fun serializeReminders(reminders: List<ReminderEntity>): JSONArray {
        val jsonArray = JSONArray()
        for (r in reminders) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("text", r.text)
                put("createdAt", r.createdAt)
                if (r.snoozeUntil != null) put("snoozeUntil", r.snoozeUntil)
                put("isDone", r.isDone)
                if (r.recurrenceInterval != null) put("recurrenceInterval", r.recurrenceInterval)
                put("updatedAt", r.updatedAt)
            }
            jsonArray.put(obj)
        }
        return jsonArray
    }

    private suspend fun applyAction(dao: ReminderDao, action: String, payload: JSONObject?) {
        val target = payload?.optString("target") ?: ""
        when (action) {
            "create" -> {
                val text = payload?.optString("text")?.trim() ?: ""
                if (text.isNotBlank()) {
                    val recurrence = CreateReminderReceiver.parseRecurrenceDuration(text)?.second
                    dao.insert(ReminderEntity(text = text, recurrenceInterval = recurrence))
                }
            }
            "done" -> {
                val id = target.toLongOrNull()
                val entity = if (id != null) dao.getReminderById(id) else dao.getReminderByText(target)
                if (entity != null) dao.update(entity.copy(isDone = true, updatedAt = System.currentTimeMillis()))
            }
            "punt" -> {
                val durationStr = payload?.optString("duration") ?: "1h"
                val snoozeMs = CreateReminderReceiver.parseSnoozeDuration(durationStr)?.first ?: DEFAULT_PUNT_MS
                val snoozeUntil = System.currentTimeMillis() + snoozeMs
                val id = target.toLongOrNull()
                val entity = if (id != null) dao.getReminderById(id) else dao.getReminderByText(target)
                if (entity != null) {
                    dao.update(entity.copy(snoozeUntil = snoozeUntil, updatedAt = System.currentTimeMillis()))
                }
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
