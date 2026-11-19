Bring the web interface to feature parity with the Android items view

Port the Android `MainActivity` items-view feature set to the web client (`src/web/main.ts`) and extend the phone-side sync mutation set in `ServerSyncManager` so web-driven actions can be applied on the phone. Scope confirmed by developer (`@bas080`): implement all parity items; keep the web menu minimal (no About dialog).

### Task Items
- Inline task editing with debounced persistence keeping done/punt state.
- Undo done for completed tasks; done tasks hidden by default, revealed when the query contains `#done`.
- Recurring tasks punting by their recurrence interval when completed (instead of being marked done).
- Configurable punt dialog: presets (15m, 1h, 4h, 24h, 1w, 1M) plus custom duration input; Unpunt on punted tasks.
- Humanized punt labels ("punted until today at HH:mm", "tomorrow at ...", weekday, date).
- Punt All for currently displayed active tasks with a chosen duration.
- Export Matching to a Markdown checklist download, with optional punt info.
- Import Markdown checklists/text to create reminders.
- Tiered, tag-aware search matching (AND word / AND substring / OR word / OR substring) replacing plain substring search.
- Focus Profiles: create/edit/delete profiles (`Name #tag...`), activate multiple, union matching, combined `all | focus | search` pipeline, "Focus: names" input hint. Profiles persist per browser (localStorage).
- Minimal overflow menu: Focus Profiles, Punt All, Export Matching, Import.
- Empty states with instruction text and Clear Filter / Clear Focus actions.
- Search clear button, persisted search query and collapsed states.
- Section ordering: active tasks newest first, punted sorted by punt time ascending.

### Subissues
- `.web-parity-ui.md` — web client implementation.
- `.phone-sync-mutations.md` — Kotlin `ServerSyncManager` mutation additions.

### Status
- 2026-11-19: Scope approved by developer; moved to `implement`.
- 2026-11-19: Implemented. Web client (`src/web/main.ts`, `src/web/style.css`) gains all parity items; `ServerSyncManager.applyAction` gains `edit`, `unpunt`, `undone`, and recurrence-aware `done`. Node test suite updated and passing (66 tests); `npm run build` passing. Kotlin changes are compile-verified only in CI (no Android SDK in local sandbox); `ServerSyncManagerTest` has no Room-backed `applyAction` coverage to extend.
- Known limitation: importing reminders with `<time>` punt tags creates the tasks but cannot set their `snoozeUntil` (the `create` mutation carries text only); tracked in the phone-sync subissue.
- Import was kept alongside Export Matching in the minimal menu as the data-ingest counterpart, per "Implement all" scope confirmation.
