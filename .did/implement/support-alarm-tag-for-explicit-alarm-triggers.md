# Support #alarm tag for explicit alarm triggers (#refine)

Refine specification and behavior for reminders tagged with `#alarm` (e.g. `Take medication #alarm`), which trigger a full alarm that the user must explicitly dismiss or turn off.

## Refinement Decisions
1. **Full-Screen Intent Alarm Activity**: Uses Android's `USE_FULL_SCREEN_INTENT` with `setShowWhenLocked(true)` and `setTurnScreenOn(true)` to present a full-screen alarm screen on top of the lock screen or active apps.
2. **Audio Playback**: Plays a continuous alarm ringtone sound on the alarm audio stream without vibration (vibration support logged as a potential future backlog item).
3. **Trigger Schedule**: Uses the task's **punt expiration timestamp** (`snooze_$cleanKey`). Fires when the punt/snooze expiration is reached for a task containing `#alarm`.
4. **Card Swipe Gesture Navigation**: Presents a single ringing task card that users can swipe **left** to punt or **right** to complete/advance:
   - **Above the card**: `← Swipe left to punt / snooze`
   - **Outside bottom right of the card**:
     - For recurring tasks: `Swipe right to punt to next recurring →`
     - For non-recurring tasks: `Swipe right to mark done →`
5. **Left Swipe Snooze Duration Picker**: Swiping left opens the snooze duration picker options dialog (e.g. 15m, 1h, custom duration) while stopping the ringing audio once a duration is selected.

## Screen Mockup Design

### 1. Recurring Task (#every 1d #alarm)
```
+---------------------------------------------------+
|                  ALARM RINGING                    |
|                                                   |
|           ← Swipe left to punt / snooze           |
|  +---------------------------------------------+  |
|  | Take medication #every 1d #alarm            |  |
|  |                                             |  |
|  |           Punted • until 08:00              |  |
|  +---------------------------------------------+  |
|        Swipe right to punt to next recurring →   |
|                                                   |
+---------------------------------------------------+
```

### 2. One-off / Non-recurring Task (#alarm)
```
+---------------------------------------------------+
|                  ALARM RINGING                    |
|                                                   |
|           ← Swipe left to punt / snooze           |
|  +---------------------------------------------+  |
|  | Pick up laundry #alarm                      |  |
|  |                                             |  |
|  |           Punted • until 08:00              |  |
|  +---------------------------------------------+  |
|                     Swipe right to mark done →   |
|                                                   |
+---------------------------------------------------+
```
