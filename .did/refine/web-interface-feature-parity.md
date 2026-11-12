Here’s a parity checklist based on the Android task workflows in MainActivity.kt and the current web actions in main.ts. No implementation has started.

Already in the web interface

Add a task by typing and pressing Enter.
Search task text and collapse Active/Punted sections.
Mark a task done.
Punt a task for a fixed 1 hour.
Share an individual task and delete it.
Parity gaps to review

Edit task text inline, persisting changes and keeping its state.
Undo Done for recently completed tasks; completed tasks should remain hidden by default but be findable with #done.
Recurring tasks: completing one should punt it by its recurrence interval rather than mark it done.
Configurable Punt: Android’s presets, custom durations/date-times, weekday choices, and recent per-task/global choices. Web currently only offers 1 hour.
Unpunt a punted task and return it to Active.
Punt All currently displayed active tasks, with a chosen duration.
Share Matching tasks, respecting the current search/focus filter.
Focus Profiles: create/edit/delete profiles, activate multiple profiles, and combine them with search using Android’s filter behavior.
Search parity: preserve Android’s tiered/tag-aware matching rather than relying only on web’s basic lowercase substring match.
Consistent state across actions: display punt timestamps, done/recurrence state, and reflect mutations after sync.

