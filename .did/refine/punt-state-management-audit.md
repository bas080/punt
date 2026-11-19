# Refine: State Management Audit — Make Where State Lives and How It Travels Clear

**Owner:** `@bas080`
**Status:** `refine`
**Scope:** Android app (Room + SharedPreferences + process state), server relay, web client, CLI

---

## 1. Goal

Today it is not possible to answer "where is the current state of task X?" by reading
the code — the answer depends on the task, the entry point, and whether the process
restarted. This issue maps every store, documents the flows, lists the concrete risks
found, and proposes a target model. It is a report first; implementation would follow
as separate `implement` issues once the questions at the end are answered.

---

## 2. Where state lives — complete inventory

### 2.1 Room (intended canonical store)

`data/PuntDatabase.kt` / `data/ReminderEntity.kt` / `data/ReminderDao.kt`

| Column | Meaning |
|---|---|
| `id` | Primary key |
| `text` | Cleaned task text — **also encodes `#done` inline** |
| `created_at` / `updated_at` | Timestamps |
| `snooze_until` | Punt/reminder time (nullable) |
| `is_done` | Done flag — **duplicate of the `#done` tag in `text`** |
| `recurrence_interval` | Recurrence |

DAO is suspend-only and reasonably narrow, but there is no repository layer and no
Flow/LiveData: every consumer calls `runBlocking { dao.getAllReminders() }` or pulls a
fresh list after its own mutation. Identity is **text**, not `id`: nearly every DAO
method takes `text` as the lookup key (`updateSnooze(text, …)`, `markDone(text, …)`,
`deleteByText(text)`).

### 2.2 SharedPreferences — `reminders_prefs` (`PREFS_REMINDERS`)

48 touchpoints across 9 files (MainActivity, CreateReminderReceiver, CreateReminderActivity,
ProcessTextActivity, PickNotificationActivity, SnoozeDialogActivity, ListenerService,
TaskBackupManager, LegacyStorageMigration, ServerSyncManager). Contents:

- `key_reminders_list` — `StringSet` of task texts. **Order is lost** by the
  `StringSet` type and the set is only a legacy mirror of Room now.
- `snooze_$cleanTrimmed` (Long) — **second copy of the punt/reminder time**, keyed by
  lowercased trimmed text (not id). Written by MainActivity, CreateReminderReceiver,
  SnoozeDialogActivity, ServerSyncManager; read by MainActivity and export/punt flows.
- Search query, focus-profile keys, per-section collapse flags — UI-persistent state
  mixed into the same file as task data.

### 2.3 Static / process-local state

- `ReminderNotificationListenerService.lastTriggeredMap: ConcurrentHashMap<String, Long>`
  — third copy of punt times plus notification cooldowns (`key → last triggered millis`).
  **Volatile: lost on process death**, so cooldowns quietly reset.
- `MainActivity.recentlyDoneReminders`, `pendingEditRunnable` (edit debounce) — UI-only
  timers live as bare fields in the Activity.

### 2.4 Other stores

- `snooze_freq_prefs` — adaptive punt choices: `choice_key` → timestamp, `count_$choice`.
  Declared/re-implemented in 3 files.
- `server_sync_prefs` — `key_last_server_endpoint`; written in one file, read in another.
- `experiment_prefs` (`ExperimentTracker`), `crash_prefs` (`App`).
- `AppLogger` log file; backup file `punt_tasks_backup.md` (external-files/downloads +
  hardcoded `/sdcard/...` paths).
- Notification tray + status notification — **derived state**, rebuilt from
  Room + `lastTriggeredMap`.

### 2.5 Server (`server/`) and clients

- Server is an in-memory relay: `tasks`, `phoneSocket`, `phoneVersion`, pairing
  code/expiry/timer, `sessionToken`, `pendingMutations` (mutationId → deferred).
- Web client (`src/web/main.ts`): one module-level mutable `state` singleton with manual
  `updateView()` calls; localStorage `punt_web_*` mirrors UI state.
- **Focus profiles are browser-local on web but phone-local in the APK** — two
  disconnected stores for the same concept.
- CLI is stateless.

---

## 3. How state travels

```
                         ┌─────────────────── PHONE (source of truth) ───────────────────┐
                         │                                                               │
  Any of ~10 entry      │  prefs write        async Room write      notification         │
  points:               │  (snooze_$key,      (insert/update/       rebuild              │
  MainActivity          │   key_reminders_    deleteByText)         (from Room + map)    │
  CreateReceiver/Act.   │   list)             |                     |                    │
  ProcessText           │      |              |                     |                    │
  SnoozeDialog          │      |              v                     v                    │
  PickNotification      │      └────► Room ◄──── Room ◄──── ListenerService            │
  ServerSyncManager     │             (dao calls, often runBlocking)                   │
  Notification actions  │                    |                                        │
  Backup restore        │                    └──► tasks_dump ──► Server relay           │
                         │                                       |    |                 │
                         └───────────────────────────────────────┼────┼─────────────────┘
                                                                 v    v
                                                          web client   CLI
                                                                 ^    ^
                                                                 |    |
                        mutation_request ◄── server ◄── mutate ◄──┴────┘
                        (mutationId; phone applies via SyncActions, sync write-back   │
                         to prefs + map mirrors the local paths above)                └─ fail-fast "Phone is unreachable" if disconnected
```

Observed flow problems:

1. **~10 entry points each re-implement the write sequence** (prefs edit + map update +
   dao call + notification refresh) instead of calling one mutation path. The web-parity
   work added `SyncActions` for server mutations, but local UI paths still do their own
   thing (e.g. `CreateReminderReceiver` alone has 3 separate snooze-write blocks).
2. **Reads are pull-through**: every change triggers a full `getAllReminders()` and a
   full adapter rebuild in MainActivity; no observation, no diffing.
3. **Precedence is inconsistent between readers**:
   - `ListenerService` reads punt time as **Room → `lastTriggeredMap`**;
   - `MainActivity`/export/punt read it as **prefs → `lastTriggeredMap`**.
   Same fact, two different sources depending on the reader.

---

## 4. Findings and risks

1. **Triple snooze truth** — Room `snooze_until`, prefs `snooze_$key`, and static
   `lastTriggeredMap` disagree over precedence, and the map dies with the process.
   This is the most likely source of "punt time forgot itself" / double-notification
   bugs.
2. **Canonical-store ambiguity** — Room is the migration target, yet 48 prefs
   touchpoints remain and the legacy `key_reminders_list` set is still written
   on every mutation (including `SyncActions` in ServerSyncManager). Every new
   entry point must remember to keep the mirror in sync.
3. **Likely bug — backup restore key mismatch**: `TaskBackupManager` uses
   `KEY_REMINDERS = "key_reminders"` (utils/TaskBackupManager.kt:14) everywhere else
   uses `"key_reminders_list"`. `restoreIfEmpty()` checks Room count **OR** this pref —
   so with mismatches the prefs read is always null, and the detection rests on Room
   alone. If the intent was "don't restore if the legacy pref still has content", this
   silently never triggers. Needs confirmation (question Q1).
4. **Duplicated constants** — `KEY_REMINDERS = "key_reminders_list"` is re-declared in
   5 files and `PREFS_SNOOZE_FREQ` in 3; one typo (`key_reminders`) already shipped.
5. **Dual done-encoding** — `isDone` column and `#done` tag in `text` must be kept in
   sync by every writer; `updateText(old, new)` has no way to express done-state, so
   server `edit` has to re-derive it from the text.
6. **`runBlocking` on the main threads** — 8 call sites: ListenerService ×3,
   TaskBackupManager ×3 (incl. restore), ContentProvider ×1, CreateReminderReceiver ×1.
   ANR risk under load.
7. **Volatile cooldowns** — `lastTriggeredMap` cooldown state resets on process death
   (related to finding 1; distinct because cooldown ≠ punt time).
8. **StringSet loses order** — `key_reminders_list` cannot represent manual ordering;
   the Android UI orders from Room, web orders from dump, the set is dead weight.
9. **Text as identity** — two tasks with the same cleaned text are one task for DAO
   purposes (updates/deletes are by text). Renaming an edit key off text would require
   care (already touched by the edit flow's prefs-key rename code in MainActivity:879).
10. **Web/web-Android profile split** — focus profiles live in two different stores with
    no sync path.

---

## 5. Recommended target model

1. **Room becomes the single source of truth for tasks.** All task reads/writes go
   through one `TaskRepository` exposing a `Flow<List<ReminderEntity>>` (or
   `LiveData`); UI observes instead of pull-rebuilding. Prefs mirror +
   `lastTriggeredMap` become read-only legacy (kept only to serve the
   `LegacyStorageMigration`/restore path) then deleted.
2. **Punt time moves to Room only**, one behavior-preserving migration, one writer.
   `lastTriggeredMap` keeps only *cooldown* state, ideally in a small persisted table
   or file so it survives restarts (Q5).
3. **One `Keys` contract object** (single file) for every SharedPreferences file and
   key — kills the 5× constant duplication and prevents `key_reminders`-style typos.
4. **One mutation path**: every entry point (UI, receivers, listener actions, server
   `SyncActions`, backup restore) calls a single repository method per action
   (create / edit / done / undone / punt / unpunt / delete). Repository owns the
   write side-effects (notification refresh, server change signal).
5. **ViewModel for transient UI state** in MainActivity (search text, focus drafts,
   `pendingEditRunnable`, `recentlyDoneReminders`, collapse flags) so the Activity
   holds no state fields.
6. **Remove `runBlocking`**: repository is suspend/Flow-based; the ContentProvider and
   backup paths run on their own dispatchers.
7. **Server/web**: keep the relay as-is, but once the phone is repository-driven,
   `tasks_dump` becomes a simple repository projection and `SyncActions` the only
   writer — the web client's reducer-style single-state model already matches this.
8. **Decision needed** on focus profiles being phone-side state (then add them to the
   dump/sync) vs staying client-local per platform (then document that).

---

## 6. Open questions — `@bas080`

1. `TaskBackupManager` uses key `"key_reminders"` while everywhere else is
   `"key_reminders_list"`. Was that intentional (backup-only key), or a typo that
   disables the prefs half of `restoreIfEmpty()`?
2. Should focus profiles become phone-synced state (part of dump + mutations), or stay
   client-local per platform?
3. Is the legacy `key_reminders_list` prefs mirror ready to be retired (kept read-only
   for migration/restore only)? Any consumer outside this repo (externals reading the
   set) that we must keep writing for?
4. Priority: (a) repository layer + single mutation path first, or (b) collapse the
   snooze-triple-store first? (a) is more work but makes (b) trivial; (b) is a smaller
   fix for the most user-visible bug class.
5. Must notification cooldowns survive process death? If yes, they need a persisted
   home (Room table or small file).
6. Is text-based identity in the DAO acceptable long-term, or should lookups move to
   `id` (the dump/mutation target already falls back to `task.text` only when `id` is
   missing)?
