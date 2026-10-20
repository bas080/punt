Focus Profiles / Saved Search Queries to Focus Attention

A feature allowing users to define, save, and select named profiles ("Focus" profiles, e.g. for work, fitness, or health) that store saved search/filter queries to quickly narrow down visible tasks to specific areas of interest.

## Specifications & Requirements

1. **UI Access**:
   - Access Focus Profiles via a button in the floating FAB menu that opens a dedicated "Focus Profiles" dialog.

2. **Dialog Functionality**:
   - Lists saved Focus Profiles and includes a default "All / No Profile" option to clear active profile filtering.
   - Allows creating new Focus Profiles pre-filled from the currently active search box text. If an existing profile is already selected when creating a new profile, the query is pre-filled as `profile query + search query`.
   - Unique profile names: Saving a profile with an existing profile name updates/overwrites that profile.
   - Allows selecting, editing, or deleting existing Focus Profiles.

3. **Filter Application & Composition**:
   - Focus Profile queries apply independently alongside the active search text query (`profile query + search query`).
   - `#done` items are hidden unless explicitly included in the focus profile query (behaving like standard search queries).
   - State filters (All, Active, Punted) remain independent of Focus Profile selection.

4. **Active Profile Indicator**:
   - When a Focus Profile is active, the placeholder text of `search_reminder_input` dynamically updates to indicate the active profile (e.g. `Focus: [Profile Name]`).
   - When no tasks match the active filters, the empty state instruction view (`layout_empty_reminders`) displays a clear focus profile action link (`btn_empty_clear_focus`) alongside the clear filter button.

5. **Persistence**:
   - Selected Focus Profile selection persists across app restarts in `reminders_prefs`.
