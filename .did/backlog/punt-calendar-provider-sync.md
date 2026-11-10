# Punt Calendar Provider Sync

Retain specification and backlog issue for integrating Punt reminders with Android's system `CalendarContract` (Calendar Provider) under a local calendar named 'Punt Reminders'.

### Requirements
- Provide declarative projection of tasks to `CalendarContract.Events`.
- Support `#notify` offset tags as `CalendarContract.Reminders` alarms.
- Handle bi-directional syncing or importing of externally created events.
