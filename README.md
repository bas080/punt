# Punt

[![Stand with Palestine](https://img.shields.io/badge/🇵🇸%20%20Stand%20With%20Palestine-007A3D?style=flat-square&color=brightgreen)](https://www.islamic-relief.org.uk/giving/appeals/palestine/)
[![Donate via Liberapay](https://img.shields.io/badge/Donate-Liberapay-F6C915?style=flat-square&logo=liberapay&logoColor=black)](https://liberapay.com/bas080)
[![Available on IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/com.bas080.notificationreminders&label=IzzyOnDroid&cacheSeconds=86400)](https://apt.izzysoft.de/fdroid/index/apk/com.bas080.notificationreminders)

A simple Android application that keeps track of your reminders and alerts you whenever a relevant notification arrives on your device.

## Why "Punt"?

The app is named **Punt** after the verb *to punt*—meaning to defer, postpone, or kick something down the field for later. In Punt, when you are busy or focused on another task, you can easily "punt" (snooze) reminders until a convenient time or until a relevant notification arrives.

## How It Works

1. **Add Your Reminders**: Easily add reminders directly in the app or quick-add them from your notification shade.
2. **Smart Notification Monitoring**: The app watches incoming notifications for key words matching your active reminders.
3. **Intelligent Word Matching**:
   - Matches keywords regardless of capitalization
   - Automatically ignores common filler words (such as "the", "a", "is", "in")
   - Matches keywords anywhere within incoming notification messages
4. **Timely Alerts**: When a matching notification arrives, a reminder alert is displayed in your notification tray.
5. **Manage Reminders & Alerts**:
   - Tap any notification group to open the app while keeping your reminder alerts visible in the notification tray.
   - Tap **Done** on an alert notification to mark a reminder complete and clear its notification.
   - Tap **Share** on an alert notification to share the reminder via any app.
   - Manage active reminders or view app logs anytime inside the app.

## Features

- **Clean Minimalist Design**: High-contrast, text-first interface that automatically adapts to your system dark or light theme.
- **Quick Reminder Creation**: Add reminders directly from the main screen or inline from the notification shade without switching apps.
- **Grouped Notification Alerts**: Matched reminders are neatly organized together in your notification center.
- **In-App Activity Logs**: Easily view app activity logs directly inside the app.
- **Automatic Crash Reporting**: Includes a simple optional crash report helper if something unexpected occurs.

To view screenshots of the app features, check out the [Screenshots Gallery](fastlane/metadata/android/en-US/images/phoneScreenshots/).

## Permissions

The app requests minimal Android system permissions to operate transparently:

- **Notification Listener Access (Required)**: Allows the app to monitor incoming notification text so it can match keywords against your active reminders.
- **Post Notifications (Required on Android 13+)**: Allows the app to post status and matched reminder alerts in your notification tray.
- **Start at Boot (Automatic / Optional)**: Ensures reminder listeners and background status notifications setup automatically when your device restarts.
- **Foreground Service**: Keeps reminder monitoring running reliably in the background without being closed by Android battery optimization.

## Availability & IzzyOnDroid

This repository is fully configured and compliant with **IzzyOnDroid** F-Droid repository criteria:
- **Fastlane Metadata**: Structured app titles, short and full descriptions, graphics, and changelogs maintained under `fastlane/metadata/android/en-US/`.
- **Open Source & Privacy-First**: 100% free software under the MIT License with zero ads, analytics, or trackers.
- **Signed Release Binaries**: Automated CI release workflow attaching signed release APKs (`app-release.apk`) to version tags (`v*`).

## Installation & Google Play Protect

Because releases downloaded directly from GitHub are self-signed, Android's **Google Play Protect** may display a standard prompt during installation (such as *"Unrecognized app"* or *"Blocked by Play Protect"*).

### How to Install:
1. Open the downloaded `.apk` file on your Android device.
2. When the Google Play Protect prompt appears, tap **"More details"** (or **"Advanced"**).
3. Tap **"Install anyway"** to complete installation.

## License

MIT License
