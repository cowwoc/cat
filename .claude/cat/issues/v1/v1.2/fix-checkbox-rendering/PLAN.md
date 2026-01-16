# Plan: fix-checkbox-rendering

## Objective
fix checkbox rendering in cat:status output

## Details
Remove list dash prefix from checkbox syntax. Use '[x] task' instead
of '- [x] task' because the dash triggers markdown list rendering
which strips checkbox syntax in Claude Code CLI output.

Fixes M056.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
