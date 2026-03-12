# State

- **Status:** closed
- **Resolution:** implemented
- **Progress:** 100%
- **Dependencies:** []
- **Blocks:** []
- **Target Branch:** v2.1

## Post-Closure Bugfix (2026-03-12)

**Fixed corrupt issue directory detection logic** to flag directories missing PLAN.md regardless of STATE.md presence.

The original implementation only flagged directories as corrupt when STATE.md existed AND PLAN.md was missing.
This missed the case where an issue directory had neither STATE.md nor PLAN.md — the directory is corrupt
and should be flagged, regardless of whether STATE.md is present.

**Changes:**
- IssueDiscovery: Simplify isCorrupt to check only PLAN.md presence
- IssueDiscovery.Found: Remove mutual exclusivity between isCorrupt and createStateMd
- GetCleanupOutput: Add VERSION_DIR_PATTERN to distinguish version dirs from issue dirs
- Tests: Update and add new tests for both-flags-true and missing-both-files cases

**Test results:** All 2403 tests pass
