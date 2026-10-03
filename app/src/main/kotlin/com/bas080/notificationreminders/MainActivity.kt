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

enum class ReminderFilter { ALL, ACTIVE, SNOOZED }

const val HEADER_SNOOZED_SECTION_MARKER = "HEADER_SNOOZED_SECTION_MARKER"

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val KEY_REMINDER_FILTER = "key_reminder_filter"
        private const val KEY_SEARCH_QUERY = "key_search_query"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"

        fun getCleanTrimmed(reminderText: String): String {
            return reminderText.replace(Regex("(?i)\\s*#done\\b"), "").trim().lowercase()
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
    private lateinit var adapter: RemindersAdapter

    private var currentFilter = ReminderFilter.ALL
    private var currentSearchQuery = ""
    private val recentlyDoneReminders = mutableSetOf<String>()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            checkAndRequestNotificationListenerPermission()
        }

    private val requestCalendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                com.bas080.notificationreminders.utils.CalendarSyncManager.setCalendarSyncEnabled(this, true)
                updateCalendarSyncButtonText()
                com.bas080.notificationreminders.utils.CalendarSyncManager.syncRemindersToCalendar(this)
                Toast.makeText(this, "Calendar Sync enabled", Toast.LENGTH_SHORT).show()
            } else {
                com.bas080.notificationreminders.utils.CalendarSyncManager.setCalendarSyncEnabled(this, false)
                updateCalendarSyncButtonText()
                showCalendarPermissionSettingsDialog()
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
        loadReminders()

        setupKeyboardListener()
        checkAndShowCrashReportDialog()
        checkAndRequestPermissions()
    }

    private fun setupKeyboardListener() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val isKeyboardOpen = keypadHeight > screenHeight * 0.15
            binding.headerNavigation.visibility = if (isKeyboardOpen) View.GONE else View.VISIBLE
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (isImeVisible) {
                binding.headerNavigation.visibility = View.GONE
            }
            insets
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            recentlyDoneReminders.clear()
            loadReminders()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupNavigation() {
        markAsButtonAccessibility(binding.btnNavReminders)
        markAsButtonAccessibility(binding.btnNavAbout)
        markAsButtonAccessibility(binding.btnClearLogs)
        markAsButtonAccessibility(binding.btnExportMarkdown)
        markAsButtonAccessibility(binding.btnListExport)
        markAsButtonAccessibility(binding.btnImportMarkdown)
        markAsButtonAccessibility(binding.btnDonate)
        markAsButtonAccessibility(binding.btnToggleCalendarSync)
        markAsButtonAccessibility(binding.btnFeedback)
        markAsButtonAccessibility(binding.btnTagsFilter)

        updateCalendarSyncButtonText()

        binding.btnToggleCalendarSync.setOnClickListener {
            val currentlyEnabled = com.bas080.notificationreminders.utils.CalendarSyncManager.isCalendarSyncEnabled(this)
            if (currentlyEnabled) {
                com.bas080.notificationreminders.utils.CalendarSyncManager.setCalendarSyncEnabled(this, false)
                updateCalendarSyncButtonText()
                Toast.makeText(this, "Calendar Sync disabled", Toast.LENGTH_SHORT).show()
            } else {
                if (com.bas080.notificationreminders.utils.CalendarSyncManager.hasCalendarPermission(this)) {
                    com.bas080.notificationreminders.utils.CalendarSyncManager.setCalendarSyncEnabled(this, true)
                    updateCalendarSyncButtonText()
                    com.bas080.notificationreminders.utils.CalendarSyncManager.syncRemindersToCalendar(this)
                    Toast.makeText(this, "Calendar Sync enabled", Toast.LENGTH_SHORT).show()
                } else {
                    requestCalendarPermissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    )
                }
            }
        }

        binding.btnDonate.setOnClickListener {
            val donateUri = android.net.Uri.parse("https://liberapay.com/bas080")
            val intent = Intent(Intent.ACTION_VIEW, donateUri)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to open donation link", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnListExport.setColorFilter(ContextCompat.getColor(this, R.color.accent))
        markAsButtonAccessibility(binding.btnClearSearch)
        markAsButtonAccessibility(binding.btnEmptyClearFilter)

        val clearFilterAndSearchAction = {
            currentSearchQuery = ""
            currentFilter = ReminderFilter.ALL
            getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_REMINDER_FILTER, currentFilter.name)
                .putString(KEY_SEARCH_QUERY, "")
                .apply()
            recentlyDoneReminders.clear()
            binding.searchReminderInput.setText("")
            updateSummaryAndAdapter()
        }

        binding.btnClearSearch.setOnClickListener {
            clearFilterAndSearchAction()
        }

        binding.btnEmptyClearFilter.setOnClickListener {
            clearFilterAndSearchAction()
        }

        binding.btnNavReminders.setOnClickListener {
            showRemindersView()
        }

        binding.btnNavAbout.setOnClickListener {
            showAboutView()
        }

        binding.btnFeedback.setOnClickListener {
            val intent = Intent(this, CrashReportActivity::class.java).apply {
                putExtra(CrashReportActivity.EXTRA_IS_FEEDBACK, true)
            }
            startActivity(intent)
        }

        binding.btnClearLogs.setOnClickListener {
            AppLogger.clearLogs(this)
            loadLogs()
            Toast.makeText(this, R.string.toast_logs_cleared, Toast.LENGTH_SHORT).show()
        }

        binding.btnExportMarkdown.setOnClickListener {
            exportRemindersToMarkdown()
        }

        binding.btnListExport.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(this, view)
            popup.menu.add("Punt All")
            popup.menu.add("Share All")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Punt All" -> {
                        val activeList = displayedReminders.filter {
                            it != HEADER_SNOOZED_SECTION_MARKER && !it.contains("#done", ignoreCase = true)
                        }
                        showBulkSnoozeDialog(activeList)
                        true
                    }
                    "Share All" -> {
                        exportRemindersToMarkdown()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        binding.btnImportMarkdown.setOnClickListener {
            showImportMarkdownDialog()
        }

        binding.btnTagsFilter.setOnClickListener {
            showTagsSelectionDialog()
        }

    }

    private fun setupSearchInput() {
        binding.searchReminderInput.setOnFocusChangeListener { _, hasFocus ->
            binding.searchReminderInput.maxLines = if (hasFocus) Int.MAX_VALUE else 4
        }

        val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var searchRunnable: Runnable? = null

        val submitActionWithCancel = {
            searchRunnable?.let { searchHandler.removeCallbacks(it) }
            val text = binding.searchReminderInput.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.searchReminderInput.setText("")
                activeReminders.add(text)
                currentSearchQuery = ""
                getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SEARCH_QUERY, "")
                    .apply()
                recentlyDoneReminders.clear()
                saveRemindersToPrefs()
                ReminderNotificationListenerService.instance?.postMatchNotification(text)
                updateSummaryAndAdapter()
                Toast.makeText(this, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddReminder.setOnClickListener {
            submitActionWithCancel()
        }

        binding.searchReminderInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
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
                searchRunnable = Runnable {
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
                searchHandler.postDelayed(searchRunnable!!, 200L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun sendFeedbackEmail() {
        val feedbackBody = StringBuilder().apply {
            append("## Feedback\n\n")
            append("[ Please type your feedback here ]\n\n")

            append("### Device Info\n")
            append(CrashReportActivity.getDiagnosticMetadata(this@MainActivity))
            append("\n")

            val logs = AppLogger.getLogs(this@MainActivity)
            if (logs.isNotBlank()) {
                append("### Application Logs\n")
                append("```\n")
                append(logs)
                append("\n```\n")
            }
        }.toString()

        val feedbackSubject = "Punt Feedback"
        val mailtoUri = "mailto:bas080@hotmail.com?subject=${android.net.Uri.encode(feedbackSubject)}&body=${android.net.Uri.encode(feedbackBody)}"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse(mailtoUri)
            putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
            putExtra(Intent.EXTRA_TEXT, feedbackBody)
        }
        val chooserIntent = Intent.createChooser(intent, getString(R.string.feedback))
        try {
            startActivity(chooserIntent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_email_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAllTags(): List<String> {
        val tagRegex = Regex("#[a-zA-Z0-9_]+")
        val tagCounts = mutableMapOf<String, Int>()
        for (reminder in activeReminders) {
            tagRegex.findAll(reminder).forEach { match ->
                val tag = match.value.lowercase()
                tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
            }
        }
        return tagCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    private fun showTagsSelectionDialog() {
        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }

        val stateLabel = TextView(this).apply {
            text = "State Filter"
            setTextAppearance(android.R.style.TextAppearance_Small)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        layout.addView(stateLabel)

        val radioGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }

        val accentColor = ContextCompat.getColor(this, R.color.accent)
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)
        val colorStateList = android.content.res.ColorStateList.valueOf(accentColor)

        val rbAll = android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            text = "All"
            setTextColor(textPrimaryColor)
            buttonTintList = colorStateList
            isChecked = currentFilter == ReminderFilter.ALL
        }
        val rbActive = android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            text = "Active"
            setTextColor(textPrimaryColor)
            buttonTintList = colorStateList
            isChecked = currentFilter == ReminderFilter.ACTIVE
        }
        val rbPunted = android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            text = "Punted"
            setTextColor(textPrimaryColor)
            buttonTintList = colorStateList
            isChecked = currentFilter == ReminderFilter.SNOOZED
        }

        radioGroup.addView(rbAll)
        radioGroup.addView(rbActive)
        radioGroup.addView(rbPunted)
        layout.addView(radioGroup)

        val allTags = extractAllTags()
        val checkedTagStates = BooleanArray(allTags.size) { i ->
            currentSearchQuery.contains(allTags[i], ignoreCase = true)
        }

        if (allTags.isNotEmpty()) {
            val tagsLabel = TextView(this).apply {
                text = "Tags Filter"
                setTextAppearance(android.R.style.TextAppearance_Small)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
            }
            layout.addView(tagsLabel)

            val tagsListView = android.widget.ListView(this).apply {
                choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
                adapter = object : android.widget.ArrayAdapter<String>(
                    this@MainActivity,
                    android.R.layout.simple_list_item_multiple_choice,
                    allTags.toTypedArray()
                ) {
                    override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                        val view = super.getView(position, convertView, parent)
                        if (view is android.widget.CheckedTextView) {
                            view.setTextColor(textPrimaryColor)
                            view.checkMarkTintList = colorStateList
                        }
                        return view
                    }
                }
                for (i in allTags.indices) {
                    setItemChecked(i, checkedTagStates[i])
                }
                setOnItemClickListener { _, _, position, _ ->
                    checkedTagStates[position] = isItemChecked(position)
                }
            }
            layout.addView(tagsListView)
        }

        AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle("Filter Reminders")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                currentFilter = when {
                    rbActive.isChecked -> ReminderFilter.ACTIVE
                    rbPunted.isChecked -> ReminderFilter.SNOOZED
                    else -> ReminderFilter.ALL
                }

                var updatedQuery = currentSearchQuery
                for (i in allTags.indices) {
                    val tag = allTags[i]
                    val isChecked = checkedTagStates[i]
                    val containsTag = updatedQuery.contains(tag, ignoreCase = true)

                    if (isChecked && !containsTag) {
                        updatedQuery = if (updatedQuery.isBlank()) tag else "$updatedQuery $tag"
                    } else if (!isChecked && containsTag) {
                        updatedQuery = updatedQuery.replace(Regex("(?i)\\b${Regex.escape(tag)}\\b|${Regex.escape(tag)}"), "")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    }
                }

                currentSearchQuery = updatedQuery
                getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_REMINDER_FILTER, currentFilter.name)
                    .putString(KEY_SEARCH_QUERY, updatedQuery)
                    .apply()

                recentlyDoneReminders.clear()
                binding.searchReminderInput.setText(updatedQuery)
                updateSummaryAndAdapter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportRemindersToMarkdown() {
        val exportList = displayedReminders.filter { it != HEADER_SNOOZED_SECTION_MARKER }
        if (exportList.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_reminders_to_export, Toast.LENGTH_SHORT).show()
            return
        }
        val markdownText = com.bas080.notificationreminders.utils.MarkdownRemindersUtil.exportToMarkdown(exportList)
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

        AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.import_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.import_button) { _, _ ->
                val markdownText = input.text.toString()
                val importedItems = com.bas080.notificationreminders.utils.MarkdownRemindersUtil.importFromMarkdown(markdownText)
                if (importedItems.isNotEmpty()) {
                    var addedCount = 0
                    for (item in importedItems) {
                        if (!activeReminders.contains(item)) {
                            activeReminders.add(item)
                            addedCount++
                        }
                    }
                    if (addedCount > 0) {
                        saveRemindersToPrefs()
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

    private fun showRemindersView() {
        binding.remindersContainer.visibility = View.VISIBLE
        binding.aboutContainer.visibility = View.GONE

        binding.btnNavReminders.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnNavReminders.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.btnNavAbout.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.btnNavAbout.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun showCalendarPermissionSettingsDialog() {
        AlertDialog.Builder(this, R.style.Theme_Punt_Dialog)
            .setTitle(R.string.calendar_sync_title)
            .setMessage(R.string.calendar_permission_required)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null)
                )
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateCalendarSyncButtonText() {
        val enabled = com.bas080.notificationreminders.utils.CalendarSyncManager.isCalendarSyncEnabled(this)
        binding.btnToggleCalendarSync.setText(
            if (enabled) R.string.calendar_sync_enabled else R.string.calendar_sync_disabled
        )
    }

    private fun showAboutView() {
        binding.remindersContainer.visibility = View.GONE
        binding.aboutContainer.visibility = View.VISIBLE

        binding.btnNavReminders.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.btnNavReminders.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnNavAbout.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnNavAbout.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.txtAppVersion.text = getString(R.string.app_version_format, pInfo.versionName)
        } catch (_: Exception) {
            binding.txtAppVersion.text = getString(R.string.app_version_format, "1.0")
        }

        loadLogs()
    }

    private fun loadLogs() {
        val logs = AppLogger.getLogs(this)
        binding.txtLogs.text = if (logs.isNotBlank()) logs else "No logs available."
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
                            AppLogger.log(this, "MainActivity", "Updated reminder text")
                            val oldNotifId = ReminderNotificationListenerService.getNotificationIdForReminder(oldText)
                            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                            notificationManager?.cancel(oldNotifId)

                            val oldTrimmed = getCleanTrimmed(oldText)
                            val newTrimmed = getCleanTrimmed(updatedText)
                            if (oldTrimmed != newTrimmed) {
                                val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                                val snoozeTime = prefs.getLong("snooze_$oldTrimmed", 0L)
                                if (snoozeTime > 0L) {
                                    prefs.edit().remove("snooze_$oldTrimmed").putLong("snooze_$newTrimmed", snoozeTime).apply()
                                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$oldTrimmed")
                                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$newTrimmed"] = snoozeTime
                                }
                            }
                        }

                        activeReminders[masterIdx] = updatedText
                        displayedReminders[index] = updatedText
                        saveRemindersToPrefs(updateStatusNotification = false)
                        updateSummary()
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
                    undoMarkDone(displayedReminders[index])
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
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position in displayedReminders.indices) {
                    val reminderText = displayedReminders[position]
                    val isDone = reminderText.contains("#done", ignoreCase = true)

                    if (isDone) {
                        if (direction == ItemTouchHelper.LEFT) {
                            adapter.notifyItemChanged(position)
                            undoMarkDone(reminderText)
                        } else if (direction == ItemTouchHelper.RIGHT) {
                            deleteReminder(reminderText)
                        }
                    } else if (direction == ItemTouchHelper.LEFT) {
                        adapter.notifyItemChanged(position)
                        showSnoozeOptionsDialog(reminderText)
                    } else if (direction == ItemTouchHelper.RIGHT) {
                        adapter.notifyItemChanged(position)
                        markReminderDone(reminderText)
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
                val isDone = if (position in displayedReminders.indices) {
                    displayedReminders[position].contains("#done", ignoreCase = true)
                } else false

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                    val background = ColorDrawable()
                    val icon: Drawable?
                    val hMargin = (16 * resources.displayMetrics.density).toInt()

                    if (isDone) {
                        background.color = ContextCompat.getColor(this@MainActivity, R.color.bg_dark)
                        if (dX > 0) {
                            background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                            background.draw(c)

                            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_delete)
                            icon?.let {
                                val vMargin = (itemView.height - it.intrinsicHeight) / 2
                                val top = itemView.top + vMargin
                                val bottom = top + it.intrinsicHeight
                                val left = itemView.left + hMargin
                                val right = left + it.intrinsicWidth
                                it.setBounds(left, top, right, bottom)
                                it.draw(c)
                            }
                        } else {
                            background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                            background.draw(c)

                            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_undo)
                            icon?.let {
                                val vMargin = (itemView.height - it.intrinsicHeight) / 2
                                val top = itemView.top + vMargin
                                val bottom = top + it.intrinsicHeight
                                val right = itemView.right - hMargin
                                val left = right - it.intrinsicWidth
                                it.setBounds(left, top, right, bottom)
                                it.draw(c)
                            }
                        }
                    } else if (dX > 0) {
                        background.color = ContextCompat.getColor(this@MainActivity, R.color.bg_dark)
                        background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                        background.draw(c)

                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_done)
                        icon?.let {
                            val vMargin = (itemView.height - it.intrinsicHeight) / 2
                            val top = itemView.top + vMargin
                            val bottom = top + it.intrinsicHeight
                            val left = itemView.left + hMargin
                            val right = left + it.intrinsicWidth
                            it.setBounds(left, top, right, bottom)
                            it.draw(c)
                        }
                    } else if (dX < 0) {
                        background.color = ContextCompat.getColor(this@MainActivity, R.color.bg_dark)
                        background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                        background.draw(c)

                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_snooze)
                        icon?.let {
                            val vMargin = (itemView.height - it.intrinsicHeight) / 2
                            val top = itemView.top + vMargin
                            val bottom = top + it.intrinsicHeight
                            val right = itemView.right - hMargin
                            val left = right - it.intrinsicWidth
                            it.setBounds(left, top, right, bottom)
                            it.draw(c)
                        }
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
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val snoozeUntil = prefs.getLong("snooze_$cleanTrimmed", 0L).let {
            if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] ?: 0L)
        }
        val isSnoozed = snoozeUntil > now

        val lastChoice = prefs.getString("last_choice_$cleanTrimmed", null)
        val topChoices = ReminderNotificationListenerService.getTopSnoozeChoices(this).map { it.toString() }

        val choicesList = mutableListOf<String>()
        if (!lastChoice.isNullOrBlank()) {
            choicesList.add(lastChoice)
            for (choice in topChoices) {
                if (choice != lastChoice) {
                    choicesList.add(choice)
                }
            }
        } else {
            choicesList.addAll(topChoices)
        }

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
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$cleanTrimmed")
                    prefs.edit().remove("snooze_$cleanTrimmed").apply()
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
                    applySnoozeDuration(reminderText, choice)
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
                applySnoozeDuration(reminderText, customInput)
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

    private fun applySnoozeDurationToTargets(targets: List<String>, durationChoice: String) {
        val parseResult = CreateReminderReceiver.parseSnoozeDuration(durationChoice)
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

        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        for (reminderText in targets) {
            val cleanTrimmed = getCleanTrimmed(reminderText)
            ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] = snoozeUntil
            editor.putLong("snooze_$cleanTrimmed", snoozeUntil)
            editor.putString("last_choice_$cleanTrimmed", durationChoice)
        }
        editor.apply()

        ReminderNotificationListenerService.instance?.showStatusNotification()
        updateSummaryAndAdapter()

        AppLogger.log(this, "MainActivity", "Punted ${targets.size} reminder(s) for $durationLabel")
        val toastText = getString(R.string.toast_reminder_snoozed_duration, durationLabel)
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
    }

    private fun markReminderDone(reminderText: String) {
        val idx = activeReminders.indexOf(reminderText)
        if (idx != -1) {
            val doneText = if (reminderText.contains("#done", ignoreCase = true)) {
                reminderText
            } else {
                "$reminderText #done"
            }
            activeReminders[idx] = doneText
            recentlyDoneReminders.add(doneText)
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).apply()

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
            val cleanText = doneReminderText.replace(Regex("(?i)\\s*#done\\b"), "").trim()
            activeReminders[idx] = cleanText
            recentlyDoneReminders.remove(doneReminderText)
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).apply()

            ReminderNotificationListenerService.instance?.showStatusNotification()
            updateSummaryAndAdapter()
            AppLogger.log(this, "MainActivity", "Undid mark done")
            Toast.makeText(this, "Mark done undone", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteReminder(reminderText: String) {
        val idx = activeReminders.indexOf(reminderText)
        if (idx != -1) {
            activeReminders.removeAt(idx)
            recentlyDoneReminders.remove(reminderText)
            val cleanTrimmed = getCleanTrimmed(reminderText)
            ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$cleanTrimmed")
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).remove("snooze_$cleanTrimmed").apply()

            val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancel(notificationId)

            ReminderNotificationListenerService.instance?.showStatusNotification()
            updateSummaryAndAdapter()
            AppLogger.log(this, "MainActivity", "Deleted reminder")
            Toast.makeText(this, R.string.toast_reminder_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun unpuntReminder(reminderText: String) {
        val cleanTrimmed = getCleanTrimmed(reminderText)
        ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$cleanTrimmed")
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().remove("snooze_$cleanTrimmed").apply()

        ReminderNotificationListenerService.instance?.showStatusNotification()
        updateSummaryAndAdapter()
        AppLogger.log(this, "MainActivity", "Unpunted reminder")
        Toast.makeText(this, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
    }

    private fun loadReminders() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
        val savedFilterName = prefs.getString(KEY_REMINDER_FILTER, ReminderFilter.ALL.name)
        currentFilter = try {
            ReminderFilter.valueOf(savedFilterName ?: ReminderFilter.ALL.name)
        } catch (_: Exception) {
            ReminderFilter.ALL
        }
        val savedQuery = prefs.getString(KEY_SEARCH_QUERY, "") ?: ""
        currentSearchQuery = savedQuery
        if (binding.searchReminderInput.text.toString() != savedQuery) {
            binding.searchReminderInput.setText(savedQuery)
        }
        activeReminders.clear()
        activeReminders.addAll(savedSet)
        updateSummaryAndAdapter()
    }

    private fun saveRemindersToPrefs(updateStatusNotification: Boolean = true) {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).apply()
        if (updateStatusNotification) {
            ReminderNotificationListenerService.instance?.showStatusNotification()
        }
        com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(this)
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

        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val searchContainsDone = currentSearchQuery.contains("#done", ignoreCase = true)

        val filterByFilterType = { filterType: ReminderFilter ->
            val statusFiltered = when (filterType) {
                ReminderFilter.ALL -> activeReminders.filter { reminder ->
                    val isDone = reminder.contains("#done", ignoreCase = true)
                    if (isDone) searchContainsDone || recentlyDoneReminders.contains(reminder) else true
                }
                ReminderFilter.ACTIVE -> activeReminders.filter { reminder ->
                    val cleanTrimmed = getCleanTrimmed(reminder)
                    val snoozeUntil = prefs.getLong("snooze_$cleanTrimmed", 0L).let {
                        if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] ?: 0L)
                    }
                    val isDone = reminder.contains("#done", ignoreCase = true)
                    if (isDone) {
                        return@filter snoozeUntil <= now && (searchContainsDone || recentlyDoneReminders.contains(reminder))
                    }
                    snoozeUntil <= now
                }
                ReminderFilter.SNOOZED -> activeReminders.filter { reminder ->
                    val cleanTrimmed = getCleanTrimmed(reminder)
                    val snoozeUntil = prefs.getLong("snooze_$cleanTrimmed", 0L).let {
                        if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] ?: 0L)
                    }
                    val isDone = reminder.contains("#done", ignoreCase = true)
                    if (isDone) {
                        return@filter snoozeUntil > now && (searchContainsDone || recentlyDoneReminders.contains(reminder))
                    }
                    snoozeUntil > now
                }
            }
            com.bas080.notificationreminders.utils.ReminderMatcher.filterSearchQueryTiered(statusFiltered, currentSearchQuery)
        }

        val filtered = filterByFilterType(currentFilter)

        val activeItems = mutableListOf<String>()
        val snoozedItems = mutableListOf<Pair<String, Long>>()

        for (reminder in filtered) {
            val cleanTrimmed = getCleanTrimmed(reminder)
            val snoozeUntil = prefs.getLong("snooze_$cleanTrimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] ?: 0L)
            }
            if (snoozeUntil > now) {
                snoozedItems.add(reminder to snoozeUntil)
            } else {
                activeItems.add(reminder)
            }
        }

        activeItems.reverse()
        snoozedItems.sortBy { it.second }

        val sorted = mutableListOf<String>()
        sorted.addAll(activeItems)

        if (currentFilter == ReminderFilter.ALL && snoozedItems.isNotEmpty()) {
            sorted.add(HEADER_SNOOZED_SECTION_MARKER)
        }
        sorted.addAll(snoozedItems.map { it.first })

        val snoozeMap = mutableMapOf<String, Long>()
        for (reminder in activeReminders) {
            val cleanTrimmed = getCleanTrimmed(reminder)
            val snoozeUntil = prefs.getLong("snooze_$cleanTrimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] ?: 0L)
            }
            if (snoozeUntil > 0L) {
                snoozeMap[cleanTrimmed] = snoozeUntil
            }
        }

        adapter.updateList(sorted, snoozeMap)
        updateSummary()

        if (firstVisiblePos != RecyclerView.NO_POSITION && firstVisiblePos < sorted.size) {
            layoutManager?.scrollToPositionWithOffset(firstVisiblePos, topOffset)
        }
    }

    private fun updateSummary() {
        val stateText = when (currentFilter) {
            ReminderFilter.ACTIVE -> "Active"
            ReminderFilter.SNOOZED -> "Punted"
            ReminderFilter.ALL -> "All"
        }
        val selectedTags = Regex("#[a-zA-Z0-9_]+").findAll(currentSearchQuery).map { it.value }.toList()
        if (selectedTags.isEmpty()) {
            binding.txtSelectedTags.text = stateText
        } else {
            binding.txtSelectedTags.text = "$stateText • ${selectedTags.joinToString(" ")}"
        }

        val hasFilterOrSearch = currentSearchQuery.isNotBlank() || currentFilter != ReminderFilter.ALL
        binding.btnClearSearch.isEnabled = hasFilterOrSearch
        binding.btnClearSearch.isClickable = hasFilterOrSearch
        binding.btnClearSearch.isFocusable = hasFilterOrSearch
        if (hasFilterOrSearch) {
            binding.btnClearSearch.setColorFilter(ContextCompat.getColor(this, R.color.accent))
            binding.btnClearSearch.alpha = 1.0f
        } else {
            binding.btnClearSearch.setColorFilter(ContextCompat.getColor(this, R.color.text_muted))
            binding.btnClearSearch.alpha = 0.4f
        }

        if (displayedReminders.isEmpty()) {
            binding.layoutEmptyReminders.visibility = View.VISIBLE
            if (hasFilterOrSearch) {
                binding.txtEmptyReminders.text = "No matching reminders found."
                binding.btnEmptyClearFilter.visibility = View.VISIBLE
            } else {
                binding.txtEmptyReminders.setText(R.string.no_reminders)
                binding.btnEmptyClearFilter.visibility = View.GONE
            }
        } else {
            binding.layoutEmptyReminders.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        com.bas080.notificationreminders.utils.CalendarSyncManager.syncCalendarChangesToReminders(this)
        loadReminders()
        if (binding.aboutContainer.visibility == View.VISIBLE) {
            loadLogs()
        }
        if (isNotificationListenerEnabled()) {
            startReminderService()
        }
    }

    private fun checkAndShowCrashReportDialog() {
        val prefs = getSharedPreferences(NotificationRemindersApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = prefs.getString(NotificationRemindersApplication.KEY_CRASH_TRACE, null) ?: return

        val intent = Intent(this, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, crashTrace)
        }
        startActivity(intent)
    }

    private fun clearCrashTrace() {
        val prefs = getSharedPreferences(NotificationRemindersApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(NotificationRemindersApplication.KEY_CRASH_TRACE).apply()
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
        const val TYPE_SNOOZED_HEADER = 3
        private const val PREFS_REMINDERS = "reminders_prefs"
    }

    private val currentSnoozeMap = mutableMapOf<String, Long>()

    private class RemindersDiffCallback(
        private val oldList: List<String>,
        private val newList: List<String>,
        private val oldSnoozeMap: Map<String, Long>,
        private val newSnoozeMap: Map<String, Long>
    ) : androidx.recyclerview.widget.DiffUtil.Callback() {
        override fun getOldListSize(): Int = if (oldList.isEmpty()) 0 else oldList.size + 1
        override fun getNewListSize(): Int = if (newList.isEmpty()) 0 else newList.size + 1

        private fun getOldType(position: Int): Int {
            if (oldList.isNotEmpty() && position == oldList.size) return TYPE_FOOTER_INSTRUCTIONS
            return if (oldList.getOrNull(position) == HEADER_SNOOZED_SECTION_MARKER) TYPE_SNOOZED_HEADER else TYPE_ACTIVE_REMINDER
        }

        private fun getNewType(position: Int): Int {
            if (newList.isNotEmpty() && position == newList.size) return TYPE_FOOTER_INSTRUCTIONS
            return if (newList.getOrNull(position) == HEADER_SNOOZED_SECTION_MARKER) TYPE_SNOOZED_HEADER else TYPE_ACTIVE_REMINDER
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldType = getOldType(oldItemPosition)
            val newType = getNewType(newItemPosition)

            if (oldType != newType) return false

            return when (oldType) {
                TYPE_FOOTER_INSTRUCTIONS -> true
                TYPE_SNOOZED_HEADER -> true
                else -> oldList.getOrNull(oldItemPosition) == newList.getOrNull(newItemPosition)
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldType = getOldType(oldItemPosition)
            val newType = getNewType(newItemPosition)

            if (oldType != newType) return false

            return when (oldType) {
                TYPE_FOOTER_INSTRUCTIONS -> true
                TYPE_SNOOZED_HEADER -> true
                else -> {
                    val oldItem = oldList.getOrNull(oldItemPosition) ?: return true
                    val newItem = newList.getOrNull(newItemPosition) ?: return true
                    if (oldItem != newItem) return false

                    val oldTrimmed = MainActivity.getCleanTrimmed(oldItem)
                    val newTrimmed = MainActivity.getCleanTrimmed(newItem)

                    val oldSnooze = oldSnoozeMap[oldTrimmed] ?: 0L
                    val newSnooze = newSnoozeMap[newTrimmed] ?: 0L

                    oldSnooze == newSnooze
                }
            }
        }
    }

    fun updateList(newList: List<String>, newSnoozeMap: Map<String, Long> = emptyMap()) {
        val diffCallback = RemindersDiffCallback(displayedReminders, newList, currentSnoozeMap, newSnoozeMap)
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        displayedReminders.clear()
        displayedReminders.addAll(newList)
        currentSnoozeMap.clear()
        currentSnoozeMap.putAll(newSnoozeMap)
        diffResult.dispatchUpdatesTo(this)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reminderInput: EditText = view.findViewById(R.id.reminder_input)
        val txtStatus: TextView = view.findViewById(R.id.txt_status)
        val btnShare: ImageView = view.findViewById(R.id.btn_share)
        val btnAction: ImageView = view.findViewById(R.id.btn_action)
        var textWatcher: TextWatcher? = null
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtHeaderTitle: TextView = view.findViewById(R.id.txt_header_title)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        if (displayedReminders.isNotEmpty() && position == displayedReminders.size) return TYPE_FOOTER_INSTRUCTIONS
        val item = displayedReminders[position]
        return if (item == HEADER_SNOOZED_SECTION_MARKER) TYPE_SNOOZED_HEADER else TYPE_ACTIVE_REMINDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOOTER_INSTRUCTIONS -> {
                val view = inflater.inflate(R.layout.item_footer_instructions, parent, false)
                FooterViewHolder(view)
            }
            TYPE_SNOOZED_HEADER -> {
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
            holder.txtHeaderTitle.text = "PUNTED"
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
        }

        val reminderIndex = position
        val reminderText = displayedReminders[reminderIndex]
        val isDone = reminderText.contains("#done", ignoreCase = true)

        val cleanTrimmed = MainActivity.getCleanTrimmed(reminderText)
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val snoozeUntil = prefs.getLong("snooze_$cleanTrimmed", 0L).let {
            if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$cleanTrimmed"] ?: 0L)
        }
        val isSnoozed = snoozeUntil > now

        holder.reminderInput.hint = "Reminder"
        holder.reminderInput.setText(reminderText)

        if (isDone) {
            holder.reminderInput.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            holder.reminderInput.alpha = 0.5f
            holder.reminderInput.paintFlags = holder.reminderInput.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.btnShare.visibility = View.GONE
            holder.btnAction.visibility = View.VISIBLE
            holder.btnAction.setImageResource(R.drawable.ic_action_undo)
            holder.btnAction.setColorFilter(ContextCompat.getColor(context, R.color.accent))
            holder.btnAction.contentDescription = "Undo mark done"
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

        if (isSnoozed) {
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
