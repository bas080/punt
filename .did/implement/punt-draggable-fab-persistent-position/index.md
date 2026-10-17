Draggable Floating FAB with Persistent Screen Position

Enable long-press dragging for the floating menu FAB (`fab_menu`), allowing users to reposition the FAB on screen with persistent coordinates across app restarts.

## Confirmed Specifications
- **Long-Press Drag Gesture**:
  - Long pressing `fab_menu` triggers haptic feedback and enters drag mode, scaling `fab_menu` slightly (e.g. 1.1x) and raising elevation to visually indicate dragging.
  - Moving touch beyond touch slop updates FAB translation (`translationX`, `translationY`) smoothly.
  - Releasing touch (`ACTION_UP`) clamps the FAB position within screen boundary padding (e.g. 16dp from screen edges) and saves relative position percentages (`fab_rel_x`, `fab_rel_y`) in `reminders_prefs`.
- **Dynamic Menu Expansion Direction**:
  - When opening `fab_pill_menu_container`, evaluate whether the FAB center Y coordinate is in the top or bottom half of the screen.
  - If FAB is in the bottom half: expand pills vertically upwards above the FAB.
  - If FAB is in the top half: expand pills vertically downwards below the FAB.
- **Position Persistence & Restoration**:
  - Save relative percentages `fab_rel_x` (0.0 to 1.0) and `fab_rel_y` (0.0 to 1.0) in `reminders_prefs`.
  - Upon activity startup / layout, calculate target pixel coordinates from current container dimensions, clamp within window bounds, and set FAB translation.
- **Reset FAB Position Action**:
  - Add a "Reset FAB position" button (`btn_reset_fab_position`) in About view (`about_container`) under App Metadata card.
  - Tapping resets saved position preferences and moves `fab_menu` back to its default bottom-right location.
