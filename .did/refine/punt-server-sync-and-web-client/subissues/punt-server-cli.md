Build Punt server command-line interface (CLI)

Implement a companion command-line interface (`punt-server`) in Node.js and TypeScript to operate the server, issue pairing codes, and manage/inspect tasks mirrored from the phone.

### CLI Commands
- `punt-server start [--port <port>] [--host <host>]`: Start the HTTP mirror server and static web host. Prints the local URL and initial single-use pairing code to stdout.
- `punt-server pair [--generate]`: Generates a new single-use pairing code on demand and outputs it to stdout.
- `punt-server clients`: Lists paired client devices, their last sync time, and client IDs.
- `punt-server revoke <client_id>`: Revokes access for a paired device token.
- `punt-server tasks list`: Displays currently mirrored tasks from the phone.
- `punt-server tasks add <text>`: Submits a new task through the server to the phone (fails if phone is disconnected).
- `punt-server status`: Displays server health, connection status to the phone, and active pairing codes.

## Questions for @bas080
1. Should `punt-server` run interactively (with an active console log stream) or support a daemonized/background mode?
2. Which CLI parsing library is preferred (e.g. `commander`, `yargs`, or zero-dependency native `node:util.parseArgs`)?
