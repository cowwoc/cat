# Plan

## Goal

Prevent incorrect lock release when a rebase conflict occurs during merge. Rebase conflicts are transient/user-recoverable errors and should not trigger lock release. The lock should be held until either: (a) merge succeeds, or (b) user explicitly aborts the issue.

## Pre-conditions

(none)

## Post-conditions

- [ ] Error handling section of work-merge-agent/first-use.md reclassifies rebase conflicts from "permanent" to "transient"
- [ ] When merge-and-cleanup returns a rebase conflict error, lock_retained=true is included in response JSON
- [ ] Lock file remains on disk after rebase conflict FAILED return
- [ ] User can re-run /cat:work on same issue and find lock still held by their session
- [ ] Permanent error classification (missing branch) still releases lock correctly
- [ ] All existing tests pass, new tests added for transient error handling
