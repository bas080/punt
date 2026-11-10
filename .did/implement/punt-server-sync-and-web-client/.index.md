Connect Punt Android client to Punt backend server with offline-capable web client

Enable phone-to-browser task access and management over the local network (HTTP/WebSockets) by connecting the Punt Android client to a lightweight backend Punt server and CLI implemented in Node.js and TypeScript, installable and runnable on-demand via `npx punt`.

### Single Source of Truth Architecture
- **Phone as Sole Source of Truth**: The task document on the Android phone (`reminders_prefs` / `punt_tasks_backup.md`) is the only authoritative, persistent source of truth.
- **Server as In-Memory Relay (No Database)**: The server has no database; it stores the active task list in memory while running, hydrating its state whenever the phone connects.
- **Full List Dumps over WebSockets (No Deltas)**: Synchronization does not use complex delta merging or CalDAV. The phone dumps its complete list to the server over WebSockets upon connecting and whenever a local task change occurs.
- **Data Flow Cycle**:
  `android -> server (WebSocket) -> web/cli -> server (WebSocket) -> android -> server`
- **Synchronous Mutation Propagation**: Any mutation initiated from the web interface or CLI is forwarded in real time over WebSockets to the Android phone. If the phone is offline or unreachable, the mutation operation fails immediately.
- **Strictly Local Network**: Operates strictly over the local network (no cloud or internet fallback).

### Authentication & Discovery Flow
1. **Enable Feature on Android**:
   - The user enables "Server Sync" in the Android app (About dialog).
   - The phone generates a random **4-digit one-time pairing code** (e.g. `4829`) valid for 15 minutes, and displays the phone's current Wi-Fi IP address.
2. **Server Startup & npx Execution**:
   - Users can execute `npx punt serve [--code <code>] [--open]` without prior installation.
   - The npm package bundles both `"dist/server"` and pre-built `"dist/web"` artifacts via `"files"` and `"bin": { "punt": "./dist/server/cli.js" }` in `package.json`.
   - Starts the server on distinct default port 7868 (configurable via `PUNT_PORT` / `--port`), logs the local web URL (`http://localhost:7868`), and optionally opens the browser.
3. **Manual-Only Discovery on Android**:
   - The Android app does NOT run automatic background subnet scans.
   - On app launch/Wi-Fi connection, if previously connected, it directly pings the last known server IP/port. If the phone is on a different subnet, the ping is skipped.
   - When connecting for the first time or if the server IP changed, the user taps **"Scan for Server"**, which quickly probes port 7868 across the `/24` subnet and immediately cancels remaining probes once a server is found. A manual endpoint override field is also available.
4. **Automatic Web & CLI Authorization**:
   - Because the server is authenticated with the phone, the web client served directly from that origin inherits **Browser Origin Authentication** (instant access without secondary logins).
   - The CLI (`punt`) communicates directly through the running server.
5. **Progressive Web App (PWA) & Build Pipeline**:
   - Written in **TypeScript** using **`lit-html` functional templates** (no Custom Elements / Web Components).
   - Built with **`esbuild`** producing static artifacts in `dist/web/` (`index.html`, `app.js`, `style.css`, `manifest.json`, `sw.js`) served by the backend server.
   - Installable as a desktop app with dark theme matching Android. Standard browser interactions are used without custom single-key shortcuts.

## Recommended Implementation Order (Subissues)
1. **Phase 1: Backend Server (`subissues/punt-server-sync-backend.md`)**
   - Node.js & TypeScript in-memory HTTP and WebSocket server running on distinct default port 7868 (or `PUNT_PORT`).
   - Ping endpoint (`GET /api/ping`), pair endpoint (`POST /api/pair`), WebSocket handling (`WS /api/ws`), task hydration, mutation relay, and PWA static asset serving.
2. **Phase 2: Server CLI with npx Readiness (`subissues/punt-server-cli.md`)**
   - `package.json` with `"bin": { "punt": "./dist/server/cli.js" }`, `"files": ["dist/server", "dist/web"]`, and `"prepare": "npm run build"`.
   - `yargs`-based command-line runner (`punt serve [--code <code>] [--open]`, `punt status`, `punt tasks list|add|done|punt`) with stdin pipe support for task creation.
3. **Phase 3: Android Client Integration (`subissues/punt-server-android-sync.md`)**
   - 4-digit code generator (15m validity), direct ping of last known server, manual-only "Scan for Server" button with fast abort on match, local Wi-Fi IP display, WebSocket connection, and full task dump pushes.
4. **Phase 4: Web Client PWA with TypeScript & lit-html (`subissues/punt-server-web-client.md`)**
   - Functional `lit-html` templates (no custom elements), `esbuild` build to `dist/web/`, desktop PWA installability, live WebSocket task mirroring from server in-memory state, and fail-fast task mutations (standard browser interactions, no custom keybindings).
