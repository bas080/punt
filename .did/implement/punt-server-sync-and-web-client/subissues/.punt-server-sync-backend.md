Build Punt backend synchronization server in Node.js and TypeScript

Implement a lightweight Punt backend server in Node.js and TypeScript over local network HTTP and WebSockets.
The server functions purely as an in-memory relay and temporary cache between the Android phone, the web client, and the CLI.

### Architectural Model
- **Pure In-Memory State (No Database)**: The server does NOT persist data to a database. All task data is stored in memory in Node.js while the server is running. When the Android phone connects, it hydrates the server's memory with the current state.
- **Phone as Sole Source of Truth**: The phone's local storage (`reminders_prefs` / `punt_tasks_backup.md`) is the only permanent persistence.
- **Full Task List Dumps (No Deltas)**: The phone pushes its complete list of reminders to the server over WebSockets upon connecting and whenever a local change occurs while connected.
- **Data Flow**:
  `android -> server (WebSocket) -> web/cli -> server (WebSocket) -> android -> server`
- **Port Configuration**: Defaults to port `7868` (mnemonic for "PUNT"). Configurable via `PUNT_PORT` environment variable or `--port` CLI flag.
- **Authentication**:
  - The Android app generates a 4-digit code (valid 15m).
  - The server receives this code via `--code <code>` or prompt when started.
  - The server pairs with the phone, burns the 4-digit code, and establishes an authorized session token.
- **PWA Asset Serving**: Serves static web assets including `manifest.json` and a minimal service worker enabling standalone desktop app installation.
- **Strictly Local Network**: Fails immediately if local network communication fails.

### Technical Specifications
- **Runtime**: Node.js & TypeScript.
- **Default Port**: 7868 (configurable via `PUNT_PORT` env var or `--port` option).
- **Persistence**: None (in-memory state only).
- **Endpoints & Protocols**:
  - `GET /api/ping`: Fast discovery ping endpoint returning `{ "app": "punt-server", "version": "..." }`.
  - `POST /api/pair`: Handshake endpoint accepting the 4-digit code from the phone or server to issue session tokens.
  - `WS /api/ws`: Authenticated WebSocket connection endpoint for the phone and web clients for real-time list hydration, mutation dispatching, and broadcast.
  - `GET /api/tasks`: Returns current in-memory mirrored tasks to web clients and CLI commands.
  - `POST /api/tasks/mutate`: HTTP endpoint for CLI commands to dispatch task mutations to the phone through the server.

### Automated Tests to Write
1. **Pairing & Token Validation Tests**:
   - `testPairingWithValid4DigitCode`: Confirms code is accepted, burned (cannot be reused), and returns session token.
   - `testPairingWithExpiredCode`: Confirms code older than 15 minutes is rejected with `401 Unauthorized`.
   - `testPairingWithInvalidCode`: Confirms non-matching codes are rejected.
2. **WebSocket Connection & Hydration Tests**:
   - `testWebSocketConnectionWithoutAuth`: Confirms connection is rejected if session token is missing or invalid.
   - `testFullTaskListHydration`: Pushes task array over phone WebSocket; confirms in-memory store updates and `GET /api/tasks` returns identical tasks.
   - `testBroadcastToConnectedClients`: Confirms when phone updates tasks, all active web WebSockets receive the updated list.
3. **Mutation Relay & Timeout Tests**:
   - `testMutationRelayedToPhone`: Sends mutation from web/HTTP; verifies message arrives on phone WebSocket.
   - `testMutationTimeoutWhenPhoneDisconnected`: Sends mutation when phone WebSocket is closed; verifies immediate 504 / fail-fast error returned within timeout window.
4. **Static PWA Serving Tests**:
   - `testServesIndexHtml`: Verifies `GET /` returns the web client HTML.
   - `testServesManifestAndServiceWorker`: Verifies `GET /manifest.json` returns valid JSON with correct app name and icons.
