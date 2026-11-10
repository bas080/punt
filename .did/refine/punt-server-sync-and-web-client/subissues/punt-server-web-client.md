Build lightweight web client for Punt server using lit-html

Develop a lightweight, browser-based web interface served directly by the Punt backend server over local network HTTP.
The web client acts as an interactive mirror of the user's Android phone.
Task mutations (creating, editing, punting, marking done) initiated in the web interface are sent to the server to be applied to the phone. If the phone is offline or unreachable on the local network, the mutation fails immediately and the user is informed.

### Technical Stack & Architecture
- **Templating**: `lit-html` for high-performance, lightweight HTML rendering.
- **Frontend Core**: Vanilla TypeScript, HTML, and CSS (no heavyweight frameworks or bundler overhead).
- **Browser Origin Authentication**: Because the web client is served directly from the Punt server's origin, it automatically inherits authorized access to view tasks and dispatch mutations without requiring a secondary pairing flow in the browser.
- **No Local Persistence**: The web client does not store tasks persistently in IndexedDB or localStorage; it loads the current state directly from the server's in-memory mirror and receives updates as the phone updates the server.
- **Fail-Fast UI**: Adding, editing, punting, or marking done sends a request to `/api/tasks/mutate`. If the phone is not connected or does not respond within a short timeout, the action is rejected in the UI with a notification that the phone is unreachable.
- **UI Design**: Adheres to Punt's minimal text interface guidelines (`bg_card`, high-contrast typography, collapsible active/punted sections, and search filter input).

## Questions for @bas080
1. Should the web client maintain a real-time WebSocket connection to the server for live DOM re-rendering with `lit-html` when the phone updates, or use Server-Sent Events (SSE)?
