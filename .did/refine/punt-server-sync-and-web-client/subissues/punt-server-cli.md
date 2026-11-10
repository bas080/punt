Build Punt server command-line interface (CLI) using yargs

Implement a companion command-line interface (`punt`) in Node.js and TypeScript using `yargs`.
The CLI has two primary roles:
1. Spawning and running the in-memory Punt server in the foreground (`punt serve`).
2. Interacting with the running server as an HTTP/WebSocket client to view and mutate task data currently mirrored from the Android phone.

### Design Principles
- **Simple, Non-REPL CLI**: No interactive shell or REPL. Standard command execution.
- **Foreground Server Execution**: Running `punt serve` starts the HTTP/WebSocket server in the foreground on default port 7868, accepting `--code <4-digit-code>` and printing clear clickable URLs.
- **Environment Variable & Flag Support via yargs**:
  - `yargs` configures `.env('PUNT')` so environment variable `PUNT_PORT` automatically maps to the `--port` option (with fallback to default port `7868`).
  - Command-line arguments (`--port <port>`) override the environment variable.
- **Auto-Open Browser**: `punt serve --open` automatically launches the default web browser to the local server URL (`http://localhost:7868`).
- **Unix Stdin / Pipe Support**: `punt tasks add` supports reading input from command arguments or piped `stdin` (e.g. `echo "Buy milk #groceries" | punt tasks add`), enabling easy shell scripting and terminal aliases.
- **CLI Communicates via Server**: When executing task commands (`tasks list`, `tasks add`, etc.), the CLI connects to the running server, which relays them to the Android phone.
- **No Local Persistence**: The CLI does not store tasks locally; it queries or mutates through the server.

### Commands
- `punt serve [--port <port>] [--host <host>] [--code <code>] [--open]`: Spawns the in-memory HTTP/WebSocket server on default port 7868 (or `PUNT_PORT` / `--port`) in the foreground. Accepts the 4-digit phone pairing code (or prompts if omitted). Automatically opens the browser if `--open` is provided.
- `punt status`: Connects to running server and reports server uptime, whether the Android phone is currently connected via WebSocket, and task counts.
- `punt tasks list`: Retrieves and prints the current in-memory task list mirrored from the phone.
- `punt tasks add [text]`: Dispatches task creation to the server from argument or piped `stdin`, which propagates to the Android phone (fails if phone is disconnected).
- `punt tasks done <id/text>`: Marks a task done on the phone via the server.
- `punt tasks punt <id/text> <duration>`: Punts a task on the phone via the server.
