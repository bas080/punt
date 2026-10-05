---
title: Rethink when to update the Punt Android notification without reading system notifications
status: open
priority: 2
issue-type: task
created-at: "2026-10-05T14:25:00.000000+00:00"
---

Explore and design alternative notification trigger mechanisms to replace reading system notifications (`BIND_NOTIFICATION_LISTENER_SERVICE`).
The goal is to eliminate high-risk notification listener permissions by finding a simpler approach that updates Punt's status notification without requiring explicit high-risk user permissions.
