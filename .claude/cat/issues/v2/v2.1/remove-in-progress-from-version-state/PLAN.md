# Plan: remove-in-progress-from-version-state

## Goal
Remove the "## Issues In Progress" section from version-level STATE.md files. This section is redundant because
each issue's individual STATE.md tracks its own status (open/in-progress/closed), and `get-available-issues.sh`
reads those per-issue files as the source of truth.

## Satisfies
- None (cleanup/simplification)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None - the section is not referenced by any plugin code
- **Mitigation:** N/A

## Files to Modify
- `.claude/cat/issues/v2/v2.1/STATE.md` - Remove "## Issues In Progress" section and move its entries to Issues Pending

## Acceptance Criteria
- [ ] No "## Issues In Progress" section exists in version-level STATE.md
- [ ] Issues that were listed under In Progress are moved to Issues Pending

## Execution Steps
1. **Edit version STATE.md:** Remove the "## Issues In Progress" heading and move all entries beneath it to the "## Issues Pending" section.
2. **Commit** the change.
