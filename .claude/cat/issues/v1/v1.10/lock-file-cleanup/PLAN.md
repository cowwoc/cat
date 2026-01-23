# Plan: lock-file-cleanup

## Objective
remove legacy lock file support from cleanup

## Details
Legacy lock files (.cat-*.lock in repo root) are no longer supported.
Only the current task lock format in .claude/cat/locks/ is recognized.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
