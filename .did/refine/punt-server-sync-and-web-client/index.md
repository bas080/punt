Connect Punt Android client to Punt backend server with offline-capable web client

Enable multi-device synchronization over local network (HTTP) by connecting the Punt Android client to a backend Punt server.
Authentication is established once using temporary setup pairing codes instead of username/password credentials.
The backend server handles synchronization only (no query endpoints), and clients retain full local state of tasks, guaranteeing a robust offline-first experience.
Synchronization occurs whenever network connection is available using a research-backed, patch-like delta merging protocol that preserves concurrent contributions across devices without overwriting data.
The server component includes an optional web client (Progressive Web App with offline support) for managing reminders across web browsers.
Prior to implementation, open standards like CalDAV or other established task tracking open standards should be explored as potential protocols (as a non-hard requirement option).


## Questions for @bas080
1. Should we adopt CalDAV/iCalendar standards for task synchronization or use a custom JSON delta sync payload over HTTP?
2. What is the preferred fallback mechanism when local network host discovery fails?
