Add Punt server synchronization support to Android client

Integrate the Android Punt client with the Punt backend server over local network HTTP.
The Android phone maintains the authoritative source of truth (`reminders_prefs` and `punt_tasks_backup.md`).
The phone mirrors its state to the local server and receives/applies mutation requests initiated from the web or CLI.
Users configure where the server lives (endpoint host and port) and pair once using single-use setup codes (no QR codes required).
Operates strictly over local network: if local network connection fails, there is no remote/cloud fallback.

### Android Integration Specifications
- **Setup UI**: A "Server Sync" option in MainActivity's About dialog (`showAboutDialog()`) opening a dialog to enter Endpoint URL (`http://<host>:<port>`) and single-use setup code.
- **Persistence**: Store `server_endpoint` and `server_token` in `reminders_prefs`.
- **Mirror Sync**: Pushes task updates to `POST /api/sync/phone` whenever tasks are created, edited, punted, or marked done.
- **Mutation Listener**: Listens for incoming task commands from the server while connected to the local network and commits them to `reminders_prefs` and `TaskBackupManager`.

## Questions for @bas080
1. Should the phone use a foreground WebSocket / Server-Sent Events (SSE) connection while the app is active, or use periodic polling?
2. When the phone is disconnected from the local network, should the Android app display an unobtrusive status dot indicating the server mirror is offline?
