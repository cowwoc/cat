# Plan

## Goal

Fix the status display to show open issues that are locked by another session as in-progress (🔄)
instead of open (🔳). Currently, when another Claude session holds a lock on an issue, the status
box still shows the issue as open, leading to incorrect suggestions that it is available to work on.

Scope: status box only. All locks (active or stale) are treated as in-progress. The 🔄 indicator
is used (same as current session's in-progress issues).

## Pre-conditions

(none)

## Post-conditions

- [ ] Issues locked by another session are displayed as 🔄 (in-progress) in the status box instead of 🔳 (open)
- [ ] Regression test added verifying locked issues appear as in-progress in the status display
- [ ] No new issues introduced
- [ ] E2E verification: acquire a lock on an issue from one session context, run status from another session context, confirm the locked issue shows as 🔄
