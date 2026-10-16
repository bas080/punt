Short & Sweet Empty State Instruction Screen (#refine)

When no task cards are visible in the overview list (whether the list is completely empty or search/tag filters return 0 results), present a single unified instruction view rendered as clean left-aligned text directly on the dark background, aligned with the top search input box.

## Confirmed Design Specifications
- **Left-Aligned Layout**:
  - Aligned to the left, perfectly matching the padding and start position of the top search input box card (`paddingHorizontal="16dp"`).
  - Positioned `120dp` below the top card for balanced vertical placement on `@color/bg_dark` surface.
  - Plain typography text rendered directly on the background surface without pills, cards, or borders.
- **Content & Typography (Option B Wording)**:
  1. **Clear Filter (Conditional)**:
     - Plain text link in cyan accent color (`@color/accent` `#00E5FF`) reading **"Clear filter"** (no pill background or border).
     - Only visible when search query or tag/state filters return 0 results.
  2. **4 Core Flow Lines** (Text color: `@color/text_secondary` `#8E8E93`, `14sp`, left-aligned):
     - **Pull down to create task**
     - **Swipe right on task to mark done**
     - **Swipe left on task to punt**
     - **Use #every 1d for recurring tasks** (with `#every 1d` styled in dimmer tag secondary color)

## Questions for @bas080
*None remaining. All layout, alignment, color, and wording requirements confirmed.*
