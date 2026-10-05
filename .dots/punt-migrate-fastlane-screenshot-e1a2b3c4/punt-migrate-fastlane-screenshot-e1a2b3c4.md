---
title: Migrate Fastlane screenshot generation to Android emulator
status: open
priority: 2
issue-type: task
created-at: "2026-10-05T12:00:00Z"
blocks:
  - punt-implement-full-emulator-cb4310aa
---

# Overview

This task documents the architecture, workflow, and design considerations for capturing Fastlane store screenshots directly on an Android Emulator during instrumented UI test execution (`connectedCheck`).

---

## 1. Instrumented Test Argument Forwarding

To avoid running screenshot generation during routine benchmark runs, execution is gated behind an instrumentation test runner argument.

Passing the argument via Gradle command line:
```bash
./gradlew connectedCheck -Pandroid.testInstrumentationRunnerArguments.generate.screenshots=true
```

Reading the argument inside `androidTest` (`ScreenshotGeneratorAndroidTest.kt`) using `InstrumentationRegistry`:
```kotlin
import androidx.test.platform.app.InstrumentationRegistry

val args = InstrumentationRegistry.getArguments()
val shouldGenerate = args.getString("generate.screenshots") == "true"
if (!shouldGenerate) {
    println("Skipping screenshot generation because argument is not set.")
    return
}
```

---

## 2. Storage & ADB Pull Workflow

### A. Saving PNGs to App Storage
In Android instrumented tests (`androidTest`), saving screenshots to external app storage (`context.getExternalFilesDir("screenshots")`) requires no additional runtime storage permissions (`WRITE_EXTERNAL_STORAGE`):

```kotlin
val context = ApplicationProvider.getApplicationContext<Context>()
val baseDir = context.getExternalFilesDir("screenshots") ?: File(context.filesDir, "screenshots")
val phoneDir = File(baseDir, "phoneScreenshots")
val tabletDir = File(baseDir, "tenInchScreenshots")
```

### B. Pulling Screenshots in CI Workflow
In GitHub Actions CI (`.github/workflows/ci.yml`), after executing `connectedCheck` on `ReactiveCircus/android-emulator-runner@v2`, pull the PNG assets back to the repository workspace:

```bash
mkdir -p fastlane/metadata/android/en-US/images/phoneScreenshots
mkdir -p fastlane/metadata/android/en-US/images/tenInchScreenshots

adb pull /sdcard/Android/data/com.bas080.notificationreminders/files/screenshots/phoneScreenshots/. fastlane/metadata/android/en-US/images/phoneScreenshots/ || true
adb pull /sdcard/Android/data/com.bas080.notificationreminders/files/screenshots/tenInchScreenshots/. fastlane/metadata/android/en-US/images/tenInchScreenshots/ || true
```

---

## 3. Key Lifecycle Considerations

- Structure screenshot captures into separate `@Test` methods (`capture1Overview` .. `capture6KeyboardEditing`) so each capture runs in its own clean `ActivityScenario` lifecycle.
- Avoid calling `finish()` on dialog activities (such as `PickNotificationActivity`) before screenshot capture.
- Refrain from guarding activity states with `isFinishing`/`isDestroyed` checks so that any window rendering issue fails the screenshot test explicitly.

---

## 4. Task Breakdown & Research Dependency Graph

Implementation of the full emulator screenshot suite is blocked by preliminary research tasks to verify and prove each component step-by-step:

```
[1. Research test argument forwarding]  ──┐
                                          ├───> [4. Research ADB pull workflow in CI] ──┐
[2. Research screenshot capture & storage] ┘                                           │
                                                                                       ├───> [5. Implement full emulator suite] ───> [Parent task completion]
[3. Research ActivityScenario lifecycle isolation] ────────────────────────────────────┘
```

1. **`punt-research-test-arg-20594d39`**: Research & prove test argument forwarding (`generate.screenshots=true`).
2. **`punt-research-screenshot-capture-b1469c85`**: Research & prove bitmap capture and file saving to external storage in `androidTest`.
3. **`punt-research-activityscenario-89b32b97`**: Research & prove `ActivityScenario` lifecycle isolation across `@Test` methods.
4. **`punt-research-adb-pull-cfa6629c`**: Research & prove `adb pull` asset extraction in CI (Blocked by #1 & #2).
5. **`punt-implement-full-emulator-cb4310aa`**: Implement end-to-end `ScreenshotGeneratorAndroidTest` suite (Blocked by #1, #2, #3, and #4).
