@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "EmptyFunctionBlock", "LargeClass", "LongMethod", "LoopWithTooManyJumpStatements", "MagicNumber", "MaxLineLength", "NestedBlockDepth", "ReturnCount", "TooManyFunctions", "UnusedPrivateMember", "UseRequire")
package com.bas080.notificationreminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bas080.notificationreminders.databinding.ActivityMainBinding
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val HEADER_ACTIVE_SECTION_MARKER = "HEADER_ACTIVE_SECTION_MARKER"
const val HEADER_SNOOZED_SECTION_MARKER = "HEADER_SNOOZED_SECTION_MARKER"

data class SectionState(
    val activeCount: Int = 0,
    val puntedCount: Int = 0,
    val isActiveCollapsed: Boolean = false,
    val isPuntedCollapsed: Boolean = false
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_SEARCH_QUERY = "key_search_query"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"
        private const val KEY_FOCUS_PROFILES = "key_focus_profiles"
        private const val KEY_ACTIVE_FOCUS_PROFILE = "key_active_focus_profile"
        private const val KEY_ACTIVE_FOCUS_PROFILES = "key_active_focus_profiles"
        private const val KEY_ACTIVE_COLLAPSED = "key_active_collapsed"
        private const val KEY_PUNTED_COLLAPSED = "key_punted_collapsed"

        fun getCleanTrimmed(reminderText: String): String {
            return removeDoneTag(reminderText).lowercase()
        }

        fun removeDoneTag(reminderText: String): String {
            return reminderText.replace(Regex("(?i)\\s*#done\\b"), "").trim()
        }

        fun formatSnoozeUntil(snoozeUntil: Long, now: Long = System.currentTimeMillis()): String {
            val snoozeCal = Calendar.getInstance().apply { timeInMillis = snoozeUntil }
            val nowCal = Calendar.getInstance().apply { timeInMillis = now }

            val sameYear = snoozeCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
            val dayOfYearDiff = snoozeCal.get(Calendar.DAY_OF_YEAR) - nowCal.get(Calendar.DAY_OF_YEAR)

            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val timeStr = timeFormat.format(snoozeCal.time)

            return when {
                sameYear && dayOfYearDiff == 0 -> "today at $timeStr"
                sameYear && dayOfYearDiff == 1 -> "tomorrow at $timeStr"
                sameYear && dayOfYearDiff in 2..6 -> {
                    val dayFormat = SimpleDateFormat("EEE 'at' HH:mm", Locale.US)
                    dayFormat.format(snoozeCal.time)
                }
                sameYear -> {
                    val dateFormat = SimpleDateFormat("MMM d 'at' HH:mm", Locale.US)
                    dateFormat.format(snoozeCal.time)
                }
                else -> {
                    val fullFormat = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.US)
                    fullFormat.format(snoozeCal.time)
                }
            }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private val activeReminders = mutableListOf<String>()
    private val displayedReminders = mutableListOf<String>()
    private val taskSnoozeByName = mutableMapOf<String, Long>()
    private lateinit var adapter: RemindersAdapter

    private var currentSearchQuery = ""
    private val activeFocusProfileNames = mutableSetOf<String>()
    var isActiveCollapsed = false
        private set
    var isPuntedCollapsed = false
        private set
    private val recentlyDoneReminders = mutableSetOf<String>()

    internal val swipeStateMachine = SwipeStateMachine()

    private val taskIdByCleanName = mutableMapOf<String, Long>()

    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val editReminderHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingEditRunnable: Runnable? = null
    private var pendingOldText: String? = null
    private var pendingUpdatedText: String? = null

    fun flushPendingReminderUpdate() {
        pendingEditRunnable?.let {
            editReminderHandler.removeCallbacks(it)
            it.run()
            pendingEditRunnable = null
        }
    }

    internal fun getDisplayedRemindersForTesting(): List<String> = displayedReminders.toList()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            checkAndRequestNotificationListenerPermission()
        }

    private val folderSyncLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                com.bas080.notificationreminders.sync.FolderSyncManager.setSyncFolder(this, uri)
                Toast.makeText(this, R.string.toast_folder_sync_enabled, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppLogger.log(this, "MainActivity", "onCreate called")

        setupNavigation()
        setupSearchInput()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSwipeGestures()
        setupClearInputButton()
        loadReminders()

        checkAndShowCrashReportDialog()
        checkAndRequestPermissions()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.accent)
        binding.swipeRefreshLayout.setOnRefreshListener {
            finalizePendingSwipes()
            recentlyDoneReminders.clear()
            loadReminders()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupNavigation() {
        markAsButtonAccessibility(binding.btnMenu)

        markAsButtonAccessibility(binding.btnEmptyClearFilter)

        binding.btnEmptyClearFilter.setOnClickListener {
            clearFilterAndSearchAction()
        }

        markAsButtonAccessibility(binding.btnEmptyClearFocus)

        binding.btnEmptyClearFocus.setOnClickListener {
            clearFocusProfileAction()
        }

        markAsButtonAccessibility(binding.btnMenuFocusProfiles)
        markAsButtonAccessibility(binding.btnMenuPuntAll)
        markAsButtonAccessibility(binding.btnMenuShareMatching)
        markAsButtonAccessibility(binding.btnMenuAbout)

        val toggleMenu = {
            val isCurrentlyExpanded = binding.menuContainer.visibility == View.VISIBLE
            binding.menuContainer.visibility = if (!isCurrentlyExpanded) View.VISIBLE else View.GONE
        }

        val collapseMenu = {
            binding.menuContainer.visibility = View.GONE
        }

        binding.btnMenu.setOnClickListener {
            toggleMenu()
        }

        binding.remindersContainer.setOnClickListener {
            collapseMenu()
        }

        binding.btnMenuFocusProfiles.setOnClickListener {
            collapseMenu()
            showFocusProfilesDialog()
        }

        binding.btnMenuPuntAll.setOnClickListener {
            collapseMenu()
            val activeList = displayedReminders.filter {
                it != HEADER_ACTIVE_SECTION_MARKER && it != HEADER_SNOOZED_SECTION_MARKER && !it.contains("#done", ignoreCase = true)
            }
            showBulkSnoozeDialog(activeList)
        }

        binding.btnMenuShareMatching.setOnClickListener {
            collapseMenu()
            val activeList = displayedReminders.filter {
                it != HEADER_ACTIVE_SECTION_MARKER && it != HEADER_SNOOZED_SECTION_MARKER && !it.contains("#done", ignoreCase = true)
            }
            showExportOptionsDialog(activeList)
        }

        binding.btnMenuAbout.setOnClickListener {
            collapseMenu()
            showAboutDialog()
        }
    }

    fun showAboutDialog() {
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextAppearance(android.R.style.TextAppearance_Large)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        container.addView(titleTv)

        val versionText = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            getString(R.string.app_version_format, pInfo.versionName)
        } catch (_: Exception) {
            getString(R.string.app_version_format, "1.0")
        }

        val versionTv = TextView(this).apply {
            text = versionText
            setTextAppearance(android.R.style.TextAppearance_Small)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, (2 * density).toInt(), 0, (12 * density).toInt())
        }
        container.addView(versionTv)

        val accentColor = ContextCompat.getColor(this, R.color.accent)

        fun buildActionRow(titleText: String, descriptionText: String): android.widget.LinearLayout {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                isClickable = true
                isFocusable = true
            }
            val titleTv = TextView(this).apply {
                text = titleText
                setTextColor(accentColor)
                setTextAppearance(android.R.style.TextAppearance_Small)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val descriptionTv = TextView(this).apply {
                text = descriptionText
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setTextAppearance(android.R.style.TextAppearance_Small)
            }
            row.addView(titleTv)
            row.addView(descriptionTv)
            return row
        }

        val btnImport = buildActionRow(getString(R.string.import_markdown), getString(R.string.about_desc_import))
        val btnExport = buildActionRow(getString(R.string.export_markdown), getString(R.string.about_desc_export))
        val btnServerSync = buildActionRow("Server Sync", getString(R.string.about_desc_server_sync))
        val btnDonate = buildActionRow(getString(R.string.donate), getString(R.string.about_desc_donate))
        val btnFeedback = buildActionRow(getString(R.string.feedback), getString(R.string.about_desc_feedback))

        val folderSyncRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            isClickable = true
            isFocusable = true
        }
        val folderSyncTitleTv = TextView(this).apply {
            text = getString(R.string.folder_sync)
            setTextColor(accentColor)
            setTextAppearance(android.R.style.TextAppearance_Small)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val folderSyncDescTv = TextView(this).apply {
            text = if (com.bas080.notificationreminders.sync.FolderSyncManager.isEnabled(this@MainActivity)) {
                getString(R.string.folder_sync_desc_on)
            } else {
                getString(R.string.folder_sync_desc_off)
            }
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setTextAppearance(android.R.style.TextAppearance_Small)
        }
        folderSyncRow.addView(folderSyncTitleTv)
        folderSyncRow.addView(folderSyncDescTv)

        container.addView(btnImport)
        container.addView(btnExport)
        container.addView(btnServerSync)
        container.addView(folderSyncRow)
        container.addView(btnDonate)
        container.addView(btnFeedback)

        val logsHeaderRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (6 * density).toInt())
        }

        val logsHeaderTv = TextView(this).apply {
            text = "APPLICATION LOGS"
            setTextAppearance(android.R.style.TextAppearance_Small)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val spaceView = View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f)
        }

        val btnClearLogs = TextView(this).apply {
            text = getString(R.string.clear_logs)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_danger))
            setTextAppearance(android.R.style.TextAppearance_Small)
            setTypeface(null, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
        }

        logsHeaderRow.addView(logsHeaderTv)
        logsHeaderRow.addView(spaceView)
        logsHeaderRow.addView(btnClearLogs)
        container.addView(logsHeaderRow)

        val logsTv = TextView(this).apply {
            val rawLogs = AppLogger.getLogs(this@MainActivity)
            text = if (rawLogs.isNotBlank()) rawLogs else "No logs available."
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (160 * density).toInt()
            )
            setBackgroundResource(R.drawable.bg_card)
            val p = (10 * density).toInt()
            setPadding(p, p, p, p)
            addView(logsTv)
        }
        container.addView(scrollView)

        val dialog = AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle("About Punt")
            .setView(container)
            .setPositiveButton("Close", null)
            .create()

        btnImport.setOnClickListener {
            dialog.dismiss()
            showImportMarkdownDialog()
        }

        btnExport.setOnClickListener {
            dialog.dismiss()
            showExportOptionsDialog(activeReminders)
        }

        btnFeedback.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, CrashReportActivity::class.java).apply {
                putExtra(CrashReportActivity.EXTRA_IS_FEEDBACK, true)
            }
            startActivity(intent)
        }

        btnDonate.setOnClickListener {
            val donateUri = android.net.Uri.parse("https://liberapay.com/bas080")
            val intent = Intent(Intent.ACTION_VIEW, donateUri)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to open donation link", Toast.LENGTH_SHORT).show()
            }
        }

        btnServerSync.setOnClickListener {
            showServerSyncDialog()
        }

        folderSyncRow.setOnClickListener {
            if (com.bas080.notificationreminders.sync.FolderSyncManager.isEnabled(this)) {
                com.bas080.notificationreminders.sync.FolderSyncManager.setEnabled(this, false)
                folderSyncDescTv.text = getString(R.string.folder_sync_desc_off)
                Toast.makeText(this, R.string.toast_folder_sync_disabled, Toast.LENGTH_SHORT).show()
            } else if (com.bas080.notificationreminders.sync.FolderSyncManager.getTreeUri(this) != null) {
                com.bas080.notificationreminders.sync.FolderSyncManager.setEnabled(this, true)
                folderSyncDescTv.text = getString(R.string.folder_sync_desc_on)
            } else {
                dialog.dismiss()
                folderSyncLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            }
        }

        btnClearLogs.setOnClickListener {
            AppLogger.clearLogs(this)
            logsTv.text = "No logs available."
            Toast.makeText(this, R.string.toast_logs_cleared, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showServerSyncDialog() {
        val density = resources.displayMetrics.density
        val localIp = com.bas080.notificationreminders.sync.ServerSyncManager.getLocalIpAddress() ?: "10.0.2.2"
        val defaultHint = "http://$localIp:7868"
        val serverPrefs = getSharedPreferences("server_sync_prefs", Context.MODE_PRIVATE)
        val savedEndpoint = serverPrefs.getString("key_last_server_endpoint", "") ?: ""

        val descriptionText = TextView(this).apply {
            text = "Optional. Defaults to local network ($defaultHint). Fill in only when deviating from default."
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setTextAppearance(android.R.style.TextAppearance_Small)
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val urlRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val urlInput = EditText(this).apply {
            hint = defaultHint
            setText(savedEndpoint)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClearUrl = android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_action_clear_circle)
            background = null
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            visibility = if (savedEndpoint.isNotEmpty()) View.VISIBLE else View.GONE
            setOnClickListener {
                urlInput.setText("")
                serverPrefs.edit().remove("key_last_server_endpoint").apply()
            }
        }

        urlInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                btnClearUrl.visibility = if (hasText) View.VISIBLE else View.GONE
                if (!hasText) serverPrefs.edit().remove("key_last_server_endpoint").apply()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        urlRow.addView(urlInput)
        urlRow.addView(btnClearUrl)

        val input = EditText(this).apply {
            hint = "4-digit Pairing PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        }

        val statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            setPadding(0, (8 * density).toInt(), 0, 0)
            visibility = View.GONE
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), 0)
            addView(descriptionText)
            addView(urlRow)
            addView(input)
            addView(statusText)
        }

        val dialog = AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle("Server Sync")
            .setView(container)
            .setPositiveButton("Connect", null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()

        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.setOnClickListener {
            val pin = input.text.toString().trim()
            val url = urlInput.text.toString().trim()

            positiveBtn.isEnabled = false
            urlInput.isEnabled = false
            input.isEnabled = false
            statusText.visibility = View.VISIBLE
            statusText.setTextColor(ContextCompat.getColor(this, R.color.accent))
            statusText.text = "Connecting..."

            com.bas080.notificationreminders.sync.ServerSyncManager.discoverAndConnect(
                this,
                url,
                pin,
                onStatus = { msg -> statusText.text = msg }
            ) { success, connectedUrl, failureReason ->
                if (success) {
                    if (url.isNotEmpty()) {
                        serverPrefs.edit().putString("key_last_server_endpoint", url).apply()
                    }
                    Toast.makeText(this, "Connected to $connectedUrl", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    positiveBtn.isEnabled = true
                    urlInput.isEnabled = true
                    input.isEnabled = true
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.accent_danger))
                    statusText.text = "Failed to connect to $connectedUrl: ${failureReason ?: "Unknown error"}"
                }
            }
        }
    }

    private fun setupClearInputButton() {
        markAsButtonAccessibility(binding.btnClearInput)
        binding.btnClearInput.setOnClickListener {
            binding.searchReminderInput.setText("")
            searchRunnable?.let { searchHandler.removeCallbacks(it) }
            currentSearchQuery = ""
            getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SEARCH_QUERY, "")
                .apply()
            recentlyDoneReminders.clear()
            updateSummaryAndAdapter()
        }
    }

    private fun setupSearchInput() {
        val submitActionWithCancel = {
            searchRunnable?.let { searchHandler.removeCallbacks(it) }
            val text = binding.searchReminderInput.text.toString().trim()
            if (text.isNotEmpty()) {
                com.bas080.notificationreminders.utils.ExperimentTracker.trackCreation(this, "app_input")
                if (!activeReminders.contains(text)) {
                    activeReminders.add(text)
                }
                currentSearchQuery = ""
                getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SEARCH_QUERY, "")
                    .apply()
                recentlyDoneReminders.clear()
                kotlinx.coroutines.runBlocking {
                    insertTaskIfMissing(removeDoneTag(text))
                }
                ReminderNotificationListenerService.instance?.postMatchNotification(text)
                binding.searchReminderInput.setText("")
                updateSummaryAndAdapter()
                Toast.makeText(this, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
            }
        }

        binding.searchReminderInput.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                && event.action == android.view.KeyEvent.ACTION_DOWN) {
                submitActionWithCancel()
                true
            } else {
                false
            }
        }

        binding.searchReminderInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER || event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                submitActionWithCancel()
                true
            } else {
                false
            }
        }

        binding.searchReminderInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val query = s?.toString() ?: ""
                binding.btnClearInput.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                val delay = 200L
                val runnable = Runnable {
                    if (currentSearchQuery != query) {
                        recentlyDoneReminders.clear()
                    }
                    currentSearchQuery = query
                    getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_SEARCH_QUERY, query)
                        .apply()
                    updateSummaryAndAdapter()
                }
                searchRunnable = runnable
                searchHandler.postDelayed(runnable, delay)
            }
            override fun afterTextChanged(s: Editable?) {
                val editable = s ?: return
                if (editable.contains('\n') || editable.contains('\r')) {
                    val sanitized = editable.toString().replace(Regex("[\\r\\n]+"), " ").trim()
                    binding.searchReminderInput.setText(sanitized)
                    binding.searchReminderInput.setSelection(sanitized.length)
                }
            }
        })
    }

    private fun clearFilterAndSearchAction() {
        currentSearchQuery = ""
        activeFocusProfileNames.clear()
        getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SEARCH_QUERY, "")
            .putString(KEY_ACTIVE_FOCUS_PROFILE, "")
            .putStringSet(KEY_ACTIVE_FOCUS_PROFILES, emptySet())
            .apply()
        recentlyDoneReminders.clear()
        binding.searchReminderInput.setText("")
        binding.btnClearInput.visibility = View.GONE
        updateSearchInputHint()
        updateSummaryAndAdapter()
    }

    private fun clearFocusProfileAction() {
        activeFocusProfileNames.clear()
        getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_FOCUS_PROFILE, "")
            .putStringSet(KEY_ACTIVE_FOCUS_PROFILES, emptySet())
            .apply()
        updateSearchInputHint()
        updateSummaryAndAdapter()
    }

    fun toggleActiveCollapsed() {
        isActiveCollapsed = !isActiveCollapsed
        getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE_COLLAPSED, isActiveCollapsed)
            .apply()
        updateSummaryAndAdapter()
    }

    fun togglePuntedCollapsed() {
        isPuntedCollapsed = !isPuntedCollapsed
        getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PUNTED_COLLAPSED, isPuntedCollapsed)
            .apply()
        updateSummaryAndAdapter()
    }

    private fun updateSearchInputHint() {
        if (activeFocusProfileNames.isNotEmpty()) {
            val namesStr = activeFocusProfileNames.joinToString(", ")
            binding.searchReminderInput.hint = "Focus: $namesStr"
        } else {
            binding.searchReminderInput.hint = "Add reminder (press ↵) or search..."
        }
    }

    private fun showExportOptionsDialog(reminders: List<String>) {
        if (reminders.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_reminders_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.export_markdown)
            .setMessage("Include punt state information in the exported Markdown?")
            .setPositiveButton("Include Punt Info") { _, _ ->
                performExport(reminders, includePuntInfo = true)
            }
            .setNegativeButton("Text Only") { _, _ ->
                performExport(reminders, includePuntInfo = false)
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun performExport(reminders: List<String>, includePuntInfo: Boolean) {
        val snoozeMap = mutableMapOf<String, Long>()
        for (reminder in activeReminders) {
            val cleanKey = getCleanTrimmed(reminder)
            val snoozeUntil = taskSnoozeByName[cleanKey] ?: 0L
            if (snoozeUntil > 0L) {
                snoozeMap[cleanKey] = snoozeUntil
            }
        }

        val markdownText = com.bas080.notificationreminders.utils.MarkdownRemindersUtil.exportToMarkdown(
            reminders = reminders,
            snoozeMap = snoozeMap,
            includePuntInfo = includePuntInfo,
            getCleanTrimmed = ::getCleanTrimmed
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, markdownText)
            putExtra(Intent.EXTRA_SUBJECT, "Reminders Export")
        }
        val chooserIntent = Intent.createChooser(shareIntent, "Export Reminders")
        startActivity(chooserIntent)
    }

    private fun showImportMarkdownDialog() {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            id = R.id.import_input
            hint = getString(R.string.import_dialog_hint)
            setLines(6)
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setPadding(padding, padding / 2, padding, 0)
        }

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text
                if (!clipText.isNullOrBlank()) {
                    input.setText(clipText)
                    input.setSelection(clipText.length)
                }
            }
        } catch (_: Exception) {
            // Ignore clipboard access exceptions gracefully
        }

        AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.import_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.import_button) { _, _ ->
                val markdownText = input.text.toString()
                val importedItems = com.bas080.notificationreminders.utils.MarkdownRemindersUtil.importFromMarkdown(markdownText)
                if (importedItems.isNotEmpty()) {
                    var addedCount = 0
                    val now = System.currentTimeMillis()

                    for (item in importedItems) {
                        val cleanKey = getCleanTrimmed(item.text)
                        var itemUpdatedOrAdded = false

                        if (!activeReminders.contains(item.text)) {
                            activeReminders.add(item.text)
                            itemUpdatedOrAdded = true
                        }

                        if (item.snoozeUntilMillis != null && item.snoozeUntilMillis > now) {
                            taskSnoozeByName[cleanKey] = item.snoozeUntilMillis
                            itemUpdatedOrAdded = true
                        }

                        if (itemUpdatedOrAdded) {
                            addedCount++
                        }
                    }

                    if (addedCount > 0) {
                        kotlinx.coroutines.runBlocking {
                            val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
                            for (item in importedItems) {
                                val cleanText = removeDoneTag(item.text)
                                val futureSnooze = item.snoozeUntilMillis?.takeIf { it > now }
                                val existing = dao.getReminderByText(cleanText)
                                val isDone = item.text.contains("#done", ignoreCase = true)
                                val recurrence = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseRecurrenceDuration(cleanText)?.second
                                if (existing != null) {
                                    dao.update(
                                        existing.copy(
                                            isDone = isDone || existing.isDone,
                                            snoozeUntil = futureSnooze ?: existing.snoozeUntil,
                                            recurrenceInterval = recurrence ?: existing.recurrenceInterval,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                } else {
                                    dao.insert(
                                        com.bas080.notificationreminders.data.ReminderEntity(
                                            text = cleanText,
                                            snoozeUntil = futureSnooze,
                                            isDone = isDone,
                                            recurrenceInterval = recurrence
                                        )
                                    )
                                }
                            }
                        }
                        ReminderNotificationListenerService.instance?.showStatusNotification()
                        com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(this)
                        com.bas080.notificationreminders.utils.TaskBackupManager.backupTasks(this)
                        updateSummaryAndAdapter()
                        Toast.makeText(this, getString(R.string.toast_imported_reminders, addedCount), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun markAsButtonAccessibility(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            displayedReminders,
            onUpdateReminder = { index, updatedText ->
                if (index in displayedReminders.indices) {
                    val oldText = displayedReminders[index]
                    val masterIdx = activeReminders.indexOf(oldText)
                    if (masterIdx != -1) {
                        if (oldText != updatedText) {
                            activeReminders[masterIdx] = updatedText
                            displayedReminders[index] = updatedText

                            pendingEditRunnable?.let { editReminderHandler.removeCallbacks(it) }
                            val initialOldText = pendingOldText ?: oldText
                            pendingOldText = initialOldText
                            pendingUpdatedText = updatedText

                            val runnable = Runnable {
                                val currentOldText = pendingOldText
                                val currentUpdatedText = pendingUpdatedText
                                pendingEditRunnable = null
                                pendingOldText = null
                                pendingUpdatedText = null

                                if (currentOldText != null && currentUpdatedText != null && currentOldText != currentUpdatedText) {
                                    AppLogger.log(this@MainActivity, "MainActivity", "Updated reminder text")
                                    com.bas080.notificationreminders.utils.ExperimentTracker.trackEditInterval(this@MainActivity)
                                    val oldNotifId = ReminderNotificationListenerService.getNotificationIdForReminder(currentOldText)
                                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                                    notificationManager?.cancel(oldNotifId)

                                    val oldTrimmed = getCleanTrimmed(currentOldText)
                                    val newTrimmed = getCleanTrimmed(currentUpdatedText)
                                    if (oldTrimmed != newTrimmed) {
                                        val snoozeTime = taskSnoozeByName[oldTrimmed]
                                        if (snoozeTime != null) {
                                            taskSnoozeByName.remove(oldTrimmed)
                                            taskSnoozeByName[newTrimmed] = snoozeTime
                                        }
                                        val taskId = taskIdByCleanName.remove(oldTrimmed)
                                        if (taskId != null) {
                                            taskIdByCleanName[newTrimmed] = taskId
                                        }
                                    }

                                    kotlinx.coroutines.runBlocking {
                                        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
                                        val existing = dao.getReminderByText(removeDoneTag(currentOldText))
                                        if (existing != null) {
                                            dao.update(
                                                existing.copy(
                                                    text = removeDoneTag(currentUpdatedText),
                                                    isDone = currentUpdatedText.contains("#done", ignoreCase = true),
                                                    recurrenceInterval = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseRecurrenceDuration(currentUpdatedText)?.second,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                    updateSummary()
                                }
                            }
                            pendingEditRunnable = runnable
                            editReminderHandler.postDelayed(runnable, 300L)
                        }
                    }
                }
            },
            onShareReminderRequested = { index ->
                if (index in displayedReminders.indices) {
                    val reminderText = displayedReminders[index]
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, reminderText)
                    }
                    val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.share))
                    startActivity(chooserIntent)
                }
            },
            onUndoReminderRequested = { index ->
                if (index in displayedReminders.indices) {
                    undoSwipeAction(displayedReminders[index])
                }
            },
            onUnpuntReminderRequested = { index ->
                if (index in displayedReminders.indices) {
                    unpuntReminder(displayedReminders[index])
                }
            }
        )
        binding.remindersList.layoutManager = LinearLayoutManager(this)
        binding.remindersList.adapter = adapter
    }

    internal fun createSwipeHandler(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.75f

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 3f

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder.itemViewType != RemindersAdapter.TYPE_ACTIVE_REMINDER) {
                    return 0
                }
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position in displayedReminders.indices &&
                    isPendingDelete(displayedReminders[position])
                ) {
                    return ItemTouchHelper.LEFT
                }
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position !in displayedReminders.indices) return
                val reminderText = displayedReminders[position]
                adapter.notifyItemChanged(position)

                val state = resolveCardState(reminderText)
                val line = swipeStateMachine.stateLine(
                    CreateReminderReceiver.parseRecurrenceDuration(reminderText) != null
                )
                val target = line.getOrNull(line.indexOf(state) +
                    if (direction == ItemTouchHelper.RIGHT) 1 else -1)

                when (direction) {
                    ItemTouchHelper.LEFT -> when {
                        state == SwipeStateMachine.CardState.PUNTED -> showSnoozeOptionsDialog(reminderText)
                        target == SwipeStateMachine.CardState.PUNTED -> showSnoozeOptionsDialog(reminderText)
                        target != null -> undoSwipeAction(reminderText)
                    }
                    ItemTouchHelper.RIGHT -> when (state) {
                        SwipeStateMachine.CardState.DELETED -> Unit
                        SwipeStateMachine.CardState.PUNTED -> undoSwipeAction(reminderText)
                        SwipeStateMachine.CardState.ACTIVE ->
                            if (CreateReminderReceiver.parseRecurrenceDuration(reminderText) != null) {
                                puntReminderByRecurrence(reminderText)
                            } else {
                                markReminderDone(reminderText)
                            }
                        SwipeStateMachine.CardState.AUTO_PUNTED -> markReminderDone(reminderText)
                        SwipeStateMachine.CardState.DONE -> requestDeleteReminder(reminderText)
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val position = viewHolder.bindingAdapterPosition
                val reminderText = if (position in displayedReminders.indices) displayedReminders[position] else null
                val background = ColorDrawable()
                val hMargin = (16 * resources.displayMetrics.density).toInt()

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f && reminderText != null) {
                    background.color = ContextCompat.getColor(this@MainActivity, R.color.bg_dark)
                    val iconRes = if (dX > 0) getRightSwipePeekIcon(reminderText) else getLeftSwipePeekIcon(reminderText)
                    val icon = if (iconRes != 0) ContextCompat.getDrawable(this@MainActivity, iconRes) else null
                    icon?.let {
                        val vMargin = (itemView.height - it.intrinsicHeight) / 2
                        val top = itemView.top + vMargin
                        val bottom = top + it.intrinsicHeight
                        if (dX > 0) {
                            background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                            background.draw(c)
                            val left = itemView.left + hMargin
                            it.setBounds(left, top, left + it.intrinsicWidth, bottom)
                        } else {
                            background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                            background.draw(c)
                            val right = itemView.right - hMargin
                            it.setBounds(right - it.intrinsicWidth, top, right, bottom)
                        }
                        it.draw(c)
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

    }

    private fun setupSwipeGestures() {
        val swipeHandler = createSwipeHandler()
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.remindersList)
    }

    private fun showSnoozeOptionsDialog(reminderText: String) {
        val cleanTrimmed = getCleanTrimmed(reminderText)
        val now = System.currentTimeMillis()
        val snoozeUntil = taskSnoozeByName[cleanTrimmed] ?: 0L
        val isSnoozed = snoozeUntil > now

        val choicesList = ReminderNotificationListenerService.getTopSnoozeChoices(this, reminderText).map { it.toString() }

        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }

        var dialogRef: AlertDialog? = null

        if (isSnoozed) {
            val unsnoozeTv = TextView(this).apply {
                text = getString(R.string.unsnooze)
                setTextAppearance(android.R.style.TextAppearance_Medium)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
                setPadding(0, (4 * density).toInt(), 0, (12 * density).toInt())
                setOnClickListener {
                    dialogRef?.dismiss()
                    pushSwipeHistory(reminderText, SwipeActionKind.PUNT, SwipeStateMachine.CardState.ACTIVE)
                    kotlinx.coroutines.runBlocking {
                        val unsnoozeDao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity)
                            .reminderDao()
                        resolveTaskId(reminderText)?.let { unsnoozeDao.updateSnoozeById(it, null) }
                    }
                    taskSnoozeByName.remove(cleanTrimmed)
                    ReminderNotificationListenerService.instance?.showStatusNotification()
                    updateSummaryAndAdapter()
                    Toast.makeText(this@MainActivity, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(unsnoozeTv)
        }

        val optionsList = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        for (choice in choicesList) {
            val itemTv = TextView(this).apply {
                text = choice
                setTextAppearance(android.R.style.TextAppearance_Medium)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                setOnClickListener {
                    dialogRef?.dismiss()
                    applySnoozeDurationFromSwipe(reminderText, choice)
                }
            }
            optionsList.addView(itemTv)
        }
        layout.addView(optionsList)

        val input = EditText(this).apply {
            id = R.id.import_input
            hint = CreateReminderReceiver.getSnoozeCustomHint(this@MainActivity)
            setSingleLine(true)
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
        }
        layout.addView(input)

        val dialog = AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.snooze_dialog_title)
            .setView(layout)
            .setPositiveButton(R.string.snooze) { _, _ ->
                val customInput = input.text.toString().trim()
                applySnoozeDurationFromSwipe(reminderText, customInput)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialogRef = dialog
        dialog.show()

        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn?.isEnabled = false

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                positiveBtn?.isEnabled = hasText
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showBulkSnoozeDialog(targets: List<String>) {
        if (targets.isEmpty()) {
            Toast.makeText(this, "No active reminders to punt", Toast.LENGTH_SHORT).show()
            return
        }
        val topChoices = ReminderNotificationListenerService.getTopSnoozeChoices(this).map { it.toString() }

        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }

        var dialogRef: AlertDialog? = null

        val optionsList = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        for (choice in topChoices) {
            val itemTv = TextView(this).apply {
                text = choice
                setTextAppearance(android.R.style.TextAppearance_Medium)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                setOnClickListener {
                    dialogRef?.dismiss()
                    applySnoozeDurationToTargets(targets, choice)
                }
            }
            optionsList.addView(itemTv)
        }
        layout.addView(optionsList)

        val input = EditText(this).apply {
            id = R.id.import_input
            hint = CreateReminderReceiver.getSnoozeCustomHint(this@MainActivity)
            setSingleLine(true)
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
        }
        layout.addView(input)

        val titleText = if (targets.size > 1) "Punt All Reminders" else "Punt Reminder"

        val dialog = AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(titleText)
            .setView(layout)
            .setPositiveButton(R.string.snooze) { _, _ ->
                val customInput = input.text.toString().trim()
                applySnoozeDurationToTargets(targets, customInput)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialogRef = dialog
        dialog.show()

        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn?.isEnabled = false

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                positiveBtn?.isEnabled = hasText
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applySnoozeDuration(reminderText: String, durationChoice: String) {
        applySnoozeDurationToTargets(listOf(reminderText), durationChoice)
    }

    internal fun applySnoozeDurationFromSwipe(reminderText: String, durationChoice: String) {
        val parseResult = CreateReminderReceiver.parseSnoozeDuration(durationChoice)
        if (parseResult != null &&
            resolveCardState(reminderText) == SwipeStateMachine.CardState.ACTIVE
        ) {
            pushSwipeHistory(reminderText, SwipeActionKind.PUNT, SwipeStateMachine.CardState.PUNTED)
        }
        applySnoozeDuration(reminderText, durationChoice)
    }

    private fun applySnoozeDurationToTargets(targets: List<String>, durationChoice: String) {
        val parseResult = CreateReminderReceiver.parseSnoozeDuration(durationChoice)
        val topChoices = ReminderNotificationListenerService.getTopSnoozeChoices(this).map { it.toString() }
        val isPreset = topChoices.contains(durationChoice)
        val choiceType = if (isPreset) "preset" else if (parseResult != null) "custom_valid" else "custom_invalid"
        com.bas080.notificationreminders.utils.ExperimentTracker.trackSnoozeChoice(this, choiceType)

        if (parseResult == null) {
            AppLogger.log(this, "MainActivity", "Failed to snooze: invalid duration '$durationChoice'")
            Toast.makeText(this, R.string.toast_invalid_snooze_input, Toast.LENGTH_SHORT).show()
            return
        }

        currentFocus?.clearFocus()

        val canonicalChoice = CreateReminderReceiver.canonicalizeSnoozeChoice(durationChoice)
        if (canonicalChoice != null) {
            val freqPrefs = getSharedPreferences(PREFS_SNOOZE_FREQ, Context.MODE_PRIVATE)
            val currentCount = freqPrefs.getLong("count_$canonicalChoice", 0L)
            freqPrefs.edit()
                .putLong(canonicalChoice, System.currentTimeMillis())
                .putLong("count_$canonicalChoice", currentCount + 1L)
                .apply()
        }

        val (snoozeMs, durationLabel) = parseResult
        val snoozeUntil = System.currentTimeMillis() + snoozeMs

        for (reminderText in targets) {
            val cleanTrimmed = getCleanTrimmed(reminderText)
            val cleanText = removeDoneTag(reminderText)
            taskSnoozeByName[cleanTrimmed] = snoozeUntil
            ReminderNotificationListenerService.recordTaskSnoozeChoice(this, reminderText, durationChoice)
            kotlinx.coroutines.runBlocking {
                val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
                val existing = dao.getReminderByText(cleanText)
                if (existing != null) {
                    dao.update(existing.copy(snoozeUntil = snoozeUntil, updatedAt = System.currentTimeMillis()))
                } else {
                    dao.insert(com.bas080.notificationreminders.data.ReminderEntity(text = cleanText, snoozeUntil = snoozeUntil))
                }
            }
        }

        ReminderNotificationListenerService.instance?.showStatusNotification()
        updateSummaryAndAdapter()

        AppLogger.log(this, "MainActivity", "Punted ${targets.size} reminder(s) for $durationLabel")
        val toastText = getString(R.string.toast_reminder_snoozed_duration, durationLabel)
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
    }

    private fun pushSwipeHistory(
        reminderText: String,
        kind: SwipeActionKind,
        toState: SwipeStateMachine.CardState
    ) {
        val taskId = resolveTaskId(reminderText) ?: return
        val priorEntity = if (kind == SwipeActionKind.DELETE) {
            kotlinx.coroutines.runBlocking {
                com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity)
                    .reminderDao()
                    .getReminderById(taskId)
            }
        } else {
            null
        }
        swipeStateMachine.record(
            taskId,
            SwipeTransition(
                kind = kind,
                toState = toState,
                priorSnoozeUntil = taskSnoozeByName[getCleanTrimmed(reminderText)],
                priorEntity = priorEntity,
                fromPuntedSection = reminderIsInPuntedSection(reminderText)
            )
        )
    }

    private fun resolveTaskId(reminderText: String): Long? {
        taskIdByCleanName[getCleanTrimmed(reminderText)]?.let { return it }
        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
        val candidates = listOfNotNull(
            removeDoneTag(reminderText),
            pendingOldText?.let { removeDoneTag(it) }
        ).distinct()
        return kotlinx.coroutines.runBlocking {
            candidates.firstNotNullOfOrNull { dao.getReminderByText(it)?.id }
        }
    }

    private fun reminderIsInPuntedSection(reminderText: String): Boolean {
        taskIdByCleanName[getCleanTrimmed(reminderText)]?.let { taskId ->
            swipeStateMachine.pendingFromPuntedSection(taskId)?.let { return it }
        }
        val snoozeUntil = taskSnoozeByName[getCleanTrimmed(reminderText)] ?: 0L
        return snoozeUntil > System.currentTimeMillis()
    }

    internal fun isSwipeUndoable(reminderText: String): Boolean {
        return resolveTaskId(reminderText)?.let { swipeStateMachine.canUndo(it) } ?: false
    }

    private fun committedCardState(reminderText: String): SwipeStateMachine.CardState {
        val snoozeUntil = taskSnoozeByName[getCleanTrimmed(reminderText)] ?: 0L
        return when {
            reminderText.contains("#done", ignoreCase = true) -> SwipeStateMachine.CardState.DONE
            snoozeUntil > System.currentTimeMillis() ->
                if (CreateReminderReceiver.parseRecurrenceDuration(reminderText) != null) {
                    SwipeStateMachine.CardState.AUTO_PUNTED
                } else {
                    SwipeStateMachine.CardState.PUNTED
                }
            else -> SwipeStateMachine.CardState.ACTIVE
        }
    }

    internal fun resolveCardState(reminderText: String): SwipeStateMachine.CardState {
        val taskId = resolveTaskId(reminderText) ?: return committedCardState(reminderText)
        return swipeStateMachine.pendingStateOf(taskId) ?: committedCardState(reminderText)
    }

    internal fun isPendingDelete(reminderText: String): Boolean {
        return resolveTaskId(reminderText)?.let { swipeStateMachine.pendingStateOf(it) == SwipeStateMachine.CardState.DELETED } ?: false
    }

    internal fun getLeftSwipePeekIcon(reminderText: String): Int {
        return when (resolveCardState(reminderText)) {
            SwipeStateMachine.CardState.ACTIVE, SwipeStateMachine.CardState.PUNTED -> R.drawable.ic_action_snooze
            SwipeStateMachine.CardState.AUTO_PUNTED,
            SwipeStateMachine.CardState.DONE,
            SwipeStateMachine.CardState.DELETED -> R.drawable.ic_action_undo
        }
    }

    internal fun getRightSwipePeekIcon(reminderText: String): Int {
        return when (resolveCardState(reminderText)) {
            SwipeStateMachine.CardState.DELETED -> 0
            SwipeStateMachine.CardState.PUNTED -> R.drawable.ic_action_undo
            SwipeStateMachine.CardState.AUTO_PUNTED -> R.drawable.ic_action_done
            SwipeStateMachine.CardState.DONE -> R.drawable.ic_action_delete
            SwipeStateMachine.CardState.ACTIVE ->
                if (CreateReminderReceiver.parseRecurrenceDuration(reminderText) != null) {
                    R.drawable.ic_action_repeat
                } else {
                    R.drawable.ic_action_done
                }
        }
    }

    private fun restoreEntrySnooze(taskId: Long, entry: SwipeTransition) {
        kotlinx.coroutines.runBlocking {
            val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
            dao.updateSnoozeById(taskId, entry.priorSnoozeUntil)
            val currentTask = dao.getReminderById(taskId)
            if (currentTask != null) {
                val currentClean = getCleanTrimmed(currentTask.text)
                if (entry.priorSnoozeUntil != null) {
                    taskSnoozeByName[currentClean] = entry.priorSnoozeUntil
                } else {
                    taskSnoozeByName.remove(currentClean)
                }
            }
        }
    }

    internal fun undoSwipeAction(reminderText: String) {
        flushPendingReminderUpdate()
        val taskId = resolveTaskId(reminderText)
        val entry = taskId?.let { swipeStateMachine.pop(it) }
        if (taskId == null || entry == null) {
            when (committedCardState(reminderText)) {
                SwipeStateMachine.CardState.DONE -> undoMarkDone(reminderText)
                SwipeStateMachine.CardState.PUNTED, SwipeStateMachine.CardState.AUTO_PUNTED -> {
                    if (taskId != null) {
                        kotlinx.coroutines.runBlocking {
                            com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity)
                                .reminderDao()
                                .updateSnoozeById(taskId, null)
                        }
                        taskSnoozeByName.remove(getCleanTrimmed(reminderText))
                        Toast.makeText(this, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> return
            }
            updateSummaryAndAdapter()
            return
        }
        when (entry.kind) {
            SwipeActionKind.DONE -> {
                restoreEntrySnooze(taskId, entry)
                undoMarkDone(reminderText)
            }
            SwipeActionKind.PUNT -> {
                restoreEntrySnooze(taskId, entry)
                Toast.makeText(this, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
            }
            SwipeActionKind.DELETE -> {
                val snapshot = entry.priorEntity
                if (snapshot != null) {
                    kotlinx.coroutines.runBlocking {
                        com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity)
                            .reminderDao()
                            .insert(snapshot)
                    }
                    val clean = getCleanTrimmed(snapshot.text)
                    if (snapshot.snoozeUntil != null) {
                        taskSnoozeByName[clean] = snapshot.snoozeUntil
                    } else {
                        taskSnoozeByName.remove(clean)
                    }
                }
                Toast.makeText(this, R.string.toast_reminder_undo_delete, Toast.LENGTH_SHORT).show()
            }
        }
        AppLogger.log(this, "MainActivity", "Undid swipe ${entry.kind.name.lowercase(Locale.US)}")
        updateSummaryAndAdapter()
    }

    private fun requestDeleteReminder(reminderText: String) {
        val idx = activeReminders.indexOf(reminderText)
        val taskId = resolveTaskId(reminderText)
        if (idx != -1 && taskId != null) {
            pushSwipeHistory(reminderText, SwipeActionKind.DELETE, SwipeStateMachine.CardState.DELETED)
            kotlinx.coroutines.runBlocking {
                com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity)
                    .reminderDao()
                    .deleteById(taskId)
            }

            val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancel(notificationId)

            updateSummaryAndAdapter()
            AppLogger.log(this, "MainActivity", "Deleted reminder")
            Toast.makeText(this, R.string.toast_reminder_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun puntReminderByRecurrence(reminderText: String) {
        val recurrenceResult = CreateReminderReceiver.parseRecurrenceDuration(reminderText)
        if (recurrenceResult == null) {
            markReminderDone(reminderText)
            return
        }
        val (snoozeMs, durationLabel) = recurrenceResult
        val snoozeUntil = System.currentTimeMillis() + snoozeMs
        val cleanTrimmed = getCleanTrimmed(reminderText)
        val taskId = resolveTaskId(reminderText) ?: return

        pushSwipeHistory(reminderText, SwipeActionKind.PUNT, SwipeStateMachine.CardState.AUTO_PUNTED)
        taskSnoozeByName[cleanTrimmed] = snoozeUntil
        kotlinx.coroutines.runBlocking {
            val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
            dao.updateSnoozeById(taskId, snoozeUntil)
        }

        val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.cancel(notificationId)

        ReminderNotificationListenerService.instance?.showStatusNotification()
        updateSummaryAndAdapter()
        AppLogger.log(this, "MainActivity", "Punted recurring reminder for $durationLabel")
        val toastText = getString(R.string.toast_reminder_snoozed_duration, durationLabel)
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
    }

    private fun finalizePendingSwipes() {
        swipeStateMachine.clearAll()
    }

    private fun markReminderDone(reminderText: String) {
        val idx = activeReminders.indexOf(reminderText)
        if (idx != -1) {
            com.bas080.notificationreminders.utils.ExperimentTracker.trackTaskLifecycle(this, "marked_done")
            pushSwipeHistory(reminderText, SwipeActionKind.DONE, SwipeStateMachine.CardState.DONE)

            val doneText = if (reminderText.contains("#done", ignoreCase = true)) {
                reminderText
            } else {
                "$reminderText #done"
            }
            activeReminders[idx] = doneText
            recentlyDoneReminders.add(doneText)
            kotlinx.coroutines.runBlocking {
                val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
                resolveTaskId(reminderText)?.let { taskId ->
                    dao.getReminderById(taskId)?.let { entity ->
                        dao.update(entity.copy(isDone = true, updatedAt = System.currentTimeMillis()))
                    }
                }
            }

            val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancel(notificationId)

            ReminderNotificationListenerService.instance?.showStatusNotification()
            updateSummaryAndAdapter()
            AppLogger.log(this, "MainActivity", "Marked reminder done")
            Toast.makeText(this, R.string.toast_reminder_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun undoMarkDone(doneReminderText: String) {
        val idx = activeReminders.indexOf(doneReminderText)
        if (idx != -1) {
            com.bas080.notificationreminders.utils.ExperimentTracker.trackTaskLifecycle(this, "mark_done_undone")
            val cleanText = removeDoneTag(doneReminderText)
            activeReminders[idx] = cleanText
            recentlyDoneReminders.remove(doneReminderText)
            kotlinx.coroutines.runBlocking {
                val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
                resolveTaskId(doneReminderText)?.let { taskId ->
                    dao.getReminderById(taskId)?.let { entity ->
                        dao.update(entity.copy(isDone = false, updatedAt = System.currentTimeMillis()))
                    }
                }
            }

            ReminderNotificationListenerService.instance?.showStatusNotification()
            updateSummaryAndAdapter()
            AppLogger.log(this, "MainActivity", "Undid mark done")
            Toast.makeText(this, "Mark done undone", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unpuntReminder(reminderText: String) {
        com.bas080.notificationreminders.utils.ExperimentTracker.trackTaskLifecycle(this, "unpunted")
        val cleanTrimmed = getCleanTrimmed(reminderText)
        taskSnoozeByName.remove(cleanTrimmed)
        kotlinx.coroutines.runBlocking {
            val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this@MainActivity).reminderDao()
            dao.updateSnooze(removeDoneTag(reminderText), null)
        }

        ReminderNotificationListenerService.instance?.showStatusNotification()
        updateSummaryAndAdapter()
        AppLogger.log(this, "MainActivity", "Unpunted reminder")
        Toast.makeText(this, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
    }

    private suspend fun insertTaskIfMissing(cleanText: String): Long? {
        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this).reminderDao()
        val existing = dao.getReminderByText(cleanText)
        if (existing != null) {
            taskIdByCleanName[getCleanTrimmed(cleanText)] = existing.id
            return existing.id
        }
        val recurrence = com.bas080.notificationreminders.receivers.CreateReminderReceiver.parseRecurrenceDuration(cleanText)?.second
        val id = dao.insert(com.bas080.notificationreminders.data.ReminderEntity(text = cleanText, recurrenceInterval = recurrence))
        if (id != -1L) {
            taskIdByCleanName[getCleanTrimmed(cleanText)] = id
        }
        return id
    }

    private fun loadReminders() {
        com.bas080.notificationreminders.utils.TaskBackupManager.restoreIfEmpty(this)
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedQuery = prefs.getString(KEY_SEARCH_QUERY, "") ?: ""
        currentSearchQuery = savedQuery
        val savedActiveSet = prefs.getStringSet(KEY_ACTIVE_FOCUS_PROFILES, null)
        activeFocusProfileNames.clear()
        if (savedActiveSet != null) {
            activeFocusProfileNames.addAll(savedActiveSet)
        } else {
            val legacySingle = prefs.getString(KEY_ACTIVE_FOCUS_PROFILE, null)
            if (!legacySingle.isNullOrBlank()) {
                activeFocusProfileNames.add(legacySingle)
            }
        }
        isActiveCollapsed = prefs.getBoolean(KEY_ACTIVE_COLLAPSED, false)
        isPuntedCollapsed = prefs.getBoolean(KEY_PUNTED_COLLAPSED, false)

        updateSearchInputHint()
        if (binding.searchReminderInput.text.toString() != savedQuery) {
            binding.searchReminderInput.setText(savedQuery)
        }
        binding.btnClearInput.visibility = if (binding.searchReminderInput.text.isNotEmpty()) View.VISIBLE else View.GONE

        val dao = com.bas080.notificationreminders.data.PuntDatabase.getInstance(this).reminderDao()
        val entities = kotlinx.coroutines.runBlocking { dao.getAllReminders() }
        activeReminders.clear()
        activeReminders.addAll(entities.map { entity ->
            if (entity.isDone) "${entity.text} #done" else entity.text
        })
        taskIdByCleanName.clear()
        for (entity in entities) {
            taskIdByCleanName[getCleanTrimmed(entity.text)] = entity.id
        }
        taskSnoozeByName.clear()
        for (entity in entities) {
            entity.snoozeUntil?.let { snoozeUntil ->
                taskSnoozeByName[getCleanTrimmed(entity.text)] = snoozeUntil
            }
        }
        updateSummaryAndAdapter()
        com.bas080.notificationreminders.utils.TaskBackupManager.backupTasks(this)
    }

    private fun updateSummaryAndAdapter() {
        if (binding.remindersList.isComputingLayout) {
            binding.remindersList.post { updateSummaryAndAdapter() }
            return
        }

        val layoutManager = binding.remindersList.layoutManager as? LinearLayoutManager
        val firstVisiblePos = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
        val topOffset = if (firstVisiblePos != RecyclerView.NO_POSITION) {
            layoutManager?.findViewByPosition(firstVisiblePos)?.top ?: 0
        } else 0

        if (currentFocus != binding.searchReminderInput) {
            currentFocus?.clearFocus()
        }

        val savedProfiles = getSavedFocusProfiles()
        val activeProfiles = savedProfiles.filter { p -> activeFocusProfileNames.any { it.equals(p.name, ignoreCase = true) } }

        val focusContainsDone = activeProfiles.any { it.query.contains("#done", ignoreCase = true) }
        val searchContainsDone = focusContainsDone || currentSearchQuery.contains("#done", ignoreCase = true)

        val statusFiltered = activeReminders.filter { reminder ->
            val isDone = reminder.contains("#done", ignoreCase = true)
            if (isDone) searchContainsDone || recentlyDoneReminders.contains(reminder) else true
        }

        val focusedFiltered = if (activeProfiles.isNotEmpty()) {
            val unionMatches = mutableSetOf<String>()
            for (p in activeProfiles) {
                if (p.query.isNotBlank()) {
                    val matchesForProfile = com.bas080.notificationreminders.utils.ReminderMatcher.filterSearchQueryTiered(statusFiltered, p.query, this)
                    unionMatches.addAll(matchesForProfile)
                } else {
                    unionMatches.addAll(statusFiltered)
                }
            }
            statusFiltered.filter { unionMatches.contains(it) }
        } else {
            statusFiltered
        }

        val filtered = if (currentSearchQuery.isNotBlank()) {
            val focusMatches = com.bas080.notificationreminders.utils.ReminderMatcher.filterSearchQueryTiered(focusedFiltered, currentSearchQuery, this)
            if (focusMatches.isEmpty() && activeProfiles.isNotEmpty()) {
                com.bas080.notificationreminders.utils.ReminderMatcher.filterSearchQueryTiered(statusFiltered, currentSearchQuery, this)
            } else {
                focusMatches
            }
        } else {
            focusedFiltered
        }

        val activeItems = mutableListOf<String>()
        val snoozedItems = mutableListOf<Pair<String, Long>>()

        for (reminder in filtered) {
            val cleanTrimmed = getCleanTrimmed(reminder)
            val snoozeUntil = taskSnoozeByName[cleanTrimmed] ?: 0L
            if (reminderIsInPuntedSection(reminder)) {
                snoozedItems.add(reminder to snoozeUntil)
            } else {
                activeItems.add(reminder)
            }
        }

        activeItems.reverse()
        snoozedItems.sortBy { it.second }

        val sorted = mutableListOf<String>()

        if (activeItems.isNotEmpty()) {
            sorted.add(HEADER_ACTIVE_SECTION_MARKER)
            if (!isActiveCollapsed) {
                sorted.addAll(activeItems)
            }
        }

        if (snoozedItems.isNotEmpty()) {
            sorted.add(HEADER_SNOOZED_SECTION_MARKER)
            if (!isPuntedCollapsed) {
                sorted.addAll(snoozedItems.map { it.first })
            }
        }

        val snoozeMap = mutableMapOf<String, Long>()
        for (reminder in activeReminders) {
            val cleanTrimmed = getCleanTrimmed(reminder)
            val snoozeUntil = taskSnoozeByName[cleanTrimmed] ?: 0L
            if (snoozeUntil > 0L) {
                snoozeMap[cleanTrimmed] = snoozeUntil
            }
        }

        val undoableSet = sorted.filter { isSwipeUndoable(it) }.map { getCleanTrimmed(it) }.toSet()
        val pendingDeleteSet = sorted.filter { isPendingDelete(it) }.map { getCleanTrimmed(it) }.toSet()
        adapter.updateList(
            sorted,
            snoozeMap,
            SectionState(
                activeCount = activeItems.size,
                puntedCount = snoozedItems.size,
                isActiveCollapsed = isActiveCollapsed,
                isPuntedCollapsed = isPuntedCollapsed
            ),
            undoableSet,
            pendingDeleteSet
        )
        updateSummary()

        if (firstVisiblePos != RecyclerView.NO_POSITION && firstVisiblePos < sorted.size) {
            layoutManager?.scrollToPositionWithOffset(firstVisiblePos, topOffset)
        }
    }

    private fun updateSummary() {
        val hasFilterOrSearch = currentSearchQuery.isNotBlank()

        if (displayedReminders.isEmpty()) {
            binding.layoutEmptyReminders.visibility = View.VISIBLE
            binding.btnEmptyClearFilter.visibility = if (hasFilterOrSearch) View.VISIBLE else View.GONE
            if (activeFocusProfileNames.isNotEmpty()) {
                binding.btnEmptyClearFocus.visibility = View.VISIBLE
                val namesStr = activeFocusProfileNames.joinToString(", ")
                binding.btnEmptyClearFocus.text = "Clear focus ($namesStr)"
            } else {
                binding.btnEmptyClearFocus.visibility = View.GONE
            }

            val instructions = "Press Enter to create task\nSwipe right on task to mark done\nSwipe left on task to punt\nUse #every 1d for recurring tasks"
            val spannable = android.text.SpannableStringBuilder(instructions)
            val tagMatch = Regex("#every 1d").find(instructions)
            if (tagMatch != null) {
                val start = tagMatch.range.first
                val end = tagMatch.range.last + 1
                val textColor = ContextCompat.getColor(this, R.color.text_secondary)
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(textColor),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.txtEmptyReminders.text = spannable
        } else {
            binding.layoutEmptyReminders.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
        if (isNotificationListenerEnabled()) {
            startReminderService()
        }
    }

    override fun onPause() {
        super.onPause()
        flushPendingReminderUpdate()
        com.bas080.notificationreminders.utils.TaskBackupManager.backupTasks(this)
    }

    private fun checkAndShowCrashReportDialog() {
        val prefs = getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = prefs.getString(App.KEY_CRASH_TRACE, null) ?: return

        val intent = Intent(this, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, crashTrace)
        }
        startActivity(intent)
    }

    private fun clearCrashTrace() {
        val prefs = getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(App.KEY_CRASH_TRACE).apply()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndRequestNotificationListenerPermission()
            }
        } else {
            checkAndRequestNotificationListenerPermission()
        }
    }

    private fun checkAndRequestNotificationListenerPermission() {
        if (!isNotificationListenerEnabled()) {
            showNotificationListenerDialog()
        } else {
            startReminderService()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledPackages.contains(packageName)
    }

    private fun showNotificationListenerDialog() {
        AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.app_name)
            .setMessage("Punt requires Notification Listener Access to monitor notifications and trigger your reminders.")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    data class FocusProfile(val name: String, val query: String)

    private fun getSavedFocusProfiles(): MutableList<FocusProfile> {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_FOCUS_PROFILES, null) ?: return mutableListOf()
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            val list = mutableListOf<FocusProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "")
                val query = obj.optString("query", "")
                if (name.isNotBlank()) {
                    list.add(FocusProfile(name, query))
                }
            }
            list
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveFocusProfiles(profiles: List<FocusProfile>) {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        for (p in profiles) {
            val obj = org.json.JSONObject()
            obj.put("name", p.name)
            obj.put("query", p.query)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_FOCUS_PROFILES, jsonArray.toString()).apply()
    }

    fun showFocusProfilesDialog() {
        val density = resources.displayMetrics.density
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)
        val textSecondaryColor = ContextCompat.getColor(this, R.color.text_secondary)
        val accentColor = ContextCompat.getColor(this, R.color.accent)

        val savedProfiles = getSavedFocusProfiles()

        class ProfileItemState(var originalName: String, var currentText: String, var isEnabled: Boolean)
        val itemStates = savedProfiles.map { p ->
            val fullText = if (p.query.isNotBlank()) "${p.name} ${p.query}" else p.name
            val enabled = activeFocusProfileNames.any { it.equals(p.name, ignoreCase = true) }
            ProfileItemState(p.name, fullText, enabled)
        }.toMutableList()

        val mainContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padHoriz = (24 * density).toInt()
            val padTop = (12 * density).toInt()
            setPadding(padHoriz, padTop, padHoriz, 0)
        }

        // Top input row for creating a new profile
        val createRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val editNew = android.widget.EditText(this).apply {
            hint = "Name #tag..."
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            textSize = 15f
            setSingleLine(true)
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnAdd = android.widget.TextView(this).apply {
            text = "+"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(accentColor)
            setPadding((16 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
        }

        createRow.addView(editNew)
        createRow.addView(btnAdd)
        mainContainer.addView(createRow)

        // List layout for profiles
        val listContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        fun updateItemTextHighlight(editText: android.widget.EditText, text: String) {
            val trimmed = text.trimStart()
            val parts = trimmed.split("\\s+".toRegex(), 2)
            val nameWord = if (parts.isNotEmpty()) parts[0] else ""
            if (nameWord.isNotBlank() && text.contains(nameWord)) {
                val spannable = android.text.SpannableStringBuilder(text)
                val start = text.indexOf(nameWord)
                val end = start + nameWord.length
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(accentColor),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val selStart = editText.selectionStart
                val selEnd = editText.selectionEnd
                editText.setText(spannable)
                if (selStart in 0..text.length && selEnd in 0..text.length) {
                    editText.setSelection(selStart, selEnd)
                }
            } else {
                editText.setTextColor(textPrimaryColor)
            }
        }

        fun rebuildListUI() {
            listContainer.removeAllViews()
            for (state in itemStates) {
                val row = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                }

                val rowEditText = android.widget.EditText(this@MainActivity).apply {
                    setTextColor(textPrimaryColor)
                    setHintTextColor(textSecondaryColor)
                    textSize = 16f
                    setSingleLine(true)
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setText(state.currentText)
                }

                updateItemTextHighlight(rowEditText, state.currentText)

                rowEditText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val newStr = s?.toString() ?: ""
                        state.currentText = newStr
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })

                val toggle = androidx.appcompat.widget.SwitchCompat(this@MainActivity).apply {
                    isChecked = state.isEnabled
                    setOnCheckedChangeListener { _, isChecked ->
                        state.isEnabled = isChecked
                    }
                }

                row.addView(rowEditText)
                row.addView(toggle)
                listContainer.addView(row)
            }
        }

        rebuildListUI()
        mainContainer.addView(listContainer)

        btnAdd.setOnClickListener {
            val text = editNew.text.toString().trim()
            if (text.isNotBlank()) {
                val parts = text.split("\\s+".toRegex(), 2)
                val name = parts[0]
                itemStates.add(ProfileItemState(name, text, true))
                editNew.setText("")
                rebuildListUI()
            }
        }

        val dialog = AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle("Focus Profiles")
            .setView(mainContainer)
            .setPositiveButton("Done") { _, _ ->
                val newProfiles = mutableListOf<FocusProfile>()
                val newActiveNames = mutableSetOf<String>()

                for (state in itemStates) {
                    val raw = state.currentText.trim()
                    if (raw.isNotBlank()) {
                        val parts = raw.split("\\s+".toRegex(), 2)
                        val name = parts[0]
                        val query = if (parts.size > 1) parts[1].trim() else ""
                        newProfiles.add(FocusProfile(name, query))
                        if (state.isEnabled) {
                            newActiveNames.add(name)
                        }
                    }
                }

                saveFocusProfiles(newProfiles)
                activeFocusProfileNames.clear()
                activeFocusProfileNames.addAll(newActiveNames)

                getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet(KEY_ACTIVE_FOCUS_PROFILES, newActiveNames)
                    .apply()

                updateSearchInputHint()
                updateSummaryAndAdapter()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(accentColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(textSecondaryColor)
            }
        }

        dialog.show()
    }

    private fun startReminderService() {
        try {
            ReminderNotificationListenerService.startService(this)
        } catch (_: Exception) {
        }
    }
}

class RemindersAdapter(
    private val displayedReminders: MutableList<String>,
    private val onUpdateReminder: (Int, String) -> Unit,
    private val onShareReminderRequested: (Int) -> Unit,
    private val onUndoReminderRequested: (Int) -> Unit = {},
    private val onUnpuntReminderRequested: (Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_ACTIVE_REMINDER = 1
        const val TYPE_FOOTER_INSTRUCTIONS = 2
        const val TYPE_SECTION_HEADER = 3
    }

    private val currentSnoozeMap = mutableMapOf<String, Long>()
    private var sectionState = SectionState()
    private var undoableKeys: Set<String> = emptySet()
    private var pendingDeleteKeys: Set<String> = emptySet()

    private data class AdapterSnapshot(
        val list: List<String> = emptyList(),
        val snoozeMap: Map<String, Long> = emptyMap(),
        val sectionState: SectionState = SectionState(),
        val undoableKeys: Set<String> = emptySet(),
        val pendingDeleteKeys: Set<String> = emptySet()
    )

    private class RemindersDiffCallback(
        private val old: AdapterSnapshot,
        private val new: AdapterSnapshot
    ) : androidx.recyclerview.widget.DiffUtil.Callback() {

        private val oldList get() = old.list
        private val newList get() = new.list

        override fun getOldListSize(): Int = if (oldList.isEmpty()) 0 else oldList.size + 1
        override fun getNewListSize(): Int = if (newList.isEmpty()) 0 else newList.size + 1

        private fun getOldType(position: Int): Int {
            if (oldList.isNotEmpty() && position == oldList.size) return TYPE_FOOTER_INSTRUCTIONS
            val item = oldList.getOrNull(position)
            return if (item == HEADER_ACTIVE_SECTION_MARKER || item == HEADER_SNOOZED_SECTION_MARKER) TYPE_SECTION_HEADER else TYPE_ACTIVE_REMINDER
        }

        private fun getNewType(position: Int): Int {
            if (newList.isNotEmpty() && position == newList.size) return TYPE_FOOTER_INSTRUCTIONS
            val item = newList.getOrNull(position)
            return if (item == HEADER_ACTIVE_SECTION_MARKER || item == HEADER_SNOOZED_SECTION_MARKER) TYPE_SECTION_HEADER else TYPE_ACTIVE_REMINDER
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldType = getOldType(oldItemPosition)
            val newType = getNewType(newItemPosition)

            if (oldType != newType) return false

            return when (oldType) {
                TYPE_FOOTER_INSTRUCTIONS -> true
                TYPE_SECTION_HEADER -> oldList.getOrNull(oldItemPosition) == newList.getOrNull(newItemPosition)
                else -> oldList.getOrNull(oldItemPosition) == newList.getOrNull(newItemPosition)
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldType = getOldType(oldItemPosition)
            val newType = getNewType(newItemPosition)

            if (oldType != newType) return false

            return when (oldType) {
                TYPE_FOOTER_INSTRUCTIONS -> true
                TYPE_SECTION_HEADER -> {
                    oldList.getOrNull(oldItemPosition) == newList.getOrNull(newItemPosition) &&
                            old.sectionState == new.sectionState
                }
                else -> {
                    val oldItem = oldList.getOrNull(oldItemPosition) ?: return true
                    val newItem = newList.getOrNull(newItemPosition) ?: return true
                    if (oldItem != newItem) return false

                    val oldTrimmed = MainActivity.getCleanTrimmed(oldItem)
                    val newTrimmed = MainActivity.getCleanTrimmed(newItem)

                    if (old.undoableKeys.contains(oldTrimmed) != new.undoableKeys.contains(newTrimmed)) return false
                    if (old.pendingDeleteKeys.contains(oldTrimmed) != new.pendingDeleteKeys.contains(newTrimmed)) return false

                    val oldSnooze = old.snoozeMap[oldTrimmed] ?: 0L
                    val newSnooze = new.snoozeMap[newTrimmed] ?: 0L

                    oldSnooze == newSnooze
                }
            }
        }
    }

    fun updateList(
        newList: List<String>,
        newSnoozeMap: Map<String, Long> = emptyMap(),
        newSectionState: SectionState = SectionState(),
        newUndoableKeys: Set<String> = emptySet(),
        newPendingDeleteKeys: Set<String> = emptySet()
    ) {
        val diffCallback = RemindersDiffCallback(
            AdapterSnapshot(displayedReminders, currentSnoozeMap, this.sectionState, undoableKeys, pendingDeleteKeys),
            AdapterSnapshot(newList, newSnoozeMap, newSectionState, newUndoableKeys, newPendingDeleteKeys)
        )
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        displayedReminders.clear()
        displayedReminders.addAll(newList)
        currentSnoozeMap.clear()
        currentSnoozeMap.putAll(newSnoozeMap)
        this.sectionState = newSectionState
        this.undoableKeys = newUndoableKeys
        this.pendingDeleteKeys = newPendingDeleteKeys
        diffResult.dispatchUpdatesTo(this)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContainer: View = view.findViewById(R.id.card_container)
        val reminderInput: EditText = view.findViewById(R.id.reminder_input)
        val txtStatus: TextView = view.findViewById(R.id.txt_status)
        val btnShare: ImageView = view.findViewById(R.id.btn_share)
        val btnAction: ImageView = view.findViewById(R.id.btn_action)
        var textWatcher: TextWatcher? = null
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutSectionHeader: View = view.findViewById(R.id.layout_section_header)
        val txtHeaderTitle: TextView = view.findViewById(R.id.txt_header_title)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        if (displayedReminders.isNotEmpty() && position == displayedReminders.size) return TYPE_FOOTER_INSTRUCTIONS
        val item = displayedReminders[position]
        return if (item == HEADER_ACTIVE_SECTION_MARKER || item == HEADER_SNOOZED_SECTION_MARKER) TYPE_SECTION_HEADER else TYPE_ACTIVE_REMINDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOOTER_INSTRUCTIONS -> {
                val view = inflater.inflate(R.layout.item_footer_instructions, parent, false)
                FooterViewHolder(view)
            }
            TYPE_SECTION_HEADER -> {
                val view = inflater.inflate(R.layout.item_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_reminder, parent, false)
                ItemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            val item = displayedReminders[position]
            val context = holder.itemView.context
            if (item == HEADER_ACTIVE_SECTION_MARKER) {
                val arrow = if (sectionState.isActiveCollapsed) "▶" else "▼"
                holder.txtHeaderTitle.text = "$arrow ACTIVE (${sectionState.activeCount})"
                holder.layoutSectionHeader.setOnClickListener {
                    (context as? MainActivity)?.toggleActiveCollapsed()
                }
            } else if (item == HEADER_SNOOZED_SECTION_MARKER) {
                val arrow = if (sectionState.isPuntedCollapsed) "▶" else "▼"
                holder.txtHeaderTitle.text = "$arrow PUNTED (${sectionState.puntedCount})"
                holder.layoutSectionHeader.setOnClickListener {
                    (context as? MainActivity)?.togglePuntedCollapsed()
                }
            }
            return
        }
        if (holder !is ItemViewHolder) return

        holder.textWatcher?.let { holder.reminderInput.removeTextChangedListener(it) }

        ViewCompat.setAccessibilityDelegate(holder.btnAction, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })

        ViewCompat.setAccessibilityDelegate(holder.btnShare, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })

        val context = holder.itemView.context

        holder.reminderInput.setOnFocusChangeListener { _, hasFocus ->
            holder.reminderInput.maxLines = if (hasFocus) Int.MAX_VALUE else 4
            if (!hasFocus) {
                (context as? MainActivity)?.flushPendingReminderUpdate()
            }
        }

        val reminderIndex = position
        val reminderText = displayedReminders[reminderIndex]
        val isDone = reminderText.contains("#done", ignoreCase = true)

        val cleanTrimmed = MainActivity.getCleanTrimmed(reminderText)
        val now = System.currentTimeMillis()
        val snoozeUntil = currentSnoozeMap[cleanTrimmed] ?: 0L
        val isSnoozed = snoozeUntil > now
        val isUndoable = undoableKeys.contains(cleanTrimmed)
        val isPendingDelete = pendingDeleteKeys.contains(cleanTrimmed)

        val cardBackgroundRes = if (isPendingDelete) R.drawable.bg_card_delete else R.drawable.bg_card
        holder.cardContainer.setBackgroundResource(cardBackgroundRes)
        holder.cardContainer.tag = cardBackgroundRes

        holder.reminderInput.hint = "Reminder"
        val hasRecurrence = CreateReminderReceiver.parseRecurrenceDuration(reminderText) != null
        val hasNotify = reminderText.contains("#notify", ignoreCase = true)

        if ((hasRecurrence || hasNotify) && !isDone) {
            val spannable = android.text.SpannableStringBuilder(reminderText)

            if (hasRecurrence) {
                val tagMatches = Regex("(?i)#(?:every|repeat)(?:\\(([^)]+)\\)|[:/]?\\s*([^#\n]+)?)").findAll(reminderText)
                val textColor = ContextCompat.getColor(context, R.color.text_secondary)
                for (tagMatch in tagMatches) {
                    val start = tagMatch.range.first
                    var end = tagMatch.range.last + 1
                    while (end > start && reminderText[end - 1].isWhitespace()) {
                        end--
                    }
                    val span = android.text.style.ForegroundColorSpan(textColor)
                    spannable.setSpan(span, start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            if (hasNotify) {
                val notifyMatches = Regex("(?i)#notify(?:\\(([^)]+)\\)|[:/]?\\s*([^#\n]+)?)").findAll(reminderText)
                val accentSubtleColor = ContextCompat.getColor(context, R.color.accent_subtle)
                for (notifyMatch in notifyMatches) {
                    val start = notifyMatch.range.first
                    var end = notifyMatch.range.last + 1
                    while (end > start && reminderText[end - 1].isWhitespace()) {
                        end--
                    }
                    val span = android.text.style.ForegroundColorSpan(accentSubtleColor)
                    spannable.setSpan(span, start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            holder.reminderInput.setText(spannable, TextView.BufferType.SPANNABLE)
        } else {
            holder.reminderInput.setText(reminderText)
        }

        if (isDone || isUndoable) {
            holder.reminderInput.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            holder.reminderInput.alpha = 0.5f
            holder.reminderInput.paintFlags = holder.reminderInput.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.btnShare.visibility = View.GONE
            holder.btnAction.visibility = View.VISIBLE
            holder.btnAction.setImageResource(R.drawable.ic_action_undo)
            holder.btnAction.setColorFilter(ContextCompat.getColor(context, R.color.accent))
            holder.btnAction.contentDescription = if (isDone) "Undo mark done" else "Undo"
            holder.btnAction.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in displayedReminders.indices) {
                    onUndoReminderRequested(currentPos)
                }
            }
        } else {
            holder.reminderInput.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            holder.reminderInput.alpha = 1.0f
            holder.reminderInput.paintFlags = holder.reminderInput.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.btnAction.visibility = View.GONE
            holder.btnShare.visibility = View.VISIBLE
            holder.btnShare.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in displayedReminders.indices) {
                    onShareReminderRequested(currentPos)
                }
            }
        }

        if (isSnoozed && !isDone) {
            val formattedTime = MainActivity.formatSnoozeUntil(snoozeUntil, now)
            holder.txtStatus.visibility = View.VISIBLE
            holder.txtStatus.text = context.getString(R.string.snooze_status_format, formattedTime)
        } else {
            holder.txtStatus.visibility = View.GONE
        }


        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in displayedReminders.indices) {
                    onUpdateReminder(currentPos, s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        holder.reminderInput.addTextChangedListener(watcher)
        holder.textWatcher = watcher
    }

    override fun getItemCount(): Int = if (displayedReminders.isEmpty()) 0 else displayedReminders.size + 1
}

internal enum class SwipeActionKind { PUNT, DONE, DELETE }

internal data class SwipeTransition(
    val kind: SwipeActionKind,
    val toState: SwipeStateMachine.CardState,
    val priorSnoozeUntil: Long?,
    val priorEntity: com.bas080.notificationreminders.data.ReminderEntity?,
    val fromPuntedSection: Boolean
)

internal class SwipeStateMachine {

    internal enum class CardState { PUNTED, ACTIVE, AUTO_PUNTED, DONE, DELETED }

    internal val normalStateLine =
        listOf(CardState.PUNTED, CardState.ACTIVE, CardState.DONE, CardState.DELETED)

    internal val recurringStateLine =
        listOf(CardState.PUNTED, CardState.ACTIVE, CardState.AUTO_PUNTED, CardState.DONE, CardState.DELETED)

    internal fun stateLine(isRecurring: Boolean): List<CardState> =
        if (isRecurring) recurringStateLine else normalStateLine

    private val undoStacks = LinkedHashMap<Long, MutableList<SwipeTransition>>()

    fun canUndo(taskId: Long): Boolean = undoStacks[taskId]?.isNotEmpty() == true

    fun pendingStateOf(taskId: Long): CardState? = undoStacks[taskId]?.lastOrNull()?.toState

    fun peek(taskId: Long): SwipeTransition? = undoStacks[taskId]?.lastOrNull()

    fun record(taskId: Long, transition: SwipeTransition) {
        undoStacks.getOrPut(taskId) { mutableListOf() }.add(transition)
    }

    fun pop(taskId: Long): SwipeTransition? {
        val stack = undoStacks[taskId] ?: return null
        val transition = stack.removeAt(stack.size - 1)
        if (stack.isEmpty()) undoStacks.remove(taskId)
        return transition
    }

    fun pendingFromPuntedSection(taskId: Long): Boolean? = undoStacks[taskId]?.lastOrNull()?.fromPuntedSection

    fun undoableKeys(): Set<Long> = undoStacks.keys

    fun clearAll() = undoStacks.clear()
}
