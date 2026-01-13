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

### Heartbeat-Based Leases

Locks use a heartbeat mechanism to handle crashed sessions:

- **Heartbeat interval**: 2 minutes (recommended refresh during long operations)
- **Stale threshold**: 5 minutes without heartbeat
- **Stale locks**: Automatically claimable by other sessions

### Atomic Operations

Lock acquisition uses atomic file operations:
1. Check existing lock validity
2. Create temp lock file
3. Atomic rename (`mv -n`) to lock file
4. Race loser cleans up and retries or fails

## Integration Points

### /cat:execute-task

1. **Acquire lock** after task identification (step: `acquire_lock`)
2. **Refresh heartbeat** during long operations
3. **Release lock** in cleanup step

### /cat:spawn-subagent

1. **Verify lock held** by parent agent
2. Subagent inherits lock ownership through worktree association

### /cat:merge-subagent

1. **Refresh heartbeat** during merge operations
2. Lock released by parent's cleanup

### session-unlock.sh Hook

1. **Release all locks** owned by ending session
2. Provides safety net for crashed agents

## Script Usage

```bash
# Acquire lock
task-lock.sh acquire "1.0-parse-tokens" "$SESSION_ID" "$WORKTREE_PATH"

# Check lock status
task-lock.sh check "1.0-parse-tokens"

# Refresh heartbeat
task-lock.sh heartbeat "1.0-parse-tokens" "$SESSION_ID"

# Release lock
task-lock.sh release "1.0-parse-tokens" "$SESSION_ID"

# Cleanup stale locks
task-lock.sh cleanup --stale-minutes 5

# List all locks
task-lock.sh list
```

## Race Conditions Prevented

| Race Condition | Prevention |
|----------------|------------|
| Simultaneous task execution | Lock acquisition fails for second instance |
| STATE.md corruption | Only lock holder writes state |
| Worktree conflicts | Lock acquired before worktree creation |
| Orphaned locks | Session-end cleanup + stale detection |

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
Instance A: Acquires lock, crashes (no heartbeat update)
... 5 minutes pass ...
Instance B: Acquires lock (stale lock detected, overwritten)
```

## Troubleshooting

### "Task locked by another session"

1. Wait for other instance to complete
2. Check `/cat:status` for task state
3. If session crashed, wait 5 minutes for stale detection
4. Emergency: `task-lock.sh cleanup --stale-minutes 0`

### Lock not released after completion

1. Check `session-unlock.sh` hook is registered
2. Verify SESSION_ID available to cleanup
3. Manual release: `task-lock.sh release <task-id> <session-id>`

### Stale locks accumulating

Run periodic cleanup:
```bash
task-lock.sh cleanup --stale-minutes 10
```

Or via `/cat:cleanup` command.
