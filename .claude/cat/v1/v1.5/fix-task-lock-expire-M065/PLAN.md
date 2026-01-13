# Plan: fix-task-lock-expire-M065

## Objective
prevent waiting for task lock to expire (M065)

## Details
When lock is held by different session, the old guidance suggested:
- Wait for the other instance to complete
- Use cleanup --stale-minutes 0 to force release

This is WRONG. Stale cleanup is for crashed sessions, not active workers.
Waiting/forcing corrupts the other instance's work.

New guidance:
- MANDATORY: Execute a DIFFERENT task instead
- DO NOT wait, force release, or retry

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
