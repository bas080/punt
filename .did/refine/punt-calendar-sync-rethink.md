# Refine: Rethinking Calendar Synchronization Architecture from the Ground Up

**Owner:** `@bas080`
**Status:** `refine`
**Feature:** Two-Way / Projection-Based Calendar Synchronization

---

## 1. Architectural Vision & Core Principles

To eliminate race conditions, duplicate intermediate typing draft cards, and synchronization drift, Calendar Sync will be re-architected around a **pure, declarative projection model**:

1. **Punt as the Single Source of Truth (`reminders_prefs`):**
   - Punt's task list (`KEY_REMINDERS`) and snooze/punt timestamps (`snooze_$cleanKey`) are the master state.
   - The system calendar ("Punt Reminders") is a projected display view computed from Punt's state.

2. **Metadata Anchor Column (`SYNC_DATA1`):**
   - Standard Android `CalendarContract.Events.SYNC_DATA1` column is used as an internal, user-invisible task anchor (`SYNC_DATA1 = cleanKey`).
   - Events in the "Punt Reminders" calendar created by Punt have `SYNC_DATA1 == cleanKey`.
   - Events created directly by users inside external calendar apps have `SYNC_DATA1 == null`.

3. **Lifecycle-Only Sync Triggering:**
   - Calendar sync writes will **never** run on character-by-character debounced typing.
   - Sync executes only on task edit completion (focus lost, keyboard closed, `onPause()`), explicit user actions, or periodic background sweeps.

---

## 2. Unidirectional Data Flow

```
[ Punt Task List ] ------------ Bulk Projection Sync ------------> [ System Calendar ("Punt Reminders") ]
(Master State)                                                    (SYNC_DATA1 = cleanKey)
       ^                                                                    |
       |                                                                    |
       +----- Import External Events (SYNC_DATA1 == null) <-----------------+
       +----- Sync External Moves / Deletions (SYNC_DATA1 == cleanKey) <----+
```

### A. Punt $\rightarrow$ Calendar (Projection & Bulk Reconciliation)
1. For every active task in Punt, query calendar entries by `SYNC_DATA1 == cleanKey`.
2. Update existing entries in place or insert missing ones with `SYNC_DATA1 = cleanKey`.
3. Purge any orphan calendar entry whose `SYNC_DATA1` is no longer present in Punt's active tasks.

### B. External Calendar $\rightarrow$ Punt (Import & Event Move/Delete Sync)
1. **New Calendar Events (`SYNC_DATA1 == null`):**
   - Import into Punt as new task cards.
   - Parse `VALARM` notification offsets into `#every` duration tags (e.g. `#every15m`).
   - Punt future start times (`dtStart`).
   - Stamp `SYNC_DATA1 = cleanKey` on the calendar event so Punt takes ownership.
2. **Event Moved in Calendar App:**
   - Look up task by `SYNC_DATA1`. Update Punt's snooze timestamp (`snooze_$cleanKey = dtStart`).
3. **Event Deleted in Calendar App:**
   - Non-recurring task: Mark `#done` in Punt (`$task #done`).
   - Recurring `#every` task: Advance snooze timestamp in Punt relative to scheduled start date (`dtstart + recurrenceInterval`).

---

## 3. Open Refinement Questions for Developer `@bas080`

1. **Calendar Event Titles:**
   When Punt creates/updates events in the external calendar app, should Punt strip tags (e.g. `#work`, `#every1d`) or retain the raw task string?
2. **Deleting Events in Calendar:**
   When a non-recurring event is deleted in Google Calendar / Samsung Calendar, should Punt mark the card `#done` in Punt or permanently delete it?
3. **All-Day vs. Timed Events:**
   Unpunted tasks currently display as all-day events in calendar apps, while punted tasks display timed start/end intervals (`dtstart`/`dtend`). Should this pattern be maintained?

---

## 4. Verification & Testing Strategy
- Unit tests (`CalendarSyncManagerTest.kt`) using Robolectric mocking.
- Instrumented integration tests (`CalendarSyncAndroidTest.kt`) running on real system `CalendarContract` ContentProvider without mocks.
- Detekt static analysis and `./gradlew test` verification.
