Add Punt server synchronization support to Android client

Integrate the Android Punt client with the Punt backend server over local network HTTP and WebSockets.
The Android phone is the sole authoritative source of truth. Its local data (`reminders_prefs` and `punt_tasks_backup.md`) is the ground truth.
Other clients (server, web, CLI) do not persist data; they simply receive and display data from the Android phone.

### Synchronization & Discovery Model
- **4-Digit Setup Code Generation**: When user enables "Server Sync", the Android app generates a random 4-digit code (e.g. `4829`) valid for 15 minutes, displayed on-screen along with the phone's current Wi-Fi IP address (e.g. `192.168.1.105`).
- **Direct Reconnect (No Background Scanning)**:
  - When the app is launched or connects to Wi-Fi, if a server was previously connected, it directly pings that known IP and port. It does NOT run background scans.
  - If the phone connects to a different Wi-Fi network / subnet, it skips the ping and indicates the server is offline.
- **Manual-Only Subnet Scan ("Scan for Server")**:
  - Scanning is strictly user-initiated by tapping a "Scan for Server" button in the Server Sync dialog.
  - Probes port 7868 across the local `/24` subnet using short timeouts (200ms per address).
  - Fast Abort: Immediately cancels remaining coroutine probes as soon as a valid Punt server responds.
  - An input field is also provided to allow manual endpoint override (`http://<custom-ip>:<port>`) if port 7868 cannot be used.
- **WebSocket Transport**: Android connects to the server's WebSocket endpoint (`WS /api/ws`) using an authenticated session.
- **No Complex Deltas**: When connecting, the Android app dumps its complete list of reminders to the server.
- **Push on Change**: Whenever a reminder change occurs locally while connected (created, edited, punted, marked done), the Android app sends the complete updated list over the WebSocket.
- **Real-Time Mutation Ingestion**: The phone receives mutation messages over the WebSocket (originating from web or CLI). Upon receiving a mutation:
  1. The phone applies the change to its local state (`reminders_prefs` and `TaskBackupManager`).
  2. The phone immediately pushes the updated complete list back over the WebSocket to the server.
- **Strictly Local Network**: Local Wi-Fi connection only; if the local server is unreachable, server sync is paused without falling back to cloud/remote services.

### Android Integration Specifications
- **Setup Dialog**: Accessible from the About dialog (`showAboutDialog()`) in `MainActivity`. Generates the 4-digit code, shows local phone IP, provides the manual "Scan for Server" button, and provides manual endpoint override.
- **Connection Storage**: Stores `server_endpoint` and `server_token` in `reminders_prefs`.

### Automated Tests to Write (Robolectric / Unit Tests)
1. **Code Generation & Expiration Tests (`ServerSyncManagerTest`)**:
   - `testGenerate4DigitCodeFormat`: Verifies generated code is strictly a 4-digit numeric string (`Regex("^\\d{4}$")`).
   - `testCodeExpiresAfter15Minutes`: Verifies code is valid at 14m59s and expired/rejected after 15m.
   - `testCodeRegenerationInvalidatesPrevious`: Verifies generating a new code invalidates the older code.
2. **Subnet & Direct Ping Discovery Tests**:
   - `testDirectPingSuccessReconnects`: Mocks local server responding on known IP/port; verifies WebSocket connection is initiated immediately without scanning.
   - `testDifferentSubnetSkipsPing`: Mocks phone IP on `10.0.0.5` while known server is on `192.168.1.50`; verifies ping is skipped.
   - `testManualSubnetScanFastAbort`: Simulates 254 subnet IPs with server at IP 3; verifies remaining probes are cancelled once IP 3 responds with `{ "app": "punt-server" }`.
   - `testManualEndpointOverrideTakesPrecedence`: Sets manual override `http://192.168.1.99:8080`; verifies connection targets override directly.
3. **Task List Dumps & Mutation Ingestion Tests**:
   - `testFullTaskListDumpOnConnect`: Verifies phone serializes all active and punted reminders into JSON array and dispatches over WebSocket.
   - `testTaskCreationPushesUpdatedList`: Creates a new reminder locally; verifies complete updated list is sent through WebSocket.
   - `testIngestTaskMutationFromWeb`: Ingests a mutation message (e.g. mark done); verifies `reminders_prefs` updates, `TaskBackupManager` saves, and updated list is sent back.
4. **UI Dialog State Tests (`ServerSyncDialogTest`)**:
   - `testDialogRenders4DigitCodeAndPhoneIp`: Verifies dialog shows large code and current Wi-Fi IP address.
   - `testDialogTransitionsToConnectedState`: Simulates WebSocket connection; verifies UI updates to show connected status and active server IP.
