Migrate Punt Android persistence from SharedPreferences to Room database

Migrate Punt's underlying persistence layer on Android from key-value `SharedPreferences` (`reminders_prefs`) to an Android Jetpack Room database (`RoomDatabase` with SQLite).

### Context & Motivation
- Currently, Punt persists reminder text entries, punt timestamps (`snooze_$cleanTrimmed`), task choices history, and UI state (section collapse, search queries) in `SharedPreferences` (`reminders_prefs`).
- Migrating to Room provides:
  1. Strongly-typed, structured schema for reminders (e.g. `id`, `text`, `createdAt`, `snoozeUntil`, `isDone`, `recurrenceInterval`).
  2. Indexing and fast SQL search/filter operations for large datasets.
  3. Stable, auto-incremented or UUID primary keys (`id`) to unambiguously identify tasks during server synchronization and mutations from web/CLI.
  4. Reactive queries via `Flow` or `LiveData` to update UI and status notifications automatically.
  5. Clean separation between task entity records and user preferences (focus profiles, server sync tokens, collapse state).

### Core Components
1. **Entity Schema (`ReminderEntity`)**:
   - `id: Long` (auto-increment primary key) or `UUID`
   - `text: String` (the reminder content, e.g. "Buy milk #groceries")
   - `createdAt: Long` (creation timestamp in ms)
   - `snoozeUntil: Long?` (punt timestamp in ms, null if active)
   - `isDone: Boolean` (completion flag, replacing/complementing `#done` tag in text)
   - `updatedAt: Long` (last mutation timestamp in ms)
2. **Data Access Object (`ReminderDao`)**:
   - CRUD operations: `insert`, `update`, `delete`.
   - Reactive queries: `getAllActive()`, `getAllPunted()`, `searchTasks(query)`.
   - Batch operations: `replaceAll(reminders)`.
3. **Migration Strategy**:
   - Auto-migration from existing `reminders_prefs` and `TaskBackupManager` (`punt_tasks_backup.md`) on first launch.
   - Preserve existing snooze timestamps and task creation ordering.
   - Retain `TaskBackupManager` markdown synchronization as the human-readable backup target.

## Questions for @bas080
1. Should `#done` status be stored purely as a dedicated database column (`isDone: Boolean`), or should `#done` still be appended to the reminder text string to keep 100% format parity with markdown backups and clipboard exports?
