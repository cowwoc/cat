# Task Locking for Concurrent Execution

## Overview

CAT uses task-level locking to safely support multiple Claude instances executing tasks concurrently.
Without locking, race conditions can corrupt STATE.md files, create conflicting worktrees, and
produce divergent task execution.

## Lock Mechanism

### Lock Files

Locks are stored in `.claude/cat/locks/<task-id>.lock` with format:

```
session_id=<uuid>
created_at=<unix-timestamp>
heartbeat=<unix-timestamp>
worktree=<path>
created_iso=<iso-timestamp>
```

### Persistent Locks

Locks persist until explicitly released:

- **No automatic expiration**: Locks never expire on their own
- **Explicit release required**: Owner session must release, or user must manually remove stale locks
- **User-driven cleanup**: If a session crashes, user must explicitly clear the lock via `/cat:cleanup`

### Atomic Operations

Lock acquisition uses atomic file operations:
1. Check existing lock validity
2. Create temp lock file
3. Atomic rename (`mv -n`) to lock file
4. Race loser cleans up and retries or fails

## Integration Points

### /cat:execute-task

1. **Acquire lock** after task identification (step: `acquire_lock`)
2. **Release lock** in cleanup step

### /cat:spawn-subagent

1. **Verify lock held** by parent agent
2. Subagent inherits lock ownership through worktree association

### /cat:merge-subagent

1. Lock released by parent's cleanup

### session-unlock.sh Hook

1. **Release all locks** owned by ending session
2. Provides safety net for crashed agents

## Script Usage

```bash
# Acquire lock
task-lock.sh acquire "1.0-parse-tokens" "$SESSION_ID" "$WORKTREE_PATH"

# Check lock status
task-lock.sh check "1.0-parse-tokens"

# Release lock
task-lock.sh release "1.0-parse-tokens" "$SESSION_ID"

# Force release (user action for crashed sessions)
task-lock.sh force-release "1.0-parse-tokens"

# List all locks
task-lock.sh list
```

## Race Conditions Prevented

| Race Condition | Prevention |
|----------------|------------|
| Simultaneous task execution | Lock acquisition fails for second instance |
| STATE.md corruption | Only lock holder writes state |
| Worktree conflicts | Lock acquired before worktree creation |
| Orphaned locks | Session-end cleanup + user-driven force release |

## Concurrent Execution Patterns

### Safe: Different Tasks

```
Instance A: /cat:execute-task 1.0-parse-tokens  (acquires lock 1.0-parse-tokens)
Instance B: /cat:execute-task 1.1-format-output (acquires lock 1.1-format-output)
```

Both proceed independently with their own locks.

### Blocked: Same Task

```
Instance A: /cat:execute-task 1.0-parse-tokens  (acquires lock)
Instance B: /cat:execute-task 1.0-parse-tokens  (BLOCKED - lock held)
```

Instance B receives error message with lock owner information.

### Recovery: Crashed Session

```
Instance A: Acquires lock, crashes
User: Runs /cat:cleanup or task-lock.sh force-release
Instance B: Acquires lock (previous lock removed by user)
```

## Troubleshooting

### "Task locked by another session"

1. Wait for other instance to complete
2. Check `/cat:status` for task state
3. If session crashed, use `/cat:cleanup` to remove stale locks
4. Force release: `task-lock.sh force-release <task-id>`

### Lock not released after completion

1. Check `session-unlock.sh` hook is registered
2. Verify SESSION_ID available to cleanup
3. Manual release: `task-lock.sh release <task-id> <session-id>`
4. Force release: `task-lock.sh force-release <task-id>`

### Stale locks from crashed sessions

Use `/cat:cleanup` command to list and remove stale locks:
```bash
/cat:cleanup
```

Or manually force-release specific locks:
```bash
task-lock.sh force-release "1.0-task-name"
```
