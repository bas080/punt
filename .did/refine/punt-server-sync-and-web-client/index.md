Connect Punt Android client to Punt backend server with offline-capable web client

Enable phone-to-browser task access and management over the local network (HTTP) by connecting the Punt Android client to a lightweight backend Punt server and CLI implemented in Node.js and TypeScript.

### Single Source of Truth Architecture
- **Phone as Sole Source of Truth**: The task document on the Android phone (`reminders_prefs` / `punt_tasks_backup.md`) is the only authoritative, persistent source of truth.
- **Server as In-Memory Relay (No Database)**: The server has no database; it stores the active task list in memory while running, hydrating its state whenever the phone connects.
- **Full List Dumps (No Deltas)**: Synchronization does not use complex delta merging or CalDAV. The phone dumps its complete list to the server upon connecting and whenever a local task change occurs.
- **Data Flow Cycle**:
  `android -> server -> web/cli -> server -> android -> server`
- **Synchronous Mutation Propagation**: Any mutation initiated from the web interface or CLI is forwarded by the server to the Android phone. If the phone is offline or unreachable, the mutation operation fails immediately.
- **Strictly Local Network**: Operates strictly over the local network (no cloud or internet fallback).
- **Authentication & Pairing**:
  - The phone pairs via the server endpoint URL (`http://<host>:<port>`) and a single-use setup code (displayed in server stdout and the web interface; no QR codes).
  - The web client uses Browser Origin Authentication (automatically authorized because it is served directly from the server origin).
- **Tech Stack**:
  - Backend Server: Node.js & TypeScript (pure in-memory).
  - Companion CLI: `punt-server` using `yargs` (spawns server and connects to running server to inspect/mutate tasks).
  - Web Client: Vanilla TypeScript/HTML/CSS with `lit-html` templating.

## Recommended Implementation Order (Subissues)
1. **Phase 1: Backend Server (`subissues/punt-server-sync-backend.md`)**
   - Node.js & TypeScript in-memory HTTP server, single-use pairing code generation and phone session token validation (`POST /api/pair`), task list hydration endpoint (`POST /api/sync/phone`), and mutation relay endpoints (`/api/tasks`, `/api/tasks/mutate`).
   - Rationale: Establishes the core relay protocol between phone, web, and CLI.
2. **Phase 2: Server CLI (`subissues/punt-server-cli.md`)**
   - `yargs`-based command-line runner (`punt-server start`, `punt-server pair`, `punt-server status`, `punt-server tasks list|add|done|punt`) that starts the server or communicates with the running server.
   - Rationale: Enables starting the server and exercising the mutation relay endpoints from the terminal.
3. **Phase 3: Android Client Integration (`subissues/punt-server-android-sync.md`)**
   - Setup dialog in Android app (Host/Port + code entry), auto-discovery/connection on local Wi-Fi, full task dump push on connect and edits, and mutation command execution listener.
   - Rationale: Connects the actual source-of-truth task store to the server relay.
4. **Phase 4: Web Client with lit-html (`subissues/punt-server-web-client.md`)**
   - Lightweight `lit-html` web UI served from origin, live task mirroring from server in-memory state, and fail-fast task mutations via `/api/tasks/mutate`.
   - Rationale: Builds the browser UI on top of the validated server relay and phone connection.
