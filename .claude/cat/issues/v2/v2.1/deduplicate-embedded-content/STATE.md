# State

- **Status:** closed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** []
- **Blocks:** []
- **Last Updated:** 2026-01-26

## Summary
Extracted `build_header_box()` from `stakeholder_handler.py` to `status_handler.py` where other
box utilities (display_width, build_line, build_border) already live. Updated handlers to use
the shared function.

## Changes
- `status_handler.py`: Added `build_header_box()` with optional `min_width` and `prefix` params
- `stakeholder_handler.py`: Removed local function, imports from status_handler
- `add_handler.py`: Uses shared function (2 places, -34 lines)
- `cleanup_handler.py`: Uses shared function (3 places, -42 lines)

## Metrics
- Files changed: 4
- Lines added: 48
- Lines removed: 111
- Net reduction: 63 lines
- Tests: 84/84 passing

## Notes
Original approach (static template files) was abandoned because LLMs cannot count character
display widths, making dynamic generation with `display_width()` necessary.
