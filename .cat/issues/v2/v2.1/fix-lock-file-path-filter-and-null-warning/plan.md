# Plan

## Goal

Fix lock file extension filter in `IssueLock` to use `path.getFileName().toString().endsWith(".lock")`
instead of `path.toString().endsWith(".lock")` in both `list()` and `scanForConflictingLock()`, so
that only the filename component is checked (not ancestor directory names). Also emit a warning in
`scanForConflictingLock()` when `parseLockFile()` returns null, matching the existing warning
behavior already present in `list()`.

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] `IssueLock.list()` uses `path.getFileName().toString().endsWith(".lock")` (line 696)
- [ ] `IssueLock.scanForConflictingLock()` uses `path.getFileName().toString().endsWith(".lock")` (line 776)
- [ ] `scanForConflictingLock()` emits a warning via `this.warnings` when `parseLockFile()` returns null
- [ ] Regression tests verify both the getFileName() filtering and the null warning
- [ ] All existing tests pass
