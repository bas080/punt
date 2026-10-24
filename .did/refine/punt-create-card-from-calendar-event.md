# Create a Punt task card from calendar event creation in Punt calendar (#refine)

Enable bidirectionality when creating events in external calendar apps (e.g., Google Calendar, Samsung Calendar, Etar) under the local "Punt Reminders" calendar. When a user creates a new event in the "Punt Reminders" calendar outside of Punt, Punt detects the new event and imports it as a new task card in Punt.

## Details
- Extend `CalendarSyncManager` to query all events in the "Punt Reminders" calendar and identify events whose `EVENT_LOCATION` or title key is not yet present in Punt's saved reminders or `syncedCalendarKeys`.
- Convert new external calendar events into Punt task card text, preserving title, start time (punting future events until `dtstart`), and description text.
- Parse `VALARM` entries (calendar alarms / reminders from `CalendarContract.Reminders`) attached to the event to generate corresponding `#notify` tags (e.g., `#notify 15m`, `#notify 1h`) and/or `#every` recurrence tags.
- Parse recurrence rules (`RRULE`) or repeated `VALARM` definitions if specified in the calendar event to populate `#every` or `#repeat` tags.

## Questions for @bas080
1. Should newly created calendar events with future start times (`dtstart`) automatically snooze/punt the created Punt task card until `dtstart`?
2. When parsing `VALARM` entries from `CalendarContract.Reminders`, how should relative alarm offsets (e.g., 15 minutes before event) be formatted in the task string (e.g. `#notify 15m` vs `#every 15m`)?
3. Should Punt listen for calendar additions in real time using a background `ContentObserver` / `WorkManager` task, or sync on app launch / resume?
