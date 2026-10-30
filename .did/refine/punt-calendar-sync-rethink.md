# Rethink Calendar Sync Architecture

The calendar sync feature is currently disabled by default on fresh installs (`KEY_CALENDAR_SYNC_ENABLED` defaults to `false` in `CalendarSyncManager.kt`).

This document outlines key findings and open design questions from session research to guide rethinking calendar sync from the ground up:

## Key Architectural Findings & Observations

1. **State Ownership & Unidirectional Flow:**
   - Punt's `reminders_prefs` (`KEY_REMINDERS` string set and `snooze_$cleanKey` timestamps) acts as the primary master repository.
   - System Calendar (`CalendarContract.Events` under 'Punt Reminders') operates as a projected display view.

2. **Event Origin Classification using `SYNC_DATA1`:**
   - **Punt-Managed Events:** Carry `SYNC_DATA1 == cleanKey`. Created and updated by Punt during task edits and snooze changes.
   - **External Calendar Events:** Created directly by users in calendar apps (Google Calendar, Etar, etc.) with `SYNC_DATA1 == null`. Imported into Punt as task cards and stamped with `SYNC_DATA1 = cleanKey`.

3. **Inline Typing Keystroke Insulation:**
   - Sync writes to system calendar provider must not run on character-by-character debounced typing. Sync executes only when editing is completed (focus loss, keyboard dismissal, `onPause()`, or lifecycle actions).

4. **Swipe Gestures vs. Punted Recurring Tasks:**
   - Active recurring tasks swiped right advance their recurrence duration (`#every`).
   - Punted recurring tasks (`snoozeUntil > now`) swiped right mark `#done` and render the checkmark peek icon (`ic_action_done`).

## Open Questions for Developer `@bas080`

1. **Calendar Event Title Formatting:**
   Should Punt strip tags (e.g. `#work`, `#every1d`) when creating/displaying event titles in external calendar apps, or keep the raw task string?
2. **Calendar Event Deletion Handling:**
   When a user deletes a non-recurring event in Google Calendar, should Punt append `#done` (retaining completed task history) or permanently delete the task string from Punt?
3. **All-Day vs. Scheduled Time Display:**
   Currently, unpunted tasks expose as all-day todo events (defaulting to 09:00 AM for `#notify` alarms), while punted tasks expose timed start/end intervals (`dtstart`/`dtend`). Should this pattern be retained or standardized?
