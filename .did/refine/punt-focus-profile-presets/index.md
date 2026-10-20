Focus Profiles / Saved Search Queries to Focus Attention (#refine)

A feature allowing users to define, save, and select named profiles ("Focus" profiles, e.g. for work, fitness, or health) that store saved search/filter queries to quickly narrow down visible tasks to specific areas of interest.

## Proposed Concept
- **Focus Profiles**: Named profiles that encapsulate saved search queries (e.g. "Work" -> `#work`, "Fitness" -> `#fitness`, "Health" -> `#health`).
- **Profile Selection**: Picking a saved Focus profile applies its corresponding search query to filter the task list. Selecting "All" / default clears the active profile filter.
- **Save & Manage**: Users can define and save custom search queries into named Focus profiles and select them later from the UI.

## Questions for @bas080
1. Where should saved Focus profiles be selected and managed in the UI: inside the filter dialog (`btn_tags_filter`), as quick-select pills in the FAB menu, or directly within the top search bar?
2. Should saved Focus profiles persist across app restarts as the default active view if one was selected before closing?
