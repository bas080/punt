Build lightweight Progressive Web App (PWA) client for Punt server using lit-html

Develop a lightweight, installable Progressive Web App (PWA) web interface served directly by the Punt backend server over local network HTTP.
The web client acts as an interactive mirror of the user's Android phone.
Task mutations (creating, editing, punting, marking done) initiated in the web interface are sent over WebSockets to the server to be applied to the phone. If the phone is offline or unreachable on the local network, the mutation fails immediately and the user is informed.

### Technical Stack & Architecture
- **Templating**: `lit-html` for high-performance, lightweight HTML rendering.
- **Frontend Core**: Vanilla TypeScript, HTML, and CSS (no heavyweight frameworks or bundler overhead).
- **Progressive Web App (PWA)**: Includes `manifest.json` and a minimal service worker, allowing users to install Punt as a standalone desktop windowed application from their browser on desktop OSs.
- **Standard Interactions (No Custom Keybindings)**: Uses standard browser form inputs and explicit click/tap controls without custom global single-key shortcuts.
- **Theme Matching Android App**: The web client visual styles match the Punt Android application theme precisely:
  - Dark background (`@color/bg_dark`, `#121212`)
  - Surface cards (`@color/bg_surface`, `#1E1E1E` with 4dp rounded corners)
  - Card stroke outlines and subtle dividers (`@color/divider_color`, `#2C2C2E`)
  - High-contrast typography (`@color/text_primary`, `#FFFFFF` and `@color/text_secondary`, `#8E8E93`)
  - Accent color highlights (`@color/accent`, `#30D158`)
- **Browser Origin Authentication**: Because the web client is served directly from the Punt server's origin, it automatically inherits authorized access to view tasks and dispatch mutations without requiring a secondary pairing flow in the browser.
- **WebSocket Live Updates**: Maintains a WebSocket connection (`WS /api/ws`) to receive real-time full list updates whenever the Android phone pushes state changes.
- **Fail-Fast UI**: Adding, editing, punting, or marking done sends a mutation message. If the phone is not connected or does not respond within a short timeout, the action is rejected in the UI with a notification that the phone is unreachable.
- **UI Design**: Adheres to Punt's minimal text interface guidelines (`bg_card`, high-contrast typography, collapsible active/punted sections, and search filter input).

### Automated Tests to Write
1. **lit-html Rendering & Theme Tests**:
   - `testRendersActiveAndPuntedSections`: Provides mock task array; verifies `lit-html` renders separate Active and Punted card containers.
   - `testAppliesDarkThemeProperties`: Verifies CSS variables match `#121212` background and `#1E1E1E` surface colors.
   - `testFilterInputNarrowsDisplayedTasks`: Enters search text into query input; verifies displayed DOM elements filter dynamically.
2. **WebSocket Live Updates Tests**:
   - `testWebSocketReceivesFullListUpdate`: Emits updated task list from simulated WebSocket; verifies DOM immediately re-renders to reflect changes.
   - `testDisconnectedStateBanner`: Simulates WebSocket disconnect; verifies UI displays *"Phone disconnected / Server offline"* banner.
3. **Fail-Fast Mutation Feedback Tests**:
   - `testMutationRejectedDisplaysError`: Submits task creation when phone is disconnected; verifies error notification is displayed in UI and optimistic item is removed.
4. **PWA Manifest & Service Worker Tests**:
   - `testManifestContainsRequiredProperties`: Validates `name`, `short_name`, `start_url`, `display: standalone`, and icons array.
   - `testServiceWorkerRegistersSuccessfully`: Confirms service worker file parses without errors and registers with browser cache.
