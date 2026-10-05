# EXPERIMENTS.md

Punt uses an on-device experiment framework via `ExperimentTracker` to continuously track and record non-invasive feature metrics on user devices. Experiment metrics are stored persistently in `SharedPreferences` (`experiment_prefs`) so they are never lost to log rotation and are always attached under `### Experiment Metrics` whenever user feedback or crash reports are submitted.

---

## 1. Privacy Guarantees

User privacy is strictly protected:
- **Zero Sensitive Data Logging**: Reminder text, notification content, search query strings, and custom snooze text inputs are **never** logged.
- **Categorical & Aggregate Metrics Only**: Only anonymous category identifiers (e.g. channel names, tier numbers, state flags) and numerical counts/durations are logged.

---

## 2. Continuous Tracking & Rolling Aggregation

Metrics are tracked continuously without client-side threshold gating. Each report submission contains the exact sample size (`n` or `total`) alongside rolling averages or cumulative counts, allowing statistical significance filtering to be performed on the analysis side.

---

## 3. Active Experiments

### 1. Reminder Edit Interval
- **Objective**: Measure user editing cadence and average time elapsed between consecutive reminder text updates.
- **Metrics**: Sample count (`n`), rolling average interval (`avg`), minimum interval (`min`), maximum interval (`max`).
- **Format**: `- **Reminder Edit Intervals**: n=X, avg Y.Ys, min Zs, max Ws`

### 2. Creation Channels
- **Objective**: Understand which entry points users prefer for adding reminders.
- **Categories**: `app_input`, `notification_reply`, `text_selection`, `create_intent`, `pick_notification`.
- **Format**: `- **Creation Channels**: total=X (app_input: A, notification_reply: B, ...)`

### 3. Matcher Tier Performance
- **Objective**: Assess the effectiveness of the 4-tier search matching algorithm in `ReminderMatcher`.
- **Categories**: `tier_1_and_word`, `tier_2_and_substring`, `tier_3_or_word`, `tier_4_or_substring`, `no_match`.
- **Format**: `- **Matcher Algorithm Tiers**: total=X (tier_1_and_word: A, tier_2_and_substring: B, ...)`

### 4. Snooze / Punt Choice Patterns
- **Objective**: Determine whether users prefer preset snooze durations or custom relative/absolute inputs, and track custom duration parsing success rates.
- **Categories**: `preset`, `custom_valid`, `custom_invalid`.
- **Format**: `- **Snooze Choice Patterns**: total=X (preset: A, custom_valid: B, custom_invalid: C)`

### 5. Search & Filter Engagement
- **Objective**: Analyze how users navigate and filter their tasks (state filters vs multi-tag filtering) and measure zero-result query frequency.
- **Metrics Tracked**: State filter (`ALL`, `ACTIVE`, `SNOOZED`), tag filter count, token count, result indicator (`has_results` vs `zero_results`).
- **Format**: `- **Search & Filter Usage**: total=X (state_ALL,tags_1,tokens_2,has_results: A; ...)`

### 6. Task Lifecycle
- **Objective**: Monitor task completion dynamics, undo frequency, deletion rates, and unpunting behavior.
- **Categories**: `marked_done`, `mark_done_undone`, `deleted`, `unpunted`.
- **Format**: `- **Task Lifecycle Events**: total=X (marked_done: A, mark_done_undone: B, ...)`
