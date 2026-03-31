# Plan

## Goal

Remove the lock deletion logic from SessionEnd hook. Locks should only be released by the cat:work cleanup phase when work is explicitly completed. SessionEnd should NOT delete any locks.

## Pre-conditions

(none)

## Post-conditions

- [ ] SessionEndHook.cleanTaskLocks() removed
- [ ] SessionEndHook.isLockOwnedBySession() removed
- [ ] SessionEndHook only removes stale locks older than 24 hours (not current-session locks)
- [ ] Tests updated/removed as needed
- [ ] --resume <sessionId> preserves issue locks across session boundaries
- [ ] E2E verification: acquire lock, shut down, resume same session, lock still owned
