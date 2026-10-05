# EXPERIMENTS.md

Punt uses structured application logging via `AppLogger` and `ExperimentTracker` to conduct and record non-invasive experiments on user devices. These experiment logs help evaluate application performance, test feature assumptions, and guide UX improvements when user feedback or crash reports are submitted.

---

## 1. Privacy Guarantees

User privacy is strictly protected:
- **Zero Sensitive Data Logging**: Reminder text, notification content, search query strings, and custom snooze text inputs are **never** logged.
- **Categorical & Aggregate Metrics Only**: Only anonymous category identifiers (e.g. channel names, tier numbers, state flags) and numerical counts/durations are logged.

---

## 2. Sample Threshold Requirements

To prevent misleading conclusions drawn from isolated single events, every experiment aggregates data points until a **minimum sample threshold** is reached. Summary statistics are compiled and recorded only when the threshold is satisfied.

---

## 3. Active Experiments

### 1. Frame Drop / Jank Tracking (`Experiment:FrameDrop`)
- **Objective**: Measure UI rendering smoothness and identify jank during list scrolling and interactions.
- **Threshold**: **100 frames** (measured via `Window.OnFrameMetricsAvailableListener` on Android N+).
- **Log Format**: `Frame metrics summary over 100 frames: X dropped frame(s) (Y%), max frame duration: Zms`

### 2. Creation Channels (`Experiment:CreationChannel`)
- **Objective**: Understand which entry points users prefer for adding reminders.
- **Threshold**: **10 creation events**.
- **Categories**: `app_input`, `notification_reply`, `text_selection`, `create_intent`, `pick_notification`.
- **Log Format**: `Creation channels summary over 10 items (app_input: A, notification_reply: B, ...)`

### 3. Matcher Tier Performance (`Experiment:MatcherTier`)
- **Objective**: Assess the effectiveness of the 4-tier search matching algorithm in `ReminderMatcher`.
- **Threshold**: **10 match attempts**.
- **Categories**: `tier_1_and_word`, `tier_2_and_substring`, `tier_3_or_word`, `tier_4_or_substring`, `no_match`.
- **Log Format**: `Matcher tiers summary over 10 evaluations (tier_1_and_word: A, tier_2_and_substring: B, ...)`

### 4. Snooze / Punt Choice Patterns (`Experiment:SnoozeChoice`)
- **Objective**: Determine whether users prefer preset snooze durations or custom relative/absolute inputs, and track custom duration parsing success rates.
- **Threshold**: **10 snooze actions**.
- **Categories**: `preset`, `custom_valid`, `custom_invalid`.
- **Log Format**: `Snooze choices summary over 10 actions (preset: A, custom_valid: B, custom_invalid: C)`

### 5. Search & Filter Engagement (`Experiment:SearchAndFilter`)
- **Objective**: Analyze how users navigate and filter their tasks (state filters vs multi-tag filtering) and measure zero-result query frequency.
- **Threshold**: **10 query interactions**.
- **Metrics Tracked**: State filter (`ALL`, `ACTIVE`, `SNOOZED`), tag filter count, token count, result indicator (`has_results` vs `zero_results`).
- **Log Format**: `Search/filter summary over 10 queries (state_ALL,tags_1,tokens_2,has_results: A; ...)`

### 6. Task Lifecycle (`Experiment:TaskLifecycle`)
- **Objective**: Monitor task completion dynamics, undo frequency, deletion rates, and unpunting behavior.
- **Threshold**: **10 lifecycle actions**.
- **Categories**: `marked_done`, `mark_done_undone`, `deleted`, `unpunted`.
- **Log Format**: `Task lifecycle summary over 10 actions (marked_done: A, mark_done_undone: B, ...)`
