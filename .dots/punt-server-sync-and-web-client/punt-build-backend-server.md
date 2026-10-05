---
title: Build initial Punt backend synchronization server
status: open
priority: 2
issue-type: feature
created-at: "2026-10-05T15:40:00.000000+00:00"
---

Implement the core Punt backend server REST / WebSocket APIs over local network HTTP for storing, querying, and synchronizing reminders and punt timestamps across devices.
Authentication uses temporary single-use setup codes for initial device pairing instead of username/password authentication, issuing persistent session tokens for subsequent synchronization.
