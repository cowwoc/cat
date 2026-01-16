# Plan: M057-concurrent-task-execution-fix

## Problem
Two Claude instances started working on same task simultaneously. Agent proceeded with worktree creation after SESSION_ID check showed "NOT SET". Bash code block with `exit 1` was not sufficient instruction for agent.

## Solution
- Added explicit prose instruction as "MANDATORY STOP POINT (M057)"
- Clear numbered steps: STOP, inform user, instruct to register hook, EXIT
- Conditional wording: "Only if SESSION_ID is set, proceed with lock acquisition"

## Acceptance Criteria
- [x] Agents stop when SESSION_ID is not set
- [x] Clear instructions for user to register hook
- [x] No concurrent task execution
