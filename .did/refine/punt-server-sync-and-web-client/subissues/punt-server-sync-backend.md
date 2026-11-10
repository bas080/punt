Build Punt backend synchronization server in Node.js and TypeScript

Implement a lightweight Punt backend server in Node.js and TypeScript over local network HTTP.
The server functions purely as an in-memory relay and temporary cache between the Android phone, the web client, and the CLI.

### Architectural Model
- **Pure In-Memory State (No Database)**: The server does NOT persist data to a database. All task data is stored in memory in Node.js while the server is running. When the Android phone connects, it hydrates the server's memory with the current state.
- **Phone as Sole Source of Truth**: The phone's local storage (`reminders_prefs` / `punt_tasks_backup.md`) is the only permanent persistence.
- **Full Task List Dumps (No Deltas)**: The phone pushes its complete list of reminders to the server upon connecting and whenever a local change occurs while connected.
- **Data Flow**:
  `android -> server -> web/cli -> server -> android -> server`
  When web or CLI mutates a task, the command is forwarded through the server to the phone. Once the phone updates its state and confirms, the phone pushes the updated complete list back to the server, which broadcasts to connected web clients.
- **Local Network Scope**: Strictly local network HTTP; fails immediately if local network communication fails.

### Technical Specifications
- **Runtime**: Node.js & TypeScript.
- **Persistence**: None (in-memory state only).
- **Endpoints**:
  - `POST /api/pair`: Accepts `{ code: string, client_name: string }`. Validates single-use setup code, burns it, and issues session token for the phone.
  - `POST /api/sync/phone`: Authenticated endpoint for the phone to dump its complete task list and receive pending mutations.
  - `GET /api/tasks`: Returns current in-memory mirrored tasks to web clients and CLI commands.
  - `POST /api/tasks/mutate`: Receives mutation requests from web or CLI, forwards to the phone, and waits for phone acknowledgment. Fails if the phone is disconnected.

## Questions for @bas080
1. Should the connection between phone and server use WebSockets or Server-Sent Events (SSE) + HTTP POST for real-time mutation forwarding and list updates?
2. How long should the single-use pairing code remain active before expiring (e.g. 10 minutes)?
