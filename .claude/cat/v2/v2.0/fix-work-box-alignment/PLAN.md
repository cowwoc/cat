# Plan: fix-work-box-alignment

## Problem
The "Task Complete" and "Task Already Complete" boxes in `/cat:work` output have misaligned right borders. The vertical bar characters (`│`) on the right side don't align properly with the box corners, resulting in broken visual display.

## Satisfies
- None (UX bugfix)

## Reproduction Code
```
Run /cat:work on an already-completed task. The output box shows:
╭─ ✓ Task Already Complete ────────────────────────────────────────╮
│                                                                  │
│  **cleanup-display-redesign** is already completed               │
                                                                   │  <-- misaligned
```

## Expected vs Actual
- **Expected:** Right border `│` characters align vertically with `╮` and `╯` corners
- **Actual:** Right borders are offset, creating jagged appearance

## Root Cause
Fixed-width box templates in work.md have inconsistent padding/width calculations. Task names or content with varying lengths cause the right border to shift.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could affect other box displays if template is shared
- **Mitigation:** Test with various task name lengths

## Files to Modify
- plugin/skills/work/work.md - Fix box template widths and padding

## Test Cases
- [ ] Short task name displays correctly
- [ ] Long task name displays correctly
- [ ] All box corners align (╭╮╰╯)
- [ ] Right border `│` aligns on all lines

## Execution Steps
1. **Identify box templates in work.md**
   - Files: plugin/skills/work/work.md
   - Verify: Locate "Task Complete" and related box templates

2. **Fix padding/width calculations**
   - Files: plugin/skills/work/work.md
   - Verify: Manual inspection of box alignment
