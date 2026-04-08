---
paths: ["plugin/**", "client/**"]
---
## Multi-Instance Safety

**MANDATORY:** All changes must be safe when multiple Claude instances run concurrently, each in its own isolated worktree.

Instances must NEVER:
- Overwrite each other's files
- Access each other's temporary state
- Share mutable file paths without session/worktree isolation

### Safe Patterns

**Temporary files/directories (ephemeral state — not needed across session restarts):**
- ✅ Use `mktemp -d` or `mktemp` for unique per-invocation isolation
- ✅ Store in `/tmp` or per-session directories (e.g., `${CLAUDE_CONFIG_DIR}/projects/.../${CLAUDE_SESSION_ID}/`)
- ❌ Use hardcoded paths like `../repo-clean.git` or `/tmp/shared-work/`
- ❌ Use paths like `${WORKTREE_PARENT}/shared-dir/` (multiple instances share the parent)

**Resumable progress state (data that must survive session restarts):**
- ✅ Write to `.cat/work` inside the issue worktree (e.g., `${WORKTREE_PATH}/.cat/work/progress.json`)
- ❌ Write to `/tmp` — `/tmp` may be cleared between sessions on Linux (systemd-tmpfiles) and macOS,
  causing resumable state to be lost when the user restarts a conversation

```bash
# WRONG: progress state lost if session is restarted
echo '{"step": 3}' > /tmp/cat-progress-${ISSUE_ID}.json

# CORRECT: progress state persists across session restarts
echo '{"step": 3}' > "${WORKTREE_PATH}/.cat/work/progress.json"
```

**Example: History rewriting with isolated temp directories**
```bash
# WRONG: All instances write to same path
BARE_REPO="../repo-clean.git"
git clone --mirror . "$BARE_REPO"

# CORRECT: Each instance uses unique temp directory
WORK_DIR=$(mktemp -d)
BARE_REPO="${WORK_DIR}/repo-clean.git"
git clone --mirror . "$BARE_REPO"
cd "$BARE_REPO"
# ... do work ...
rm -rf "$WORK_DIR"
```

**Locks and markers:**
- Lock files must include session ID: `${LOCKS_DIR}/${ISSUE_ID}.lock` (not shared)
- Marker files must be per-session: `${SESSION_DIR}/marker-name` (not in worktree/main workspace)
- Use `${CLAUDE_SESSION_ID}` as part of any file path that tracks instance state
- **CRITICAL:** When you encounter a lock file with a session_id different from your own (`${CLAUDE_SESSION_ID}`), it is **NOT necessarily stale** — it belongs to another active Claude instance. Do NOT assume it is old or try to delete it. Multiple instances run concurrently, each with its own session ID. A lock file containing a different session_id is evidence of concurrent work, not abandoned work.

**Build artifacts:**
- Worktree builds must NOT write to the main workspace `target/` directory
- Each worktree has its own `${WORKTREE_PATH}/target/` (git-ignored, not shared)
- Main workspace `target/` is read-only from worktrees

**Reading shared state:**
- ✅ Main workspace and shared plugin cache are read-only from worktrees
- ✅ Multiple instances can read the same `.cat/config.json`
- ❌ Do NOT write shared configuration from a worktree (configuration belongs in main workspace only)

### Why This Matters

Worktrees are isolated for a reason: when implementations can run in parallel (multiple users, multiple sessions), shared mutable state causes:
- Race conditions (two instances overwrite the same file)
- Lost work (one instance deletes another's temporary data)
- Mysterious failures (instance A expects a file that instance B deleted)
- Deadlocks (instances waiting for locks on same resources)

The pattern works because:
1. Each instance owns its temporary files (created by `mktemp`)
2. Lock files include session ID (preventing cross-session conflicts)
3. Main workspace is read-only from worktrees (no race conditions)
4. Worktree-local artifacts (build results) don't interfere

## Enforcement

```cat-rules
- pattern: "/workspace/"
  files: "*.sh,*.md"
  severity: medium
  message: "Hardcoded /workspace/ path violates worktree isolation. Use ${CLAUDE_PROJECT_DIR} or\
 ${WORKTREE_PATH}. See .claude/rules/multi-instance-safety.md § Multi-Instance Safety."
```
