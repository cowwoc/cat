# Workflow: Merge and Cleanup

## Overview
Post-task workflow for merging completed work and cleaning up worktrees.

## Prerequisites
- Task execution completed
- Subagent branches ready for merge
- Approval received (interactive mode)

## Workflow Steps

### 1. Collect Subagent Branches

Identify all subagent branches for the task:
```
{major}.{minor}-{task-name}-sub-*
```

### 2. Sequential Subagent Merge

For each subagent branch:
```bash
# In task worktree
git checkout {major}.{minor}-{task-name}
git merge {subagent-branch} --no-ff -m "Merge subagent work: {summary}"
```

Handle conflicts if they arise:
- Attempt automatic resolution
- Use 3-way merge for complex conflicts
- Escalate to user if unresolved

### 3. Run Verification

After all subagent merges:
```bash
# Build project
./mvnw clean compile

# Run tests
./mvnw test

# Check for regressions
```

If verification fails:
- Identify failing component
- Escalate or request fix

### 4. Squash Commits by Type

Group and squash commits by conventional commit type:

```bash
# Interactive rebase to organize commits
git rebase -i main
```

Target result:
```
feature: add feature X
bugfix: resolve issue Y
refactor: improve component Z
test: add tests for feature X
docs: update documentation
```

### 5. Approval Gate (Interactive Mode)

Present to user:
```markdown
## Ready for Merge: {task-name}

**Branch:** {major}.{minor}-{task-name}
**Commits:** 3 (1 feature, 1 bugfix, 1 test)
**Files Changed:** 8

### Summary
- Added switch statement parsing
- Fixed edge case with empty blocks
- Added 15 test cases

### Review
To review: `git diff main..{branch}`

**Approve merge to main?**
```

### 6. Update Task STATE.md (BEFORE merge)

**CRITICAL (M070): STATE.md must be in same commit as implementation.**

Before squashing/merging, update task STATE.md to completed in the task branch:

```bash
# In task worktree - update STATE.md
# .claude/cat/v{major}/v{major}.{minor}/{task-name}/STATE.md:
#   status: completed
#   progress: 100%
#   completed: {date}

# Include in implementation commit
git add .claude/cat/v{major}/v{major}.{minor}/{task-name}/STATE.md
git commit --amend --no-edit
```

### 7. Merge to Base Branch

After approval and STATE.md update, merge from within the task worktree (no checkout needed):

```bash
# From task worktree - detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
[[ ! -f "$CAT_BASE_FILE" ]] && echo "ERROR: cat-base file missing. Recreate worktree." && exit 1
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

# Fast-forward base branch to current HEAD
git push . "HEAD:${BASE_BRANCH}"

# Verify merge succeeded
echo "Merged to $BASE_BRANCH: $(git rev-parse --short HEAD)"
```

**Why this approach:**
- No branch checkout required (stays in worktree)
- Maintains linear history (fast-forward only)
- Works with any base branch (main, v1.10, etc.)

See `/cat:git-merge-linear` skill for complete workflow with squashing and cleanup.

### 8. Worktree Cleanup

Remove task worktree:
```bash
git worktree remove ../cat-worktree-{task-name}
```

If worktree removal fails:
```bash
git worktree remove --force ../cat-worktree-{task-name}
```

### 9. Branch Cleanup

Delete merged branches:
```bash
# Delete task branch
git branch -d {major}.{minor}-{task-name}

# Delete all subagent branches for this task
git branch -d {major}.{minor}-{task-name}-sub-*
```

### 10. Update Parent State Files

Update minor and major STATE.md progress (task STATE.md already updated in step 6):

- Minor STATE.md: recalculate progress based on completed tasks
- Major STATE.md: recalculate progress based on completed minor versions
- ROADMAP.md: update if version status changed

### 11. Update Changelogs

Update minor/major CHANGELOG.md to include completed task summary.

> **NOTE**: Task changelog content is embedded in commit messages, not separate files.
> Minor/major version CHANGELOG.md files aggregate completed tasks.

## High Trust Mode Differences

When trust is high, steps 5-7 are automatic:
- No user approval required
- Immediate merge after verification passes
- Cleanup proceeds without pause

## Cleanup Commands Reference

```bash
# List all worktrees
git worktree list

# Remove specific worktree
git worktree remove <path>

# Force remove (if dirty)
git worktree remove --force <path>

# Prune stale worktree references
git worktree prune

# List branches matching pattern
git branch --list "{major}.{minor}-{task-name}*"

# Delete branches
git branch -d <branch-name>
```

## Error Recovery

### CRITICAL: Worktree Location During Error Recovery (M101)

**When handling errors (especially merge conflicts), verify you're in the correct worktree:**

```bash
# BEFORE any error recovery:
pwd
git branch --show-current
```

**Expected:** Task worktree (`/workspace/.worktrees/<task>`) on task branch
**WRONG:** Main worktree (`/workspace`) on main branch

**Common mistake:** After conflict resolution attempts (abort, reset), agent ends up in main
worktree and makes edits there. This corrupts main and other parallel tasks.

**Recovery if in wrong location:**
```bash
cd /workspace/.worktrees/<task-name>  # Return to task worktree
pwd  # Verify
```

### Merge Conflict
1. **Verify worktree location first** (`pwd` should NOT be `/workspace`)
2. Identify conflicting files
3. Show conflict markers to user
4. Request resolution guidance
5. Apply resolution and continue

### Verification Failure
1. Identify failing tests/build
2. Report to user
3. Options: fix in branch, or revert and re-plan

### Cleanup Failure
1. Log worktree/branch that couldn't be removed
2. Continue with other cleanup
3. Report orphaned resources to user
