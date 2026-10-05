# AGENTS.md

- **Overview**: Punt is a Kotlin Android task and reminder application that monitors incoming device notifications and matches them against active user reminders.
- **Build & Test Commands**: Use `./gradlew` for building, linting, and testing.
- **Minimal Text Interface Design**: Maintain a clean, high-contrast typography-driven UI relying on Android system colors (`?android:attr/colorBackground`, `?android:attr/textColorPrimary`) without heavy borders or graphic cards.
- **Task tracking**: Use dots as the persistent task tracker for this project. User prompts should result in issues tracked by dots to be mutated or created. The only way the AI should start work is by picking up an existing dot issue. Run `dot ready` and choose an unblocked task before starting. Use `dot show` and `dot tree` when you need context or dependencies. Run `dot --help` to see all available commands. When you discover new project work, create a task in dots and add the appropriate dependency rather than keeping it only in your notes or conversation. Keep task status up to date. Mark a task complete only after the implementation and relevant tests are finished.
- **Commit Messages**: Write standard Git commit messages using a short, imperative subject line (50 characters max, e.g. "Add feature" rather than "Adds feature") with no conversational intros, followed by a blank line and a concise body.
- **Testing**: Any change to application specifications or feature behavior requires adding or updating relevant unit tests.
