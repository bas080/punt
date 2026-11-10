Build Punt server command-line interface (CLI) using yargs

Implement a companion command-line interface (`punt-server`) in Node.js and TypeScript using `yargs`.
The CLI has two primary roles:
1. Spawning and running the in-memory Punt server (`punt-server start`).
2. Interacting with the running server as an HTTP client to view and mutate task data currently mirrored from the Android phone.

### Design Principles
- **Simple, Non-REPL CLI**: No interactive shell or REPL. Standard command execution.
- **CLI Communicates via Server**: When executing task commands (`tasks list`, `tasks add`, etc.), the CLI sends standard HTTP requests to the running server, which relays them to the Android phone.
- **No Local Persistence**: The CLI does not store tasks locally; it queries or mutates through the server.

### Commands
- `punt-server start [--port <port>] [--host <host>]`: Spawns the in-memory HTTP server and static web host. Prints the local endpoint URL and active single-use pairing code to stdout.
- `punt-server pair [--generate]`: Generates a new single-use pairing code on demand on the running server.
- `punt-server status`: Connects to running server and reports server uptime, whether the Android phone is currently connected, and task counts.
- `punt-server tasks list`: Retrieves and prints the current in-memory task list mirrored from the phone.
- `punt-server tasks add <text>`: Dispatches task creation to the server, which propagates to the Android phone (fails if phone is disconnected).
- `punt-server tasks done <id/text>`: Marks a task done on the phone via the server.
- `punt-server tasks punt <id/text> <duration>`: Punts a task on the phone via the server.

## Questions for @bas080
1. Should `punt-server start` run in the foreground by default with standard server access logs, or should it offer a daemon flag?
