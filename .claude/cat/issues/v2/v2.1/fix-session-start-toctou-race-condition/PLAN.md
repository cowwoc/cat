# Plan: fix-session-start-toctou-race-condition

## Goal
Add atomic locking to `session-start.sh` runtime acquisition to prevent concurrent sessions from corrupting the JDK
bundle directory when both attempt to download simultaneously.

## Satisfies
None - security hardening from stakeholder review of 2.1-session-start-version-check

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Lock contention could slow startup; stale locks from crashed sessions
- **Mitigation:** Use `mkdir` for atomic lock acquisition with timeout; add stale lock detection based on age

## Files to Modify
- `plugin/hooks/session-start.sh` - Add lock acquisition around download_runtime in try_acquire_runtime
- `tests/hooks/session-start.bats` - Add tests for concurrent download protection

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Add atomic locking to try_acquire_runtime**
   - Files: `plugin/hooks/session-start.sh`
   - Before calling `download_runtime`, acquire a lock using `mkdir "${jdk_path}.lock"` (atomic on POSIX)
   - If lock already exists, wait with timeout (30s) and retry; if lock acquired by another session that completed,
     re-check VERSION file
   - Add trap to clean up lock directory on exit
   - Add stale lock detection: if lock directory mtime > 10 minutes, remove and retry

2. **Add tests for locking behavior**
   - Files: `tests/hooks/session-start.bats`
   - Test: lock acquisition succeeds when no lock exists
   - Test: stale lock is detected and removed
   - Test: lock is cleaned up on function exit (trap)

## Post-conditions
- [ ] Concurrent sessions cannot simultaneously download to the same JDK directory
- [ ] Lock is automatically released after download completes (success or failure)
- [ ] Stale locks from crashed sessions are detected and cleaned up
- [ ] Existing single-session behavior is unchanged (no performance regression on fast path)
