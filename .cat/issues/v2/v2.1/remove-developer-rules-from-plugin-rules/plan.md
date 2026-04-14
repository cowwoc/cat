# Plan: Remove Developer Rules from plugin/rules

## Goal
Remove `backwards-compatibility.md` and `license-header.md` from `plugin/rules/` — these govern CAT development
conventions (no backwards-compat shims, license header format) that end-users of the plugin have no need for.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — these files are only injected when matching paths are in context, and the same rules
  remain available to developers via `.claude/rules/`
- **Mitigation:** None needed

## Files to Modify
- `plugin/rules/backwards-compatibility.md` - delete
- `plugin/rules/license-header.md` - delete

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Delete `plugin/rules/backwards-compatibility.md`
- Delete `plugin/rules/license-header.md`

## Post-conditions
- [ ] `plugin/rules/backwards-compatibility.md` no longer exists
- [ ] `plugin/rules/license-header.md` no longer exists
- [ ] No other files in `plugin/rules/` were modified
