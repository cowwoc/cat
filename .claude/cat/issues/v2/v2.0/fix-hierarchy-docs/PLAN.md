# Plan: fix-hierarchy-docs

## Problem
The hierarchy.md documentation shows tasks under a `task/<name>/` subdirectory, but the actual implementation (version-paths.md and find-task.sh) places tasks directly under the version directory. Additionally, hierarchy.md assumes MAJOR.MINOR versioning but version-paths.md supports MAJOR, MAJOR.MINOR, and MAJOR.MINOR.PATCH schemes.

## Satisfies
None - documentation consistency fix

## Expected vs Actual
- **Expected:** hierarchy.md matches actual path structure and supports all versioning schemes
- **Actual:** hierarchy.md shows incorrect `task/<name>/` structure and only describes MAJOR.MINOR

## Root Cause
Documentation drift - hierarchy.md was not updated when path conventions were finalized.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - documentation only
- **Mitigation:** Verify paths match version-paths.md

## Files to Modify
- plugin/concepts/hierarchy.md - Fix task path structure, add versioning scheme flexibility

## Test Cases
- [ ] Task path examples match version-paths.md get_task_path() output
- [ ] All three versioning schemes (MAJOR, MAJOR.MINOR, MAJOR.MINOR.PATCH) documented

## Execution Steps
1. **Step 1:** Update structure diagram to show tasks directly under version directory
   - Files: plugin/concepts/hierarchy.md
   - Verify: Diagram matches `.claude/cat/issues/v*/v*.*/*/STATE.md` glob pattern
2. **Step 2:** Add versioning scheme section showing all three supported schemes
   - Reference: version-paths.md "Supported Versioning Schemes" table
   - Verify: Section mentions MAJOR, MAJOR.MINOR, and MAJOR.MINOR.PATCH
