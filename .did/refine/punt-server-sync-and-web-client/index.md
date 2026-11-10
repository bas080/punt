Connect Punt Android client to Punt backend server with offline-capable web client

Enable phone-to-browser task access and management over the local network (HTTP) by connecting the Punt Android client to a backend Punt server and CLI implemented in Node.js and TypeScript.

### Single Source of Truth Architecture
- **Phone as Source of Truth**: The task document on the Android phone (`punt_tasks_backup.md` / `reminders_prefs`) is the sole authoritative source of truth.
- **Server and Web as Mirrors**: The backend server, CLI, and web interface act as mirrors of the phone's document.
- **Synchronous Mutation Propagation**: Any mutation initiated from the web interface or CLI must propagate to the Android phone. If propagating the change to the phone fails (e.g. phone is disconnected), the mutation operation fails.
- **No Complex Multi-Master Deltas or CalDAV**: No CalDAV/iCalendar or complex multi-master JSON delta sync protocols.
- **Strictly Local Network**: Synchronization operates strictly over the local network. If local network connection or discovery fails, there is no remote/cloud fallback (either local or nothing).
- **Authentication & Pairing**: Established once via the server endpoint URL (`http://<host>:<port>`) and a single-use setup code displayed in server stdout and the web client (no QR codes).

## Recommended Implementation Order (Subissues)
1. **Phase 1: Backend Server (`subissues/punt-server-sync-backend.md`)**
   - Node.js & TypeScript HTTP server, single-use pairing code generation and token validation (`POST /api/pair`), phone task document mirroring, and mutation relay.
   - Rationale: Defines the relay and mirroring endpoints between phone, web, and CLI.
2. **Phase 2: Server CLI (`subissues/punt-server-cli.md`)**
   - Command-line runner (`punt-server start`, `punt-server pair`, `punt-server clients`, `punt-server status`) printing single-use codes to stdout.
   - Rationale: Enables running and testing the server easily from developer terminals and generating pairing codes for client testing.
3. **Phase 3: Android Client Integration (`subissues/punt-server-android-sync.md`)**
   - Add Server Sync setup dialog in Android app (Host/Port and code entry), background sync worker, and mirror state listener/updater.
   - Rationale: Connects the authoritative source of truth to the server relay.
4. **Phase 4: Web Client PWA (`subissues/punt-server-web-client.md`)**
   - Lightweight browser client served by the server, mirroring tasks from the server and propagating mutations to the phone via server relay.
   - Rationale: Delivers the web viewing and task management UI on top of the established phone mirror connection.
