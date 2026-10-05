---
title: Rename package com.bas080.notificationreminders to com.bas080.punt
status: in_progress
priority: 2
issue-type: task
created-at: "2026-10-05T14:10:00.000000+00:00"
---

Refactor package structure and references across the codebase to rename package `com.bas080.notificationreminders` to `com.bas080.punt`.

## Migration Strategy
1. **TaskBackupManager Release**: Release `TaskBackupManager` in the current package (`com.bas080.notificationreminders`) so existing users have their tasks automatically exported/backed up to `punt_tasks_backup.md` upon app launch, pause, and edits.
2. **Package Migration**: Perform full refactoring to rename package `com.bas080.notificationreminders` to `com.bas080.punt` across code, gradle config, manifests, components, and metadata.
3. **Automatic Restore**: On first launch of `com.bas080.punt` (when `reminders_prefs` is empty), `TaskBackupManager.restoreIfEmpty` automatically detects and imports the backed-up tasks from `punt_tasks_backup.md`.
