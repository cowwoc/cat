# Add Descriptions to Retrospective Output

## Goal

Make the retrospective output self-explanatory by including brief descriptions alongside pattern IDs
and action item IDs, so users can understand what each entry refers to without consulting index.json.

## Background

The current retrospective output shows entries like:
- `A001: ineffective` — no indication of what A001 is about
- `PATTERN-001: recurring (occurrences: 9/5)` — no indication of what the pattern is

## Scope

Modify `GetRetrospectiveOutput.java` to:
1. Include a brief description for each action item in the effectiveness section
2. Include the pattern name/note for each pattern in the pattern status section

## Post-Conditions

- [ ] Action item effectiveness lines include a truncated description
- [ ] Pattern status lines include the pattern name
- [ ] All existing tests pass
- [ ] New/updated tests cover the changed output format
