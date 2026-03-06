# Plan: fix-background-agent-task-polling

## Problem

When a background Agent task completes, the main agent attempts to retrieve its output using
`TaskOutput` with an incorrect task ID format, resulting in "No task found" errors. Background
Agent tasks deliver results via `<task-notification>` system messages automatically — `TaskOutput`
polling is unnecessary and always fails.

## Satisfies

None — bugfix for internal tooling behavior

## Reproduction Code

```
# Agent spawns background Agent task:
Agent(description="Learn analysis", run_in_background=true)
# Returns agentId: a94f0f03405a97e89

# Later, agent tries to poll (WRONG):
TaskOutput(task_id="a94f0f03405a97e89")
# ERROR: No task found with ID: a94f0f03405a97e89

# The result arrives automatically as <task-notification> — no polling needed
```

## Expected vs Actual

- **Expected:** Agent waits for `<task-notification>` to process background Agent results
- **Actual:** Agent calls `TaskOutput` with the agent ID, which fails because Agent tasks use a
  different ID namespace than TaskCreate tasks

## Root Cause

No convention distinguishes between TaskCreate tasks (pollable via TaskOutput) and Agent background
tasks (deliver via `<task-notification>`). The agent conflates the two mechanisms.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — behavioral convention change
- **Mitigation:** Add clear instruction distinguishing the two task notification mechanisms

## Files to Modify

- Add convention to session instructions or a relevant skill file clarifying:
  - `TaskCreate` tasks: pollable via `TaskOutput`/`TaskGet`
  - `Agent(run_in_background=true)`: results arrive via `<task-notification>`, do NOT poll
- Consider adding to the learn skill (which spawns background agents) or to general session
  instructions

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

1. **Identify the best injection point**
   - Check if the learn skill documentation mentions how to handle background results
   - Determine if this belongs in session instructions or skill-specific docs

2. **Add the convention**
   - Document the distinction between TaskCreate polling and Agent background notifications
   - Add to the identified injection point

## Post-conditions

- [ ] No `TaskOutput` calls are issued for background Agent tasks
- [ ] The convention is documented in the appropriate instruction source
