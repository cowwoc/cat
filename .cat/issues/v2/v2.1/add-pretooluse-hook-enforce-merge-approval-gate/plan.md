# Plan

## Goal

Create a PreToolUse hook (EnforceMergeApprovalGate) that intercepts Bash calls to merge-and-cleanup and verifies
the session marker contains `approved` before allowing execution. This is a 2x recurrence of the agent skipping
the mandatory approval gate (Step 12 of work-merge-agent). Documentation-level prevention has failed twice.

## Pre-conditions

(none)

## Post-conditions

- [ ] PreToolUse hook blocks merge-and-cleanup when session marker is not `approved`
- [ ] Hook provides actionable error message pointing to Steps 7-12 of work-merge-agent
- [ ] Hook allows merge-and-cleanup when session marker is `approved`
- [ ] Hook allows merge-and-cleanup when trust is `high` and marker is `squashed:*` (auto-merge path)
- [ ] Unit tests cover all marker states: empty, squashed, approved, approved:invalidated
