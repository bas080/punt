# AGENTS.md

- **Overview**: Punt is a Kotlin Android task and reminder application that monitors incoming device notifications and matches them against active user reminders.
- **Build & Test Commands**: Use `./gradlew` for building, linting, and testing.
- **Minimal Text Interface Design**: Maintain a clean, high-contrast typography-driven UI relying on Android system colors (`?android:attr/colorBackground`, `?android:attr/textColorPrimary`) without heavy borders or graphic cards.
- **Task Tracking**: Use `dots` (see `dot --help`). User prompts should result in issues tracked by `dots` being created or mutated. Only start implementing `dots` issues when the user explicitly requests implementation. The only way to start work is by picking up an existing `dot` issue (`dot ready` / `dot on <id>`). Use `dot show` / `dot tree` for context, create tasks in `dots` with dependencies, and mark tasks complete only after implementation and tests pass.
- **Commit Messages**: Write standard Git commit messages using a short, imperative subject line (50 characters max, e.g. "Add feature" rather than "Adds feature") with no conversational intros, followed by a blank line and a concise body.
- **Testing**: Any change to application specifications or feature behavior requires adding or updating relevant unit tests.
