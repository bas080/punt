Build Punt backend synchronization server in Node.js and TypeScript

Implement the Punt backend server in Node.js and TypeScript over local network HTTP to act as a local network mirror and mutation relay for the Android phone.

### Architectural Model
- **Phone as Source of Truth**: The task document on the Android phone is the sole source of truth.
- **Server as Mirror**: The server stores a cached copy of the phone's tasks to serve to the web client and CLI.
- **Mutation Relay**: When a client (web or CLI) requests a task mutation, the server relays this mutation to the phone. If the phone does not acknowledge or cannot be reached, the mutation fails and is rejected.
- **No CalDAV/iCalendar or Multi-Master JSON Deltas**: Operations are direct document updates / task commands mirrored with the phone.
- **Local Network Scope**: Strictly operates over the local network (no cloud/internet fallback).

### Technical Specifications
- **Runtime & Language**: Node.js and TypeScript.
- **Endpoints**:
  - `POST /api/pair`: Accepts `{ code: string, client_name: string }`. Validates single-use code, burns it, and issues session token.
  - `POST /api/sync/phone`: Authenticated endpoint for the phone to push full document updates and listen for/pull queued mutation requests.
  - `GET /api/tasks`: Returns current mirrored tasks for web/CLI.
  - `POST /api/tasks/mutate`: Dispatches a mutation command (create, edit, done, punt) to the phone and waits for acknowledgment.

## Questions for @bas080
1. Should the phone maintain an active long-poll / WebSocket connection to the server while on local Wi-Fi, or should the server invoke an Android notification / HTTP call to the phone?
2. How long should single-use pairing codes remain valid before expiring (e.g. 10 minutes, 15 minutes)?
