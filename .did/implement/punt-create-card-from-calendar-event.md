# Create a Punt task card from calendar event creation in Punt calendar

Enable bidirectionality when creating events in external calendar apps (e.g., Google Calendar, Samsung Calendar, Etar) under the local "Punt Reminders" calendar. When a user creates a new event in the "Punt Reminders" calendar outside of Punt, Punt detects the new event and imports it as a new task card in Punt.

## Implementation Specification
- Extend `CalendarSyncManager` to query all events in the "Punt Reminders" calendar and identify events whose `EVENT_LOCATION` or title key is not yet present in Punt's saved reminders or `syncedCalendarKeys`.
- Convert new external calendar events into Punt task card text, preserving title, start time (`dtstart`), and description text.
- If the event's start time (`dtstart`) is in the future, automatically snooze/punt the created Punt task card until `dtstart`.
- Parse `VALARM` entries (calendar alarms / reminders from `CalendarContract.Reminders`) attached to the event to generate corresponding single-word tags like `#every15m` or `#notify15m`.
- Parse recurrence rules (`RRULE`) or repeated `VALARM` definitions if specified in the calendar event to populate `#every` or `#repeat` tags.
- Register a `ContentObserver` on `CalendarContract.Events.CONTENT_URI` combined with app launch / resume sync in `MainActivity` to detect and import external calendar changes seamlessly.
