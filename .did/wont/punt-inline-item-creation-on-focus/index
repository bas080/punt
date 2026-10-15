Inline task creation on focus with cascading empty boxes (Declined / Won't Do)

## Overview
Attempted refinement of an inline task creation mechanism where focusing a top input box dynamically spawns an empty creation box underneath it without an explicit 'Add' button.

## Design & Implementation Difficulties
1. **Conflict with Search & Filtering**:
   - The top input card in Punt currently serves dual roles: live debounced search filtering across active reminders and single-line/multi-line new task entry.
   - Using the top box purely for auto-creating items on focus destroys the seamless live search experience where users type to filter existing reminders in real-time.

2. **UX & Input State Ambiguities**:
   - Spawning empty boxes on focus creates visual clutter and focus management complexity (e.g. keyboard focus traversal, accidental creation of empty rows, managing IME actions, and cleanup of unfocused blank rows vs retaining a single persistent creation row).
   - Dynamic insertion of temporary empty items into a `RecyclerView` while handling adapter diffing, scroll position preservation, and persistent storage synchronization introduces edge cases and potential race conditions.

3. **Conclusion**:
   - Retaining the existing explicit search/add top bar (or clear separation between search filtering and explicit task submission) provides a predictable, clean text-driven interface without unexpected structural mutations on focus.
