Integrate MainActivity, CreateReminderReceiver, NotificationListener, and TaskBackupManager with Room database

Refactor application components from direct `reminders_prefs` reading/writing to Room database persistence via `ReminderDao`:
- **TaskBackupManager**: Serializes the full database state to a JSON backup file (`punt_tasks_backup.json`) preserving exact record IDs, timestamps, clean text, snooze until, and completion boolean (`isDone`), while retaining markdown export for note-taking/sharing.
- **MainActivity**: Observes tasks or reads/writes via `ReminderDao` (active, punted, and done lists).
- **CreateReminderReceiver**: Inserts new reminders into `ReminderDao` and calculates/stores recurrence intervals.
- **ReminderNotificationListenerService**: Queries `ReminderDao` for active and punted counts and direct keyword matches.
