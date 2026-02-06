---
description: Merge subagent branch into issue branch with conflict resolution and cleanup
user-invocable: false
---

# Merge Subagent

## Purpose

Integrate a completed subagent's work back into the parent issue branch. Handles the merge process,
resolves conflicts if necessary, cleans up the subagent branch and worktree, and updates tracking
state.

## When to Use

- After `collect-results` confirms subagent work is ready
- Subagent has completed its issue successfully
- Partial results from interrupted subagent need preservation
- Ready to consolidate subagent work into main issue flow

## Concurrent Execution Safety

This skill operates under the issue lock held by `/cat:work`. Refresh the lock heartbeat for
long-running merge operations:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" heartbeat "${CLAUDE_PROJECT_DIR}" "$ISSUE_ID" "${CLAUDE_SESSION_ID}"
```

The issue lock is released by `work` finalization step after all subagent work is merged.

## Workflow

### 1. Verify Prerequisites

```bash
SUBAGENT_ID="a1b2c3d4"
ISSUE="1.2-implement-parser"
SUBAGENT_BRANCH="${ISSUE}-sub-${SUBAGENT_ID}"
ISSUE_BRANCH="${ISSUE}"
WORKTREE=".claude/cat/worktrees/${SUBAGENT_BRANCH}"

# Verify subagent results collected
# Check parent STATE.md for ready_for_merge: true

# Verify issue branch exists
git branch --list "${ISSUE_BRANCH}"

# Verify subagent branch exists
git branch --list "${SUBAGENT_BRANCH}"
```

### 2. Checkout Issue Branch

```bash
# Return to main workspace (not worktree)
cd /workspace

# Ensure clean working state
git status --porcelain
# Should be empty or only untracked files

# Checkout issue branch
git checkout "${ISSUE_BRANCH}"

# Ensure up to date
git pull origin "${ISSUE_BRANCH}" 2>/dev/null || true
```

### 3. Merge Subagent Branch

```bash
# Attempt merge
git merge "${SUBAGENT_BRANCH}" --no-edit -m "Merge subagent ${SUBAGENT_ID}: ${ISSUE}"
```

### 4. Handle Conflicts (If Any)

**If merge fails with conflicts that cannot be auto-resolved: FAIL immediately.**

```bash
CONFLICT_COUNT=$(git diff --name-only --diff-filter=U | wc -l)
if [ "$CONFLICT_COUNT" -gt 3 ]; then
  echo "ERROR: Too many conflicts ($CONFLICT_COUNT files)"
  echo ""
  echo "This indicates significant divergence between issue and subagent branches."
  echo "Manual intervention required - do NOT attempt bulk resolution."
  echo ""
  echo "Options:"
  echo "1. Review each conflict manually with user"
  echo "2. Abort merge and investigate branch history"
  exit 1
fi
```

If merge fails with conflicts:

```bash
# List conflicted files
git diff --name-only --diff-filter=U

# For each conflict, resolve:
# Option A: Accept subagent version (theirs)
git checkout --theirs path/to/file.java

# Option B: Accept issue branch version (ours)
git checkout --ours path/to/file.java

# Option C: Manual resolution
# Edit file to resolve conflicts, then:
git add path/to/file.java

# Complete merge
git commit -m "Merge subagent ${SUBAGENT_ID} with conflict resolution"
```

**Conflict Resolution Strategy:**
1. **Code files**: Prefer subagent version (they have fresher context)
2. **Config files**: Manual merge required
3. **State files**: Merge metadata, keep both contributions
4. **Tests**: Include all tests from both branches

### 5. Verify Merge Success

```bash
# Check merge completed
git log -1 --format="%s"
# Should show merge commit

# Verify no uncommitted changes
git status --porcelain

# Run quick validation (if applicable)
./gradlew compileJava 2>/dev/null || true
```

### 6. Delete Subagent Branch

```bash
# Delete local branch
git branch -d "${SUBAGENT_BRANCH}"

# Delete remote branch if pushed
git push origin --delete "${SUBAGENT_BRANCH}" 2>/dev/null || true
```

### 7. Cleanup Subagent Worktree

```bash
# Remove worktree
git worktree remove "${WORKTREE}" --force

# Verify removal
git worktree list | grep -v "${SUBAGENT_BRANCH}"

# Clean up any remaining directory
rm -rf "${WORKTREE}" 2>/dev/null || true
```

### 8. Update Parent STATE.md

```yaml
subagents:
  - id: a1b2c3d4
    issue: 1.2-implement-parser
    status: merged  # Final state
    merged_at: 2026-01-10T15:30:00Z
    merge_commit: abc123def456
    conflicts_resolved: 0
    worktree_cleaned: true
    branch_deleted: true
```

## Examples

### Clean Merge (No Conflicts)

```bash
# Full merge flow
git checkout 1.2-implement-parser
git merge 1.2-implement-parser-sub-a1b2c3d4 -m "Merge subagent a1b2c3d4"
git branch -d 1.2-implement-parser-sub-a1b2c3d4
git worktree remove .claude/cat/worktrees/1.2-implement-parser-sub-a1b2c3d4

# Result: Clean linear history with merge commit
```

### Merge with Conflicts

```bash
# Merge attempt
git merge 1.2-implement-parser-sub-a1b2c3d4
# CONFLICT (content): Merge conflict in src/Parser.java

# Resolve: prefer subagent's implementation
git checkout --theirs src/Parser.java
git add src/Parser.java

# Complete
git commit -m "Merge subagent a1b2c3d4, resolved Parser.java conflict"
git branch -d 1.2-implement-parser-sub-a1b2c3d4
git worktree remove .claude/cat/worktrees/1.2-implement-parser-sub-a1b2c3d4
```

### Merge Multiple Subagents

```bash
# Merge in dependency order
for subagent in "${SUBAGENTS_ORDERED[@]}"; do
  git merge "${subagent}" -m "Merge ${subagent}"
  git branch -d "${subagent}"
done

# Cleanup all worktrees
for worktree in "${WORKTREES[@]}"; do
  git worktree remove "${worktree}" --force
done
```

## Anti-Patterns

### Always collect results before merging

```bash
# ❌ Skipping collection
git merge "${SUBAGENT_BRANCH}"
# No metrics recorded!

# ✅ Collect first
collect-results "${SUBAGENT_ID}"
merge-subagent "${SUBAGENT_ID}"
```

### Include worktree cleanup with merge

```bash
# ❌ Forgetting cleanup
git merge "${SUBAGENT_BRANCH}"
git branch -d "${SUBAGENT_BRANCH}"
# Worktree still exists!

# ✅ Full cleanup
git merge "${SUBAGENT_BRANCH}"
git branch -d "${SUBAGENT_BRANCH}"
git worktree remove "${WORKTREE}"
```

### Understand conflicts before force-resolving

```bash
# ❌ Blindly forcing
git merge -X theirs "${SUBAGENT_BRANCH}"
# May lose parent branch work!

# ✅ Understand conflicts first
git merge "${SUBAGENT_BRANCH}"
# Review conflicts
git diff --name-only --diff-filter=U
# Make informed resolution decisions
```

### Merge to issue branch first (not directly to main)

```bash
# ❌ Merging subagent to main
git checkout main
git merge 1.2-parser-sub-a1b2c3d4
# Wrong! Should go to issue branch first

# ✅ Merge to issue branch
git checkout 1.2-implement-parser
git merge 1.2-parser-sub-a1b2c3d4
# Issue branch later merges to main
```

### Verify merge before deleting branch

```bash
# ❌ Immediate deletion
git branch -D "${SUBAGENT_BRANCH}"  # Forced delete!

# ✅ Verify merge first
git branch -d "${SUBAGENT_BRANCH}"  # Safe delete (fails if not merged)
# Or explicitly verify:
git branch --merged | grep "${SUBAGENT_BRANCH}" && git branch -d "${SUBAGENT_BRANCH}"
```

## Related Skills

- `cat:collect-results` - Must run before merge
- `cat:spawn-subagent` - Creates subagent that will later be merged
- `cat:parallel-execute` - Orchestrates multiple subagent merges
- `cat:monitor-subagents` - Verify subagent complete before merge
