# Plan: fix-hierarchy-docs

## Problem
The hierarchy.md documentation shows tasks under a `task/<name>/` subdirectory, but the actual implementation
(version-paths.md and find-task.sh) places tasks directly under the version directory. Additionally, hierarchy.md
assumes MAJOR.MINOR versioning but version-paths.md supports MAJOR, MAJOR.MINOR, and MAJOR.MINOR.PATCH schemes. The
versioning scheme information is scattered across multiple files without a single source of truth.

## Satisfies
None - documentation consistency fix

## Expected vs Actual
- **Expected:** hierarchy.md matches actual path structure; versioning schemes documented in one place and referenced
  elsewhere
- **Actual:** hierarchy.md shows incorrect `task/<name>/` structure, only describes MAJOR.MINOR, and scheme info is
  duplicated/inconsistent

## Root Cause
Documentation drift - hierarchy.md was not updated when path conventions were finalized. No centralized versioning
scheme documentation exists.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - documentation only
- **Mitigation:** Verify paths match version-paths.md; ensure all references are updated

## Files to Modify
- plugin/concepts/version-scheme.md - NEW: Centralized versioning scheme documentation
- plugin/concepts/hierarchy.md - Fix task path structure, reference version-scheme.md
- plugin/concepts/version-paths.md - Reference version-scheme.md instead of duplicating
- plugin/concepts/version_completion.md - Reference version-scheme.md
- plugin/concepts/work.md - Reference version-scheme.md for boundary detection

## Test Cases
- [ ] Task path examples in hierarchy.md match version-paths.md get_task_path() output
- [ ] version-scheme.md documents all three schemes (MAJOR, MAJOR.MINOR, MAJOR.MINOR.PATCH)
- [ ] All concept files reference version-scheme.md for scheme details
- [ ] No duplicate scheme documentation remains

## Execution Steps
1. **Step 1:** Create version-scheme.md with centralized versioning documentation
   - Extract scheme table from version-paths.md
   - Add scheme detection guidance
   - Include example paths for each scheme
2. **Step 2:** Update hierarchy.md
   - Fix task path structure (remove `task/<name>/` subdirectory)
   - Replace MAJOR.MINOR-only content with reference to version-scheme.md
   - Verify diagram matches `.claude/cat/issues/v*/v*.*/*/STATE.md` glob pattern
3. **Step 3:** Update version-paths.md
   - Replace "Supported Versioning Schemes" section with reference to version-scheme.md
   - Keep path resolution functions (they implement the schemes)
4. **Step 4:** Update version_completion.md
   - Add reference to version-scheme.md where version levels are mentioned
5. **Step 5:** Update work.md
   - Reference version-scheme.md in boundary detection section
