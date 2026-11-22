package com.bas080.notificationreminders.sync

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
        val treeUri = getTreeUri(context) ?: return false
        if (!isEnabled(context)) return false
        return try {
            val markdown = buildSyncContent(context)
            if (markdown == lastSyncedContent) return true
            if (writeToTree(context, treeUri, markdown)) {
                lastSyncedContent = markdown
                AppLogger.log(context, "FolderSync", "Synced $SYNC_FILE_NAME")
                true
            } else {
                false
            }
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
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val documentId = findDocumentId(context, resolver, treeUri, rootDocId)
            ?: createDocument(context, resolver, rootUri)
            ?: return false
        return try {
            resolver.openOutputStream(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                "w"
            )?.use { stream ->
                stream.write(markdown.toByteArray(Charsets.UTF_8))
                true
            } ?: false.also { AppLogger.log(context, "FolderSync", "Could not open $SYNC_FILE_NAME") }
        } catch (e: SecurityException) {
            throw e
        }
    }

    private fun findDocument(
        context: Context,
        resolver: android.content.ContentResolver,
        treeUri: Uri,
        rootDocId: String
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
        return try {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == SYNC_FILE_NAME) {
                        return cursor.getString(0)
                    }
                }
                null
            }
        } catch (e: SecurityException) {
            throw e
        } catch (_: Exception) {
            AppLogger.log(context, "FolderSync", "Could not list sync folder")
            null
        }
    }

    private fun createDocument(
        context: Context,
        resolver: android.content.ContentResolver,
        rootUri: Uri
    ): String? {
        return try {
            DocumentsContract.createDocument(resolver, rootUri, SYNC_MIME_TYPE, SYNC_FILE_NAME)
                ?.let { DocumentsContract.getDocumentId(it) }
        } catch (e: SecurityException) {
            throw e
        } catch (_: Exception) {
            AppLogger.log(context, "FolderSync", "Could not create $SYNC_FILE_NAME")
            null
        }
    }

    internal fun resetForTest() {
        pendingSync?.let { mainHandler.removeCallbacks(it) }
        pendingSync = null
        lastSyncedContent = null
    }
}
