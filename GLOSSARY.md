# Punt Glossary

This glossary defines key domain terms, concepts, and conventions used across the Punt application and codebase.

## Concepts & Definitions

### Punt / Snooze
* **Punt (verb / noun):** Temporarily deferring a task to a future timestamp. When a reminder is punted, its expiration timestamp (`snooze_$cleanTrimmed`) is saved in SharedPreferences (`reminders_prefs`). Punted tasks appear in the lower PUNTED section of the list until their punt duration expires.

### Focus Profile
* **Focus Profile:** A saved search query preset (e.g. `Work` -> `#work`, `Fitness` -> `#fitness`) used to restrict visible tasks to a specific context or topic. Focus profiles operate as the first stage of a two-stage filter pipeline (`all | focus | search`).

### Filter Pipeline (`all | focus | search`)
* **Two-Stage Filtering:** Tasks are evaluated in two sequential stages:
  1. **Focus Stage:** Active Focus Profile query filters the initial list of tasks.
  2. **Search Stage:** Search query typed into the search bar filters within the focused task subset.

### Recurrence
* **Recurring Task:** A task containing recurrence tags like `#every 1d`, `#repeat 12h`, or `#every 1M`. When marked done, recurring tasks are automatically punted into the future by their recurrence interval rather than being marked with `#done`.

### Tag
* **Tag:** A label starting with `#` (e.g. `#work`, `#groceries`, `#done`). Used for search matching and state filtering.

### Done State
* **Mark Done:** Marking a non-recurring task complete appends ` #done` to its string. Completed items are hidden from search results unless `#done` is explicitly included in the search or focus query.

### Status Notification
* **Add Reminder Notification:** A persistent group summary notification posted in the notification shade allowing inline task creation (`RemoteInput`) and status monitoring without opening the main UI.

### Task Backup
* **Task Backup Manager (`TaskBackupManager`):** Automatically backs up all task text strings and punt timestamps to `punt_tasks_backup.md` on task edits, app launch, and app pause, enabling automatic restoration if SharedPreferences are empty on startup.
