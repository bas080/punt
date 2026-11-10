Add Punt server synchronization support to Android client

Integrate the Android Punt client with the Punt backend server over local network HTTP.
The Android phone is the sole authoritative source of truth. Its local data (`reminders_prefs` and `punt_tasks_backup.md`) is the ground truth.
Other clients (server, web, CLI) do not persist data; they simply receive and display data from the Android phone.

### Synchronization & Data Flow Model
- **No Complex Deltas**: When connected to the local server, the Android app dumps its complete list of reminders to the server upon initial connection.
- **Push on Change**: Whenever a reminder change occurs locally while connected (created, edited, punted, marked done), the Android app pushes the complete updated list to the server.
- **Mutation Ingestion**: The phone listens for mutation requests relayed from the server (originating from web or CLI). Upon receiving a mutation:
  1. The phone applies the change to its local state (`reminders_prefs` and `TaskBackupManager`).
  2. The phone sends acknowledgment and pushes the updated complete list back to the server.
- **Data Flow Cycle**:
  `android -> server -> web/cli -> server -> android -> server`
- **Strictly Local Network**: Local Wi-Fi connection only; if the local server is unreachable, server sync is paused without falling back to cloud/remote services.

### Android Integration Specifications
- **Setup Dialog**: Accessible from the About dialog (`showAboutDialog()`) in `MainActivity`. Prompts for Server Endpoint URL (`http://<host>:<port>`) and single-use pairing code.
- **Connection Storage**: Stores `server_endpoint` and `server_token` in `reminders_prefs`.
- **Connection LifeCycle**: Automatically detects local network availability and connects to the server mirror when present.

## Questions for @bas080
1. Should the Android client connect to the server via an active WebSocket connection while the app is in the foreground, or use periodic HTTP long-polling?
