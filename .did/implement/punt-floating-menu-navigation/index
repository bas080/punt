Floating Bottom-Right Menu / Hamburger Navigation for Tasks & About views

Replace the current bottom navigation bar (`header_navigation`) with a floating hamburger FAB stuck to the bottom-right corner that expands vertically upward pill options on tap or swipe-up.

## Confirmed Design Specifications
- **Floating Button (FAB)**:
  - Fixed/stuck in bottom-right corner (`fab_menu`).
  - Clean 3-line hamburger vector (`ic_action_menu`).
  - Lighter background surface tint (`@color/bg_surface` / `#2C2C2E`) with primary text/vector color (`@color/text_primary`)—no cyan tint.
  - Tapping OR swiping up toggles the vertical pill menu open/closed.
- **Vertical Upward Pills (Expanded State)**:
  - **Navigation Pills** ("Tasks", "About"):
    - Solid surface background (`@drawable/bg_pill`) with crisp white text (`@color/text_primary`).
    - Indicates current view selection state (`isSelected`).
  - **Bulk Action Pills** ("Share Matching", "Export All"):
    - Outlined pill styling with cyan accent border (`@color/accent`) and leading vector action icon (`ic_action_share`, `ic_action_bulk`).
    - Visually distinguishes executable actions from navigation view tabs.
- **Footer Removal**:
  - The old bottom footer container card (`header_navigation`) is completely removed, granting full vertical screen space to the reminders list.
- **Interactions & Keyboard**:
  - Tapping a pill triggers its view switch or action and collapses the menu stack.
  - Tapping outside the expanded pill stack collapses the menu back into the FAB.
  - Automatically hides the FAB and expanded pills when soft keyboard is visible.
