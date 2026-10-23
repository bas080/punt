# Support for multiple every definitions (#refine)

Refine specification and behavior for tasks containing multiple `#every` or `#repeat` recurrence tags (e.g., `Workout #everyWed #everyFri`).

## Decisions & Behavior
1. **Earliest Duration Selection**: When a recurring task is marked done, the system evaluates all recurrence tags and punts the task into the future by the **earliest / shortest** calculated time offset among all specified recurrence tags.
   - *Example*: On Thursday, a task with `#everyWed #everyFri` will evaluate `#everyWed` (6 days away) and `#everyFri` (1 day away on Friday), selecting Friday as the earliest punt date.

2. **Subtle Inline Text Formatting**:
   - All `#every` and `#repeat` tags in active reminder cards are styled inline using `ForegroundColorSpan` with the subtle secondary text color (`@color/text_secondary`, `#8E8E93`).
   - Every recurrence match found via `RECURRENCE_REGEX.findAll(...)` is styled so that all multiple definitions are rendered in a clean, subtle way without background pills or borders.

3. **Combined Syntax Backlog**:
   - Combined tag syntax (e.g., `#every(12h, 2d)`) is tracked separately in `.did/refine/support-combined-syntax-for-recurrence-definitions.md`.
