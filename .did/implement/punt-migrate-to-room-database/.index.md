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

### Automated Test Approach for Migration & Database
1. **Migration Verification Tests (`LegacyStorageMigrationTest`)**:
   - `testMigratesRemindersAndSnoozesFromPrefs`: Populates `reminders_prefs` with active, punted, and completed (`#done`) reminders and snooze timestamps; triggers migration; verifies:
     - All records exist in Room database with correct `isDone` boolean.
     - Clean `text` column has `#done` stripped.
     - `snoozeUntil` timestamps match legacy `snooze_$cleanTrimmed` values.
     - Legacy migration flag `key_legacy_migration_done` is set to `true`.
   - `testMigrationRunsOnlyOnce`: Re-runs migration check when `key_legacy_migration_done` is `true`; verifies existing Room records are not duplicated.
   - `testMigrationWithEmptyLegacyPrefs`: Verifies clean initialization when starting fresh with no legacy data.
2. **Room DAO CRUD & Reactive Query Tests (`ReminderDaoTest`)**:
   - `testInsertAndQueryActiveReminders`: Verifies inserting reminders and querying active items (`isDone == false && (snoozeUntil == null || snoozeUntil <= now)`).
   - `testPuntReminderUpdatesSnoozeUntil`: Verifies punting updates `snoozeUntil` timestamp.
   - `testMarkDoneUpdatesIsDoneColumn`: Verifies marking done updates `isDone = true` without modifying text.
   - `testFlowEmitsUpdatesOnDatabaseChange`: Verifies reactive `Flow<List<ReminderEntity>>` emits new lists on insertion/deletion.
3. **Backup Import / Export Tests (`TaskBackupManagerTest`)**:
   - `testExportMarkdownVsBackupExport`: Verifies Markdown export produces human-readable markdown with `- [x]`, while backup export produces full-fidelity database state with IDs and timestamps.
   - `testBackupRestoreReconstitutesRoomDatabase`: Restores a backup file into an empty Room database; verifies exact ID and timestamp fidelity.
