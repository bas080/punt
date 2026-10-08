# AGENTS.md

- **Overview**: Punt is a Kotlin Android task and reminder application that monitors incoming device notifications and matches them against active user reminders.
- **Build & Test Commands**: Use `./gradlew` for building, linting, and testing.
- **Minimal Text Interface Design**: Maintain a clean, high-contrast typography-driven UI relying on Android system colors (`?android:attr/colorBackground`, `?android:attr/textColorPrimary`) without heavy borders or graphic cards.
- **Task Tracking**: Use `did` (see `did help`). User prompts should result in issues tracked by `did` being created or mutated. Only start implementing `did` issues when the user explicitly requests implementation. Use `did status` / `did show` for context, create tasks in `did` with `did add`, and mark tasks complete using `did done` after implementation and tests pass. An issue in `refine` should have questions for the developer (`@bas080`). If it does not have questions or if there are no further questions, move it to `implement`.
- **Commit Messages**: Write standard Git commit messages using a short, imperative subject line (50 characters max, e.g. "Add feature" rather than "Adds feature") with no conversational intros, followed by a blank line and a concise body.
- **Testing**: Any change to application specifications or feature behavior requires adding or updating relevant unit tests.
