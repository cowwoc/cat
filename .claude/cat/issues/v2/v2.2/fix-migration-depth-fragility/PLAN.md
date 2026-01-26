# Plan: fix-migration-depth-fragility

## Problem
Migration script Phase 13 uses `-mindepth 4 -maxdepth 4` to find issue-level PLAN.md files, which
assumes a fixed directory depth. If the versioning scheme changes (e.g., adding patch-level
directories), the depth changes and file selection fails silently.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None (behavior-preserving refactor of find logic)
- **Mitigation:** Tests verify file selection works correctly before and after change

## Files to Modify
- `plugin/migrations/2.1.sh` — replace depth-based selection with STATE.md sibling pattern

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Replace the `find ... -mindepth 4 -maxdepth 4` pattern in Phase 13 with logic that finds issue-level
  PLAN.md files by checking for a STATE.md sibling in the same directory
  - Files: `plugin/migrations/2.1.sh`
- Verify the replacement selects the same files as the original (no regressions)

## Post-conditions
- [ ] Phase 13 file selection no longer relies on fixed directory depth
- [ ] All open issue PLAN.md files are still selected correctly
- [ ] Migration remains idempotent
