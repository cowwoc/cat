# Plan: task-locking-subagent-delegation

## Objective
add task locking and subagent delegation requirements

## Details
Task Locking (concurrent execution safety):
- Add scripts/task-lock.sh with acquire/release/check/heartbeat/cleanup
- Locks use heartbeat-based leases (5-minute stale threshold)
- execute-task acquires lock before worktree creation
- session-unlock.sh releases locks on session end
- cleanup command includes lock status survey and cleanup

Subagent Delegation (unsupervised execution):
- Subagents run without user supervision - users cannot see output
- All decisions must happen in main agent before spawning
- Subagents can explore/research but must return findings only
- Fail-fast required: no fallback behaviors (those involve decisions)
- Prompts must be complete specifications for mechanical execution

New files:
- scripts/task-lock.sh - lock management utility
- .claude/cat/references/task-locking.md - locking documentation
- .claude/cat/references/subagent-delegation.md - delegation principles

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
