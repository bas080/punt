Pull-down (pull-to-refresh) task creation from top query/input box

When performing a pull-down gesture on the main reminders list:
1. If top input box contains non-blank text:
   - Convert text into a new saved reminder (saved to storage & backup).
   - Clear top search input box (`search_reminder_input.setText("")`) and reset search query filter so all active items are visible.
   - Set focus to top input box (`search_reminder_input.requestFocus()`) without forcibly popping up the soft keyboard.
   - Clear recently completed `#done` items and reload list.
   - DiffUtil animates the new item card smoothly sliding into position 0 at the top of the list as the pull-to-refresh indicator completes.
2. If top input box is empty or whitespace-only:
   - Perform standard pull-to-refresh (clears recently completed `#done` items and reloads list).
   - Maintain focus state without changing keyboard or focus.

## Top Box Layout & Filter Icon Placement
- Remove the explicit `+` add button (`btn_add_reminder`) on the far right of the top input card.
- Move the filter icon (`btn_tags_filter`) to the right side of the top input box (`[Input Box] [Filter Icon]`).

## Edge Cases & Safeguards
1. **Active State/Tag Filters**:
   - *Issue*: If filtered to "Punted" or specific tags, newly created active items wouldn't appear.
   - *Safeguard*: Pull-down creation resets `currentFilter` to `ALL` and clears active tag filters alongside clearing `currentSearchQuery`, guaranteeing the newly created item card at position 0 is immediately visible.
2. **Whitespace-Only Input**:
   - *Issue*: Accidentally pulling down with spaces or blank newlines in the input field.
   - *Safeguard*: Apply `reminderText.trim()`. If `trimmedText.isEmpty()`, fall back to standard list refresh without creating a reminder.
3. **Multi-line Draft Pasting**:
   - *Issue*: Pasting multi-line content into the top box.
   - *Safeguard*: Preserve multi-line text formatting as a single multi-line reminder card using standard `MarkdownRemindersUtil` multi-line continuation indentation.
4. **Touch Conflict with Input Field Scrolling**:
   - *Issue*: Swiping vertically inside a focused multi-line input field vs pulling down the `RecyclerView`.
   - *Safeguard*: `SwipeRefreshLayout` drag is only intercepted when `RecyclerView` is scrolled to the very top (`canChildScrollUp() == false`), preventing touch conflicts during multi-line input box scrolling.
5. **System & Notification Integration**:
   - *Issue*: Ensuring app notifications, backup files, and calendar sync update immediately.
   - *Safeguard*: Call standard persistence helpers (`postMatchNotification()`, `TaskBackupManager.backupTasks()`, and `CalendarSyncManager`) on pull-down creation.

## Visual & Animation Polish
- `SwipeRefreshLayout` styled with Punt's accent cyan color scheme (`setColorSchemeResources(R.color.accent)`).
- Smooth `DiffUtil` list insertion animation as the refresh spinner dismisses.
