Build Punt server command-line interface (CLI) using yargs with npx readiness

Implement a companion command-line interface (`punt`) in Node.js and TypeScript using `yargs`.
The CLI has two primary roles:
1. Spawning and running the in-memory Punt server in the foreground (`punt serve`).
2. Interacting with the running server as an HTTP/WebSocket client to view and mutate task data currently mirrored from the Android phone.

### npx Distribution & Packaging (`package.json`)
To enable users to run `npx punt serve --code <code> --open` with zero installation steps and have the web artifacts served immediately:
- The package configuration specifies:
  ```json
  {
    "name": "punt",
    "bin": {
      "punt": "./dist/server/cli.js"
    },
    "files": [
      "dist/server",
      "dist/web"
    ],
    "scripts": {
      "build:web": "esbuild src/web/main.ts --bundle --minify --outdir=dist/web && cp src/web/index.html src/web/style.css src/web/manifest.json dist/web/",
      "build:server": "esbuild src/server/cli.ts --bundle --platform=node --target=node18 --outfile=dist/server/cli.js",
      "build": "npm run build:web && npm run build:server",
      "prepare": "npm run build"
    }
  }
  ```
- **Pre-Built Web Bundling**: `"files": ["dist/server", "dist/web"]` ensures npm packages and tarballs include the compiled web UI artifacts.
- **Automated Prepack**: `"prepare": "npm run build"` guarantees that both the server and the web client are built before publishing.
- **Executable Shebang**: The entry script begins with `#!/usr/bin/env node` and has executable file permissions.
- **Co-located Artifact Resolution**: The server resolves the web assets relative to its own script location:
  ```typescript
  const webArtifactPath = path.resolve(__dirname, '../web');
  app.use(express.static(webArtifactPath));
  ```
  This ensures static web assets are served reliably when invoked via `npx punt`, `npm install -g punt`, or local repository runs.

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
- `punt serve [--port <port>] [--host <host>] [--code <code>] [--open]`: Spawns the in-memory HTTP/WebSocket server on default port 7868 (or `PUNT_PORT` / `--port`) in the foreground, serving the compiled static `dist/web/` PWA artifacts. Accepts the 4-digit phone pairing code (or prompts if omitted). Automatically opens the browser if `--open` is provided.
- `punt status`: Connects to running server and reports server uptime, whether the Android phone is currently connected via WebSocket, and task counts.
- `punt tasks list`: Retrieves and prints the current in-memory task list mirrored from the phone.
- `punt tasks add [text]`: Dispatches task creation to the server from argument or piped `stdin`, which propagates to the Android phone (fails if phone is disconnected).
- `punt tasks done <id/text>`: Marks a task done on the phone via the server.
- `punt tasks punt <id/text> <duration>`: Punts a task on the phone via the server.

### Automated Tests to Write
1. **Option & Environment Parsing Tests**:
   - `testDefaultPortIs7868`: Verifies default port evaluates to 7868 when neither flag nor env var is set.
   - `testPuntPortEnvVar`: Sets `PUNT_PORT=9000` and verifies CLI parses port as 9000.
   - `testPortFlagOverridesEnvVar`: Sets `PUNT_PORT=9000` with `--port 8080` and verifies port is 8080.
   - `testCodeArgumentParsing`: Verifies `--code 1234` correctly populates the pairing parameter.
2. **Package & Path Resolution Tests**:
   - `testWebArtifactsDirectoryExists`: Asserts that `dist/web/index.html` exists alongside `dist/server/cli.js`.
   - `testStaticPathResolution`: Verifies server resolves static directory to existing `../web` path without throwing.
3. **Stdin & Pipe Input Tests**:
   - `testTasksAddFromArgument`: Runs `punt tasks add "Test reminder #tag"` and verifies payload dispatched to server.
   - `testTasksAddFromStdinPipe`: Pipes `"Piped task text"` via simulated stdin stream and verifies identical task dispatch.
   - `testTasksAddFailsGracefullyWhenEmpty`: Verifies error exit code (1) and descriptive error message if text is empty.
4. **Server Offline Error Handling Tests**:
   - `testTasksListWhenServerNotRunning`: Verifies clear error output (*"Unable to connect to Punt server at localhost:7868. Is 'punt serve' running?"*) without unhandled stack traces.
   - `testTasksAddWhenPhoneDisconnected`: Verifies CLI receives and prints the server error (*"Failed to add task: Phone is unreachable"*).
