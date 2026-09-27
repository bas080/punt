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
import com.bas080.notificationreminders.adapters.RemindersAdapter
import com.bas080.notificationreminders.databinding.ActivityMainBinding
import com.bas080.notificationreminders.models.ReminderFilter
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val HEADER_SNOOZED_SECTION_MARKER = "HEADER_SNOOZED_SECTION_MARKER"

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val KEY_REMINDER_FILTER = "key_reminder_filter"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"

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
        markAsButtonAccessibility(binding.btnFeedback)
        markAsButtonAccessibility(binding.btnTagsFilter)
        binding.btnListExport.setColorFilter(ContextCompat.getColor(this, R.color.accent))
        markAsButtonAccessibility(binding.btnClearSearch)
        markAsButtonAccessibility(binding.btnEmptyClearFilter)

        val clearFilterAndSearchAction = {
            currentSearchQuery = ""
            currentFilter = ReminderFilter.ALL
            getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_REMINDER_FILTER, currentFilter.name)
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

        binding.btnListExport.setOnClickListener {
            exportRemindersToMarkdown()
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

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle("Filter Reminders")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                currentFilter = when {
                    rbActive.isChecked -> ReminderFilter.ACTIVE
                    rbPunted.isChecked -> ReminderFilter.SNOOZED
                    else -> ReminderFilter.ALL
                }

                getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_REMINDER_FILTER, currentFilter.name)
                    .apply()

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

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
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

                            val oldTrimmed = oldText.trim().lowercase()
                            val newTrimmed = updatedText.trim().lowercase()
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

    private fun setupSwipeGestures() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
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

                    if (isDone) {
                        background.color = ContextCompat.getColor(this@MainActivity, R.color.bg_dark)
                        if (dX > 0) {
                            background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                            background.draw(c)

                            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_delete)
                            icon?.let {
                                val margin = (itemView.height - it.intrinsicHeight) / 2
                                val top = itemView.top + margin
                                val bottom = top + it.intrinsicHeight
                                val left = itemView.left + margin
                                val right = left + it.intrinsicWidth
                                it.setBounds(left, top, right, bottom)
                                it.draw(c)
                            }
                        } else {
                            background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                            background.draw(c)

                            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_undo)
                            icon?.let {
                                val margin = (itemView.height - it.intrinsicHeight) / 2
                                val top = itemView.top + margin
                                val bottom = top + it.intrinsicHeight
                                val right = itemView.right - margin
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
                            val margin = (itemView.height - it.intrinsicHeight) / 2
                            val top = itemView.top + margin
                            val bottom = top + it.intrinsicHeight
                            val left = itemView.left + margin
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
                            val margin = (itemView.height - it.intrinsicHeight) / 2
                            val top = itemView.top + margin
                            val bottom = top + it.intrinsicHeight
                            val right = itemView.right - margin
                            val left = right - it.intrinsicWidth
                            it.setBounds(left, top, right, bottom)
                            it.draw(c)
                        }
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.remindersList)
    }

    private fun showSnoozeOptionsDialog(reminderText: String) {
        val trimmed = reminderText.trim().lowercase()
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
            if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
        }
        val isSnoozed = snoozeUntil > now

        val topChoices = ReminderNotificationListenerService.getTopSnoozeChoices(this).map { it.toString() }
        val durations = (topChoices + "Custom...").toTypedArray()
        val options = if (isSnoozed) {
            arrayOf(getString(R.string.unsnooze)) + durations
        } else {
            durations
        }

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(R.string.snooze_dialog_title)
            .setItems(options) { _, which ->
                if (isSnoozed && which == 0) {
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
                    prefs.edit().remove("snooze_$trimmed").apply()
                    ReminderNotificationListenerService.instance?.showStatusNotification()
                    updateSummaryAndAdapter()
                    Toast.makeText(this, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
                } else {
                    val durationIndex = if (isSnoozed) which - 1 else which
                    if (durationIndex in 0 until durations.size - 1) {
                        applySnoozeDuration(reminderText, durations[durationIndex])
                    } else {
                        showCustomSnoozeInputDialog(reminderText)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomSnoozeInputDialog(reminderText: String) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            id = R.id.import_input
            hint = CreateReminderReceiver.getSnoozeCustomHint(this@MainActivity)
            setSingleLine(true)
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(R.string.snooze_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.snooze) { _, _ ->
                val customInput = input.text.toString().trim()
                applySnoozeDuration(reminderText, customInput)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applySnoozeDuration(reminderText: String, durationChoice: String) {
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
        val trimmed = reminderText.trim().lowercase()

        ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] = snoozeUntil
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putLong("snooze_$trimmed", snoozeUntil).apply()

        ReminderNotificationListenerService.instance?.showStatusNotification()
        updateSummaryAndAdapter()

        AppLogger.log(this, "MainActivity", "Punted reminder for $durationLabel")
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
            val trimmed = reminderText.trim().lowercase()
            ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).remove("snooze_$trimmed").apply()

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
            val trimmed = reminderText.trim().lowercase()
            ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).remove("snooze_$trimmed").apply()

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
        val trimmed = reminderText.trim().lowercase()
        ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().remove("snooze_$trimmed").apply()

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
                    val isDone = reminder.contains("#done", ignoreCase = true)
                    if (isDone) return@filter searchContainsDone || recentlyDoneReminders.contains(reminder)
                    val trimmed = reminder.trim().lowercase()
                    val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                        if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
                    }
                    snoozeUntil <= now
                }
                ReminderFilter.SNOOZED -> activeReminders.filter { reminder ->
                    val isDone = reminder.contains("#done", ignoreCase = true)
                    if (isDone) return@filter searchContainsDone || recentlyDoneReminders.contains(reminder)
                    val trimmed = reminder.trim().lowercase()
                    val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                        if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
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
            val trimmed = reminder.trim().lowercase()
            val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
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
            val trimmed = reminder.trim().lowercase()
            val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
            }
            if (snoozeUntil > 0L) {
                snoozeMap[trimmed] = snoozeUntil
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
        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
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
