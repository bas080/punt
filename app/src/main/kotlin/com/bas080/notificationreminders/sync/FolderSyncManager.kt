package com.bas080.notificationreminders.sync

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.bas080.notificationreminders.data.PuntDatabase
import com.bas080.notificationreminders.utils.AppLogger
import com.bas080.notificationreminders.utils.MarkdownRemindersUtil
import kotlinx.coroutines.runBlocking

object FolderSyncManager {

    private const val PREFS_NAME = "folder_sync_prefs"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_ENABLED = "enabled"
    internal const val SYNC_FILE_NAME = "punt.md"
    private const val SYNC_MIME_TYPE = "text/markdown"
    private const val SYNC_DEBOUNCE_MS = 500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSync: Runnable? = null

    @Volatile
    private var lastSyncedContent: String? = null

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false) && prefs.getString(KEY_TREE_URI, null) != null
    }

    fun getTreeUri(context: Context): Uri? {
        val uri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null)
        return uri?.let { Uri.parse(it) }
    }

    fun requestSync(context: Context, delayMs: Long = SYNC_DEBOUNCE_MS) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestSync(context, delayMs) }
            return
        }
        if (!isEnabled(context)) return
        pendingSync?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { syncNow(context) }
        pendingSync = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    internal fun setSyncFolder(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            AppLogger.log(context, "FolderSync", "Could not persist folder permission: ${e.message}")
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putBoolean(KEY_ENABLED, true)
            .apply()
        requestSync(context, 0L)
    }

    internal fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) requestSync(context, 0L)
    }

    internal fun syncNow(context: Context): Boolean {
        pendingSync = null
        val treeUri = getTreeUri(context)
        if (treeUri == null || !isEnabled(context)) return false
        return try {
            val markdown = buildSyncContent(context)
            val synced = markdown == lastSyncedContent || writeToTree(context, treeUri, markdown)
            if (synced) {
                lastSyncedContent = markdown
                AppLogger.log(context, "FolderSync", "Synced $SYNC_FILE_NAME")
            } else {
                AppLogger.log(context, "FolderSync", "Folder sync failed")
            }
            synced
        } catch (e: SecurityException) {
            AppLogger.log(context, "FolderSync", "Folder permission revoked: ${e.message}")
            setEnabled(context, false)
            false
        } catch (_: Exception) {
            AppLogger.log(context, "FolderSync", "Folder sync failed")
            false
        }
    }

    private fun buildSyncContent(context: Context): String {
        val reminders = runBlocking {
            PuntDatabase.getInstance(context).reminderDao().getAllReminders()
        }
        val texts = reminders.map { if (it.isDone) "${it.text} #done" else it.text }
        val snoozeMap = reminders
            .mapNotNull { reminder -> reminder.snoozeUntil?.let { reminder.text.trim().lowercase() to it } }
            .toMap()
        return MarkdownRemindersUtil.exportToMarkdown(texts, snoozeMap, includePuntInfo = true)
    }

    private fun writeToTree(context: Context, treeUri: Uri, markdown: String): Boolean {
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val documentId = resolveSyncDocument(resolver, childrenUri, rootUri)
            ?: run {
                AppLogger.log(context, "FolderSync", "Could not resolve $SYNC_FILE_NAME in the sync folder")
                null
            }
        val documentUri = documentId?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it) }
        val stream = documentUri?.let { resolver.openOutputStream(it, "w") }
        if (stream == null) {
            AppLogger.log(context, "FolderSync", "Could not open $SYNC_FILE_NAME for writing")
        }
        stream?.use { it.write(markdown.toByteArray(Charsets.UTF_8)) }
        return stream != null
    }

    private fun resolveSyncDocument(resolver: ContentResolver, childrenUri: Uri, rootUri: Uri): String? {
        val foundId = resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            var matchId: String? = null
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == SYNC_FILE_NAME) {
                    matchId = cursor.getString(0)
                    break
                }
            }
            matchId
        }
        if (foundId != null) return foundId
        return DocumentsContract.createDocument(resolver, rootUri, SYNC_MIME_TYPE, SYNC_FILE_NAME)
            ?.let { DocumentsContract.getDocumentId(it) }
    }

    internal fun resetForTest() {
        pendingSync?.let { mainHandler.removeCallbacks(it) }
        pendingSync = null
        lastSyncedContent = null
    }
}
