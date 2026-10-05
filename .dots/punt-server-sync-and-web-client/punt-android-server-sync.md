---
title: Add Punt server synchronization support to Android client
status: open
priority: 2
issue-type: feature
blocks:
  - punt-build-backend-server
created-at: "2026-10-05T15:40:00.000000+00:00"
---

Integrate the Android Punt client with the Punt backend server to perform background sync of active/punted reminders and done states over local network HTTP.
Allow users to configure where the server lives (server URL/host and port) and perform initial connection setup using temporary pairing codes instead of username/password authentication.
