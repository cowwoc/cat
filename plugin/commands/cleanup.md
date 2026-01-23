---
name: cat:cleanup
description: Clean up abandoned worktrees, lock files, and orphaned branches
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
---

# CAT Cleanup

## Purpose

All abandoned CAT artifacts (worktrees, locks, branches) are identified and cleaned up safely.

---

## When to Use

- A previous session crashed or was cancelled
- Lock files are blocking new execution
- Orphaned worktrees are cluttering the filesystem

---

## Safety Rules

- NEVER cd into a worktree that will be deleted - use `git -C <path>` instead
- ALWAYS check for uncommitted changes before removing worktrees
- ALWAYS ask user before removing anything with uncommitted work
- ALWAYS remove worktree BEFORE deleting its branch
- NEVER force-delete branches that might have unmerged commits

---

## Procedure

### Step 1: Survey Current State

Run all survey commands to gather current artifact state.

**Worktrees:**
```bash
git worktree list
```

**Task locks:**
```bash
if [[ -d .claude/cat/locks ]]; then
  "${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" list 2>/dev/null
else
  echo "[]"
fi
```

**Context files:**
```bash
ls -la .cat-execution-context 2>/dev/null || echo "No context file"
```

**CAT branches:**
```bash
git branch -a | grep -E '(release/|worktree|[0-9]+\.[0-9]+-)' || echo "No CAT branches"
```

**Remote branch staleness (1-7 days idle):**
```bash
git fetch --prune 2>/dev/null
for remote_branch in $(git branch -r 2>/dev/null | grep -E 'origin/[0-9]+\.[0-9]+-' | tr -d ' '); do
  COMMIT_DATE=$(git log -1 --format='%ct' "$remote_branch" 2>/dev/null)
  NOW=$(date +%s)
  AGE_DAYS=$(( (NOW - COMMIT_DATE) / 86400 ))
  if [ "$AGE_DAYS" -ge 1 ] && [ "$AGE_DAYS" -le 7 ]; then
    AUTHOR=$(git log -1 --format='%an' "$remote_branch" 2>/dev/null)
    RELATIVE=$(git log -1 --format='%cr' "$remote_branch" 2>/dev/null)
    echo "STALE: $remote_branch (last: $AUTHOR, $RELATIVE)"
  fi
done
```

**Invoke handler to generate display:**

```json
{
  "handler": "cleanup",
  "context": {
    "phase": "survey",
    "worktrees": [{"path": "<path>", "branch": "<branch>", "state": "<state>"}],
    "locks": [{"task_id": "<id>", "session": "<session-id>", "age": <seconds>}],
    "branches": ["<branch-name>"],
    "stale_remotes": [{"branch": "<name>", "author": "<author>", "relative": "<time>"}],
    "context_file": "<path-or-null>"
  }
}
```

Output the PRE-COMPUTED display exactly as provided. Example:

```
╭─ 🔍 Survey Results ─────────────────────────────────╮
│ ╭─ 📁 Worktrees ──────────────────────────────────╮ │
│ │ /workspace/.worktrees/task: 2.0-task [prunable] │ │
│ ╰─────────────────────────────────────────────────╯ │
│                                                     │
│ ╭─ 🔒 Task Locks ─────────────────────────────────╮ │
│ │ 2.0-task: session=abc12345, age=3600s           │ │
│ ╰─────────────────────────────────────────────────╯ │
│                                                     │
│ ╭─ 🌿 CAT Branches ───────────────────────────────╮ │
│ │ 2.0-test-branch                                 │ │
│ ╰─────────────────────────────────────────────────╯ │
│                                                     │
│ ╭─ ⏳ Stale Remotes (1-7 days) ───────────────────╮ │
│ │ origin/old: Test, 3 days ago                    │ │
│ ╰─────────────────────────────────────────────────╯ │
│                                                     │
│ 📝 Context: None                                    │
╰─────────────────────────────────────────────────────╯

Found: 1 worktrees, 1 locks, 1 branches, 1 stale remotes
```

---

### Step 2: Identify Abandoned Artifacts

Analyze survey results to classify artifacts:

**Abandoned worktree indicators:**
- Lock file references session that is no longer active
- Worktree directory exists but has no recent activity
- No corresponding lock exists (orphaned)

**Stale lock indicators:**
- Lock age exceeds reasonable session duration (hours old)
- No heartbeat updates (if heartbeat tracking enabled)

For each lock, check status:
```bash
task_id="<from-survey>"
"${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" check "$task_id"
```

The output shows: `{"locked":true,"session_id":"...","age_seconds":...,"worktree":"..."}`

Present classification:

```
## Abandoned Artifacts

### Likely Abandoned
- <artifact>: <reason>

### Possibly Active
- <artifact>: <reason for caution>

### Safe to Keep
- <artifact>: <reason>
```

---

### Step 3: Check for Uncommitted Work

**CRITICAL: Use `git -C <path>` - NEVER cd into a worktree that will be deleted.**

For each worktree identified as abandoned:

```bash
WORKTREE_PATH="<path-from-survey>"
git -C "$WORKTREE_PATH" status --porcelain
```

If output is non-empty, there is uncommitted work.

Present findings:

```
## Uncommitted Work Check

### <worktree-path>
Status: CLEAN | HAS UNCOMMITTED WORK

If uncommitted:
  Modified files:
  - <file1>
  - <file2>

  Options:
  1. Commit changes first
  2. Stash: git -C <path> stash
  3. Discard: git -C <path> checkout -- . (DESTRUCTIVE)
  4. Skip this worktree
```

**BLOCKING: Do NOT proceed with removal until user confirms action for each worktree with uncommitted work.**

---

### Step 4: Get User Confirmation

**Invoke handler to generate display:**

```json
{
  "handler": "cleanup",
  "context": {
    "phase": "plan",
    "locks_to_remove": ["<task-id>"],
    "worktrees_to_remove": [{"path": "<path>", "branch": "<branch>"}],
    "branches_to_remove": ["<branch-name>"],
    "stale_remotes": [{"branch": "<name>", "staleness": "<info>"}]
  }
}
```

Output the PRE-COMPUTED display exactly as provided. Example:

```
╭─ 🧹 Cleanup Plan ───────────────────────────────────╮
│ 🔒 Locks to Remove:                                 │
│    • 2.0-task                                       │
│                                                     │
│ 📁 Worktrees to Remove:                             │
│    • /workspace/.worktrees/task → 2.0-task-branch   │
│                                                     │
│ 🌿 Branches to Remove:                              │
│    • 2.0-task-branch                                │
│                                                     │
│ ⏳ Stale Remotes (report only):                     │
│    • origin/old: 3 days idle                        │
╰─────────────────────────────────────────────────────╯

Total items to remove: 3

Confirm cleanup? (yes/no)
```

**BLOCKING: Do NOT execute cleanup without explicit user confirmation.**

---

### Step 5: Execute Cleanup

Execute in strict order. Errors should propagate - do not suppress with `|| true`.

**Order matters:**
1. Stale locks first (may be blocking worktree operations)
2. Worktrees second (git won't delete branch checked out in worktree)
3. Branches third (after worktrees released them)
4. Context files last

**Remove stale locks:**
```bash
task_id="<from-plan>"
"${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" force-release "$task_id"
```

**Remove worktrees:**
```bash
WORKTREE_PATH="<from-plan>"
git worktree remove "$WORKTREE_PATH" --force
```

**Remove orphaned branches:**
```bash
BRANCH_NAME="<from-plan>"
git branch -D "$BRANCH_NAME"
```

**Remove context file (if applicable):**
```bash
rm .cat-execution-context
```

Report each action:

```
## Cleanup Progress

- [x] Removed lock: <task-id>
- [x] Removed worktree: <path>
- [x] Removed branch: <branch>
- [x] Removed context file
```

---

### Step 6: Verify Cleanup

Run verification commands:

```bash
echo "Remaining worktrees:"
git worktree list

echo "Remaining CAT branches:"
git branch -a | grep -E '(release/|worktree|[0-9]+\.[0-9]+-)' || echo "None"

echo "Remaining locks:"
if [[ -d .claude/cat/locks ]]; then
  ls .claude/cat/locks/*.lock 2>/dev/null || echo "None"
else
  echo "None"
fi
```

**Invoke handler to generate display:**

```json
{
  "handler": "cleanup",
  "context": {
    "phase": "verify",
    "remaining_worktrees": ["<path>"],
    "remaining_branches": ["<branch>"],
    "remaining_locks": ["<lock-id>"],
    "removed_counts": {"locks": <n>, "worktrees": <n>, "branches": <n>}
  }
}
```

Output the PRE-COMPUTED display exactly as provided. Example:

```
╭─ ✅ Cleanup Complete ───────────────────────────────╮
│ Removed:                                            │
│    • 1 lock(s)                                      │
│    • 1 worktree(s)                                  │
│    • 1 branch(es)                                   │
│                                                     │
│ 📁 Remaining Worktrees:                             │
│    • /workspace (main)                              │
│                                                     │
│ 🌿 Remaining CAT Branches:                          │
│    (none)                                           │
│                                                     │
│ 🔒 Remaining Locks:                                 │
│    (none)                                           │
╰─────────────────────────────────────────────────────╯
```

---

## Common Scenarios

### Session crashed mid-execution

**Symptoms:** Lock file exists, worktree may have partial work

**Action:**
1. Check worktree for uncommitted changes (Step 3)
2. Offer to commit, stash, or discard
3. Remove lock and worktree after user confirms

### User cancelled and wants fresh start

**Symptoms:** Multiple stale worktrees and lock files

**Action:**
1. Survey all artifacts (Step 1)
2. Confirm cleanup of each (Step 4)
3. Remove all confirmed artifacts (Step 5)

### Lock file blocking new execution

**Symptoms:** "Task locked by another session" error but no active session

**Action:**
1. Identify specific lock via survey
2. Confirm no active work in associated worktree
3. Force-release the specific lock

### Orphaned branches after worktree removal

**Symptoms:** Branches exist but no worktrees reference them

**Action:**
1. List branches (Step 1)
2. Confirm they have no unique unmerged commits
3. Delete branches
