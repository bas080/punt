# Support #notify tags for Calendar VALARM reminders (#refine)

Refine specification for adding `VALARM` calendar alarms to exported/synced Calendar events when reminders contain `#notify` duration tags (e.g., `#notify 1d`, `#notify(1h)`, `#notify:15m`, `#notify1d`).

## Refinement Decisions
1. **Flexible Duration Units**: `#notify` supports the exact same flexible duration syntax as `#every` / `#repeat` (e.g., `#notify 1d`, `#notify 12h`, `#notify:30m`, `#notify(15m)`).
2. **Calendar VALARM / Reminders Integration**: When `CalendarSyncManager` creates or updates calendar events for punted reminders tagged with `#notify`, it converts each parsed duration offset into minutes prior to event start and inserts corresponding `CalendarContract.Reminders` (`VALARM`) entries.
3. **Multiple `#notify` Support**: If a reminder contains multiple `#notify` tags (e.g. `Appointment #notify 1d #notify 1h`), `CalendarSyncManager` attaches multiple calendar alarm entries (`CalendarContract.Reminders`) so calendar apps alert at each specified lead time.
4. **All-Day Event Lead Time Calculation**: For unpunted reminders (synced as all-day events), `#notify` lead times default to calculating offset relative to 09:00 AM on the event date.
5. **Subtle Distinct Inline Styling**: `#notify` tags in active reminder cards are styled inline using a distinct subtle accent color (e.g., `@color/accent_subtle`, `#80DEEA`) to distinguish notification lead-time tags from recurrence tags (`@color/text_secondary`, `#8E8E93`) while remaining clean and subtle.
