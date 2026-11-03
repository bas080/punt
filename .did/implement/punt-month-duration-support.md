Support `M` for Month Duration in Recurrence and Snooze Options (#refine)

Add support for `M` duration unit across recurring task tags (`#every 1M`, `#repeat 1M`) and snooze duration expressions/presets (e.g. `1M`, `3M`).

## Details & Requirements
- **Duration Definition**: 1 Month is defined as 30 days (`30d` = `30 * 24 * 60 * 60 * 1000` ms = 2,592,000,000 ms) as approved by developer.
- **Unit Disambiguation**: Lowercase `1m` represents 1 minute, while uppercase `1M` represents 1 month.
- **Snooze Choice Options**: Preset snooze options list should include Month duration options (e.g. `1M`).
- **Parsing**: Extend `parseRecurrenceDuration` and custom snooze duration parser to recognize `M` for months.
