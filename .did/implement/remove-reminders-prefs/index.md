# Implement: Store reminders only in Room, keep prefs for user preferences

**Owner:** `@bas080`
**Status:** `implement`
**Origin:** Follow-up to `.did/refine/punt-state-management-audit.md` — the user chose to act on findings 1–8: task data must not live in SharedPreferences.

## Goal

`reminders_prefs` currently stores task data (`key_reminders_list` string set, `snooze_$key` timestamps) next to user preferences. Task state becomes Room-only; the preferences file keeps only user preferences:

- Keep: `key_search_query`, `key_focus_profiles`, `key_active_focus_profile(s)`, `key_active_collapsed`, `key_punted_collapsed`, `task_history_$key` / `last_choice_$key` (adaptive snooze suggestions), `key_legacy_migration_done` (migration marker).
- Remove: `key_reminders_list` (+ legacy `key_reminders`), every `snooze_$key` entry — reads and writes.

## Design

- Room (`reminders` table) is the single persistent store for tasks, including punt/reminder times (`snooze_until`).
- `lastTriggeredMap` keeps a single role: notification-read cooldowns. Its `snooze_*` entries and fallback reads are removed.
- Mutation paths (12 receiver/activity/service call sites) write Room synchronously (`runBlocking`) so the state is consistent when toasts/status notifications render; previously they mirrored to prefs synchronously and wrote Room fire-and-forget.
- `MainActivity` keeps in-memory view state only: task display list + `taskSnoozeByName` snapshot loaded from Room, updated by the same mutations.
- `TaskBackupManager` becomes Room-only (backup source and restore target), which also fixes the `key_reminders` / `key_reminders_list` key mismatch from the audit.
- `LegacyStorageMigration` stays: it is the one intentional read of legacy prefs task data, moving it into Room once.
- `ServerSyncManager` no longer projects mutations into prefs; the phone's repository state is Room.

## Subissues

- [x] [Android task code](subissues/.android-room-only.md)
- [x] [Tests](subissues/.tests-room-seeding.md)

## Verification

- `./gradlew detekt lint test testDebugUnitTest` in CI (no local Android SDK; Robolectric suites cover behavior).
- New/updated tests assert task state through `ReminderDao` instead of SharedPreferences.

## Implementation log

- All 9 main sources edited: task data reads/writes removed everywhere except `LegacyStorageMigration` (intentional). Verified by repo-wide grep: no `key_reminders_list`/`key_reminders` string-set or `snooze_$key` pref operations remain in `app/src/main` outside the migration.
- Room writes on mutation paths made synchronous (`runBlocking`); `lastTriggeredMap` now only holds notification-read cooldowns.
- All 9 listed test suites now seed via `ReminderDao.insert` (`runBlocking`), assert through the dao, and clear the DB (`deleteAll`) in `setUp`, plus delete the markdown backup file so `restoreIfEmpty` cannot resurrect stale tasks between tests.
- User-preference assertions (search query, focus profiles, task history/`last_choice`) intentionally keep using prefs.
