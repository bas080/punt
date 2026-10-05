---
title: Implement full emulator-based screenshot generation suite
status: open
priority: 2
issue-type: task
created-at: "2026-10-05T13:07:42Z"
blocks:
  - punt-research-test-arg-20594d39
  - punt-research-screenshot-capture-b1469c85
  - punt-research-activityscenario-89b32b97
  - punt-research-adb-pull-cfa6629c
---

Implement end-to-end `ScreenshotGeneratorAndroidTest` combining verified argument gating, storage, adb pull, and lifecycle isolation.

**Blockers**:
- Blocked by `punt-research-test-arg-20594d39` (Test argument forwarding)
- Blocked by `punt-research-screenshot-capture-b1469c85` (Device screenshot storage)
- Blocked by `punt-research-activityscenario-89b32b97` (ActivityScenario lifecycle isolation)
- Blocked by `punt-research-adb-pull-cfa6629c` (ADB pull workflow in CI)
