Always-on-Screen Overlay FAB (#refine)

Add an opt-in feature toggle in Punt app settings allowing the floating action button (FAB) to remain visible as a persistent system overlay (`SYSTEM_ALERT_WINDOW`) above all other applications, enabling 1-tap task creation and quick access anywhere on Android.

## Proposed Scope
- **Settings Toggle**: Add a toggle button/switch in About view (`btn_toggle_overlay_fab`) to enable/disable Always-On-Screen Overlay FAB.
- **System Permission**: Direct users to Android's "Display over other apps" (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`) if `Settings.canDrawOverlays(context)` returns false.
- **Background Overlay Service**: Run a lightweight `ForegroundService` with a minimalist system window overlay (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`) rendering the draggable Punt FAB.
- **Overlay Actions**: Tapping the floating overlay launches quick task creation dialog (`CreateReminderActivity` or `PickNotificationActivity`) without launching full main activity.

## Questions for @bas080
1. Should tapping the persistent overlay FAB open a lightweight dialog overlay (`CreateReminderActivity`) or bring `MainActivity` to the foreground?
2. When the overlay FAB is enabled, should it support the exact same expanded pill menu (Tasks, About, Punt, Share) as the main activity FAB, or only a single quick-add task action?
3. Android requires a persistent status bar notification for foreground services. Should we reuse Punt's existing status notification channel (`NOTIFICATION_ID`) or register a dedicated notification channel for the overlay service?
