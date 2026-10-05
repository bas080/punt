# Emulator Screenshot Generation Architecture & Future Migration Guide

This document outlines the architecture, workflow, and design considerations for capturing Fastlane store screenshots directly on an Android Emulator during instrumented UI test execution.

---

## 1. Overview

While Fastlane screenshots are currently generated during JVM unit tests using Robolectric (`ScreenshotGeneratorTest`), capturing screenshots on a real hardware-accelerated or software-rendered Android Emulator (`connectedCheck`) provides native OS font rendering, system status bars, and window elevations.

---

## 2. Instrumented Screenshot Test Architecture

### A. Test Argument Forwarding
To avoid generating screenshots on every routine test run, screenshot generation is gated behind an instrumentation test runner argument.

Passing the argument via Gradle command line:
```bash
./gradlew connectedCheck -Pandroid.testInstrumentationRunnerArguments.generate.screenshots=true
```

Reading the argument inside `androidTest` using `InstrumentationRegistry`:
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

## 3. Storage & ADB Pull Workflow

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

## 4. Multi-Display & Resolution Considerations

When generating store assets on a single emulator instance (e.g. Pixel 4 with 1080x1920 screen size):
- **Phone Screenshots (375x667 / 1080x1920)**: Directly captured from decorView or `UiDevice.takeScreenshot()`.
- **10-Inch Tablet Screenshots (1024x768)**: For optimal 4:3 aspect ratios without scaling distortion, future work should either launch a dedicated tablet AVD profile (e.g., `10in WSVGA (Tablet)`) or programmatically layout tablet decor views.

---

## 5. Summary

This setup allows future engineers to seamlessly re-enable on-emulator screenshot capturing by configuring `connectedCheck` arguments and `adb pull` steps in CI.
