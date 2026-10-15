Replace colorful emojis with text emojis or minimal text symbols (#refine)

Ensure all user-facing strings, logs, notifications, and documentation replace any colorful system/graphic Unicode emojis (e.g. 😀, 🚀, 👍) with plain text emoticons (e.g. `:-)`, `:-P`, `<3`, `(o_O)`) or minimal abstract text symbols (`•`, `→`, `✓`) to adhere to Punt's Minimal Text Interface Design principles.

## Objectives & Guidelines
- Audit app string resources (`strings.xml`), Toast feedback messages, notifications, and UI elements.
- Ensure zero multi-color bitmap/graphic Unicode emojis are rendered in the application UI.
- Use plain text ASCII emoticons or clean vector icons where visual cues are needed.

## Questions for @bas080
1. Should text emoticons be standard ASCII (e.g. `:-)`, `;-)`, `:-D`, `O_o`) or Kaomoji (e.g. `(^_^)`, `(o_O)`)?
2. Are there any specific string resources or notification templates where text emoticons should be enabled by default?
