Migrate Punt Android persistence from SharedPreferences to Room database

Migrate Punt's underlying persistence layer on Android from key-value `SharedPreferences` (`reminders_prefs`) to an Android Jetpack Room database (`RoomDatabase` with SQLite).

### Context & Motivation
- Currently, Punt persists reminder text entries, punt timestamps (`snooze_$cleanTrimmed`), task choices history, and UI state (section collapse, search queries) in `SharedPreferences` (`reminders_prefs`).
- Migrating to Room provides:
  1. Strongly-typed, structured schema for reminders (e.g. `id`, `text`, `createdAt`, `snoozeUntil`, `isDone`, `recurrenceInterval`).
  2. Indexing and fast SQL search/filter operations for large datasets.
  3. Stable, auto-incremented primary keys (`id`) to unambiguously identify tasks during server synchronization and mutations from web/CLI.
  4. Reactive queries via `Flow` to update UI and status notifications automatically.
  5. Clean separation between task entity records and user preferences (focus profiles, server sync tokens, collapse state).

### Core Components & Requirements

#### 1. Entity Schema (`ReminderEntity`)
- `id: Long` (auto-increment primary key)
- `text: String` (clean reminder content without `#done` appended)
- `createdAt: Long` (creation timestamp in ms)
- `snoozeUntil: Long?` (punt timestamp in ms, null if active)
- `isDone: Boolean` (dedicated boolean database column storing completion status, instead of altering the raw reminder text with `#done`)
- `recurrenceInterval: String?` (optional cached recurrence interval like `1d`, `1w`, or `1M`)
- `updatedAt: Long` (last mutation timestamp in ms)

#### 2. Automatic Legacy Migration on App Startup
- On application start, the app checks if the legacy `reminders_prefs` storage is in use (e.g. `key_legacy_migration_done` is false and `reminders_prefs` contains reminders).
- If detected:
  1. Reads all legacy reminder strings and corresponding snooze timestamps (`snooze_$cleanTrimmed`).
  2. Parses any existing `#done` tags, setting `isDone = true` and stripping `#done` from the clean `text` column.
  3. Inserts all converted records into the Room database within a single transaction.
  4. Sets `key_legacy_migration_done = true` in SharedPreferences.
  5. Triggers `TaskBackupManager` to write an initial backup.

#### 3. Separation of Markdown Export vs. Backup Import/Export
- **Export to Markdown (Sharing / Notes)**:
  - Formatted human-readable markdown document.
  - Formats active/punted tasks, optional `<time>` punt tags, and `- [x]` / `- [ ]` checklist formatting for pasting into note-taking apps (Obsidian, Logseq, GitHub).
- **Import / Export for Backup**:
  - Full-fidelity data exchange designed to backup and restore the entire database state.
  - Preserves exact primary keys (`id`), creation dates (`createdAt`), punt timestamps (`snoozeUntil`), and completion states (`isDone`).

## Questions for @bas080
1. What format should the full-fidelity backup file use (e.g., a structured JSON backup file `punt_backup.json` or an enhanced markdown schema with structured metadata)?
