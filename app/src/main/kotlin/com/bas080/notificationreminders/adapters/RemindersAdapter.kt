package com.bas080.notificationreminders.adapters

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bas080.notificationreminders.HEADER_SNOOZED_SECTION_MARKER
import com.bas080.notificationreminders.MainActivity
import com.bas080.notificationreminders.R
import com.bas080.notificationreminders.services.ReminderNotificationListenerService

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
    ) : DiffUtil.Callback() {
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

                    val oldTrimmed = oldItem.trim().lowercase()
                    val newTrimmed = newItem.trim().lowercase()

                    val oldSnooze = oldSnoozeMap[oldTrimmed] ?: 0L
                    val newSnooze = newSnoozeMap[newTrimmed] ?: 0L

                    oldSnooze == newSnooze
                }
            }
        }
    }

    fun updateList(newList: List<String>, newSnoozeMap: Map<String, Long> = emptyMap()) {
        val diffCallback = RemindersDiffCallback(displayedReminders.toList(), newList, currentSnoozeMap, newSnoozeMap)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
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

        val trimmed = reminderText.trim().lowercase()
        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
            if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
        }
        val isSnoozed = !isDone && snoozeUntil > now

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
