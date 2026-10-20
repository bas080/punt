Swipe Screen Navigation, Collapsible Task Sections, and Top FAB Button

A major UI refinement that replaces the floating FAB menu and filter dialog with gesture-based ViewPager2 screen navigation, collapsible Active/Punted section headers, and a top search card action button.

## Specifications & Requirements

1. **ViewPager2 Swipe Screen Navigation**:
   - Replace manual view visibility toggling with a `ViewPager2` adapter holding the Tasks view (`reminders_container`) and About view (`about_container`).
   - Enable horizontal swiping (left/right) to switch between Tasks and About screens.
   - Task item gestures (swipe right to complete, swipe left to punt) take precedence so item swiping does not trigger page switching.

2. **Top Search Bar FAB Button & Filter Dialog Removal**:
   - Remove the old bottom floating FAB container (`layout_fab_container`, `fab_menu`) and the Tasks/About pills.
   - Remove the filter icon (`btn_tags_filter`) and old filter selection dialog (`showFilterDialog`).
   - Place a normal button inside the top search card where the filter button was located. Tapping this button opens the vertical pill action menu (Punt, Focus, Share).
   - Filtering is driven entirely by typing text queries directly in `search_reminder_input` or choosing Focus Profiles.

3. **Collapsible Section Headers**:
   - Render section headers for both active and punted tasks: `ACTIVE (count)` and `PUNTED (count)`.
   - Tapping either section header toggles its collapsed/expanded state.
   - Collapsed section states persist across app restarts in `reminders_prefs`.

4. **Empty State Instructions Update**:
   - Update instruction lines on `layout_empty_reminders` to explicitly mention swiping left/right to navigate between Tasks and About screens.
