# CAT Core Principles

## 1. Non-Linear Progression
Tasks execute based on dependency resolution, not sequence order. When dependencies are met, tasks
become eligible for execution regardless of their position in the list.

## 2. Concurrent Execution
Multiple subagents work on independent tasks simultaneously. No arbitrary limits on parallel execution.
The main agent manages concurrency dynamically.

## 3. Orchestration Model
Clear separation of concerns:
- **Main agent**: Coordinates, plans, merges (no production code)
- **Subagents**: Execute tasks in dedicated worktrees

## 4. Fail-Fast
Subagents return errors immediately when:
- Plan has issues
- Prerequisites not met
- Unrecoverable errors occur

Never continue with invalid state.

**Anti-pattern (M206):** When a skill or workflow says "FAIL immediately" or specifies error
output, do NOT attempt to "helpfully" work around the failure by:
- Manually performing what automated tooling should have done
- Providing a simplified or degraded version of the output
- Offering alternatives that bypass the required mechanism

The fail-fast instruction exists because the workaround will produce incorrect results.
Output the specified error message and STOP.

## 5. Token Awareness
Work sized to fit context limits:
- Default target: 40% of context (80K of 200K tokens)
- Track token usage via session files
- Detect compaction events
- Decompose oversized tasks into new user-visible tasks

## 6. Persistence
STATE.md files are source of truth for progress:
- Enable cross-session resume
- Survive session interruptions
- Allow parallel tracking of multiple tasks

## 7. Automatic Conflict Resolution
Main agent handles merge conflicts when integrating subagent branches. User escalation only when
automatic resolution fails.

## 8. Quality Gates
Every task passes through approval gates (unless trust is high):
- Work review
- Commit squashing by type
- User approval before merge to main
