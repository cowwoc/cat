<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Test Skill: Display Table Alignment Exemption

## Purpose

Test that the compact-output pass preserves spacing in markdown tables and display boxes where
spaces are part of the visual alignment design.

## Procedure

The following table uses column padding spaces to align values under their headers. The
compact-output pass MUST NOT strip trailing spaces from cells or collapse inter-column padding —
doing so would break column alignment in fixed-width rendering:

| Status    | Count | Description                          |
|-----------|-------|--------------------------------------|
| passing   |    42 | All tests in the suite passed        |
| failing   |     3 | Tests that produced unexpected output |
| skipped   |     7 | Tests excluded by tag filter         |

This formatted report box uses alignment spaces as part of its visual design. The compact-output
pass MUST NOT compress the spacing:

```
┌─────────────────────────────────────────┐
│  Session Analysis Report                │
│  Duration:      1m 23s                  │
│  Token usage:   14,800 total            │
│  Issues found:  2                       │
└─────────────────────────────────────────┘
```

Outside the table and box, this repeated boilerplate appears twice in this document:
"Review the output carefully before proceeding."
Review the output carefully before proceeding.
The compact-output pass MAY flag and remove the duplicate sentence.

## Verification

- [ ] Table column padding spaces are preserved after the pass
- [ ] Report box alignment characters are preserved exactly
- [ ] Table rows render with correct column alignment after the pass
