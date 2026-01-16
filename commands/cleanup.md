---
name: cat:cleanup
description: Clean up abandoned worktrees, lock files, and orphaned branches
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
---

<objective>
Identify and clean up abandoned CAT artifacts: worktrees, lock files, and orphaned branches.

Use this when:
- A previous session crashed or was cancelled
- Lock files are blocking new execution
- Orphaned worktrees are cluttering the filesystem
</objective>

<process>

<step name="survey">
**Survey current state:**

```bash
# List all worktrees
echo "=== Git Worktrees ==="
git worktree list

# List task locks (new format in .claude/cat/locks/)
echo ""
echo "=== Task Locks ==="
if [[ -d .claude/cat/locks ]]; then
  "${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" list 2>/dev/null || \
    ls -la .claude/cat/locks/*.lock 2>/dev/null || echo "No task locks found"
else
  echo "No task locks found"
fi

# List legacy lock files
echo ""
echo "=== Legacy Lock Files ==="
ls -la .cat-*.lock 2>/dev/null || echo "No legacy lock files found"

# List execution context files
echo ""
echo "=== Context Files ==="
ls -la .cat-execution-context 2>/dev/null || echo "No context file found"

# List CAT-related branches
echo ""
echo "=== CAT Branches ==="
git branch -a | grep -E "(release/|worktree|\d+\.\d+-)" || echo "No CAT branches found"
```

Present findings to user.
</step>

<step name="identify_abandoned">
**Identify abandoned artifacts:**

A worktree is likely abandoned if:
- Its lock file references a session that's no longer active
- The worktree directory exists but has no recent activity
- The main project has a stale `.cat-change-*.lock` file

**Check task locks (new format):**
```bash
if [[ -d .claude/cat/locks ]]; then
  echo "=== Task Lock Status ==="
  for lock in .claude/cat/locks/*.lock; do
    [[ ! -f "$lock" ]] && continue
    task_id=$(basename "$lock" .lock)
    status=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" check "$task_id" 2>/dev/null)
    is_stale=$(echo "$status" | jq -r '.stale // false')
    heartbeat_age=$(echo "$status" | jq -r '.heartbeat_age_seconds // 0')
    session=$(echo "$status" | jq -r '.session_id // "unknown"')
    echo "  $task_id: session=$session, heartbeat_age=${heartbeat_age}s, stale=$is_stale"
  done
fi
```

**Check legacy lock files:**
```bash
for lock in .cat-change-*.lock; do
  if [[ -f "$lock" ]]; then
    echo "Lock: $lock"
    echo "  Session: $(cat "$lock")"
    echo "  Age: $(stat -c %y "$lock" 2>/dev/null || stat -f %Sm "$lock")"
  fi
done
```
</step>

<step name="check_uncommitted">
**CRITICAL: Check for uncommitted work before cleanup:**

**MANDATORY: Run git commands from main directory using `-C` flag. NEVER `cd` into a worktree that
will be deleted - this makes the shell unusable after deletion.**

For each worktree to be removed:
```bash
WORKTREE_PATH="<path-from-git-worktree-list>"

# Check for uncommitted changes (use -C flag, NEVER cd into worktree)
if [[ -n "$(git -C "$WORKTREE_PATH" status --porcelain)" ]]; then
  echo "WARNING: Uncommitted changes in $WORKTREE_PATH"
  git -C "$WORKTREE_PATH" status --short
  echo ""
  echo "Options:"
  echo "1. Commit the changes first"
  echo "2. Stash the changes: git -C $WORKTREE_PATH stash"
  echo "3. Discard changes: git -C $WORKTREE_PATH checkout -- . (DESTRUCTIVE)"
  echo "4. Skip this worktree"
  # ASK USER before proceeding
fi
```

**NEVER remove a worktree with uncommitted changes without explicit user approval.**
</step>

<step name="cleanup">
**Perform cleanup (with user confirmation):**

For each abandoned artifact, confirm before removing:

**Clean up stale task locks:**
```bash
# Remove task locks older than 5 minutes without heartbeat
"${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" cleanup --stale-minutes 5

# For emergency cleanup (DANGER: may interrupt active sessions):
# "${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" cleanup --stale-minutes 0
```

**Clean up worktrees:**
```bash
# Remove worktree (MUST be done BEFORE deleting its branch)
WORKTREE_PATH="<path>"
git worktree remove "$WORKTREE_PATH" --force

# Remove associated legacy lock file
CHANGE_ID="<extracted-from-path>"  # e.g., "02-01" from ".worktrees/m1-02-01"
rm -f ".cat-change-${CHANGE_ID}.lock"

# Remove orphaned branch (AFTER worktree removal)
BRANCH_NAME="<branch-from-worktree>"
git branch -D "$BRANCH_NAME" 2>/dev/null || true
```

**Order matters:**
1. Clean up stale task locks FIRST (may be blocking worktree operations)
2. Remove worktree (git won't delete a branch checked out in a worktree)
3. Remove legacy lock files
4. Delete orphaned branches
5. Remove context file last
</step>

<step name="verify">
**Verify cleanup complete:**

```bash
echo "=== Verification ==="

# Confirm no orphaned worktrees
echo "Remaining worktrees:"
git worktree list

# Confirm no stale locks
echo ""
echo "Remaining lock files:"
ls -la .cat-*.lock 2>/dev/null || echo "None"

# Confirm branches cleaned
echo ""
echo "Remaining CAT branches:"
git branch -a | grep -E "(release/|worktree)" || echo "None"

echo ""
echo "Cleanup complete"
```
</step>

</process>

<safety_rules>
- NEVER `cd` into a worktree that will be deleted (use `git -C <path>` instead) - deleting a directory
  you're in makes the shell unusable
- ALWAYS check for uncommitted changes before removing worktrees
- ALWAYS ask user before removing anything with uncommitted work
- ALWAYS remove worktree BEFORE deleting its branch
- NEVER force-delete branches that might have unmerged commits
- List what will be removed and get confirmation before proceeding
</safety_rules>

<common_scenarios>

**Scenario 1: Session crashed mid-execution**
```
Symptoms: Lock file exists, worktree may have partial work
Action: Check worktree for uncommitted changes, offer to commit or discard
```

**Scenario 2: User cancelled and wants fresh start**
```
Symptoms: Multiple stale worktrees and lock files
Action: Survey all, confirm cleanup of each
```

**Scenario 3: Lock file blocking new execution**
```
Symptoms: "Change already being executed" error but no active session
Action: Remove specific lock file after confirming no active work
```

**Scenario 4: Orphaned branches after worktree removal**
```
Symptoms: Branches exist but no worktrees reference them
Action: List branches, confirm they have no unique commits, delete
```

</common_scenarios>
