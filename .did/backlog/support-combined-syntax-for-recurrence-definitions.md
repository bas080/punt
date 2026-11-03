# Support combined syntax for recurrence definitions (#refine)

Add support for combined single-tag multi-duration recurrence syntax like `#every(12h, 2d)` or `#repeat(1d, 12h)`.

## Details
- Parse multi-duration lists inside parentheses for `#every(...)` or `#repeat(...)`.
- Select the earliest / shortest duration when punting recurring tasks upon completion.
