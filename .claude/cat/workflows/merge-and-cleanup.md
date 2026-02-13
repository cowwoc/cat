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

### 6. Merge to Main

After approval:
```bash
# Ensure main is up to date
git checkout main
git pull origin main

# Merge task branch
git merge {major}.{minor}-{task-name} --no-ff

# Push to remote
git push origin main
```

### 7. Worktree Cleanup

Remove task worktree:
```bash
git worktree remove ../cat-worktree-{task-name}
```

If worktree removal fails:
```bash
git worktree remove --force ../cat-worktree-{task-name}
```

### 8. Branch Cleanup

Delete merged branches:
```bash
# Delete task branch
git branch -d {major}.{minor}-{task-name}

# Delete all subagent branches for this task
git branch -d {major}.{minor}-{task-name}-sub-*
```

### 9. Update State Files

Update task STATE.md:
```markdown
- **Status:** completed
- **Progress:** 100%
- **Last Updated:** {timestamp}
```

Update minor STATE.md progress.
Update major STATE.md progress.
Update ROADMAP.md if needed.

### 10. Update Changelogs

Update minor/major CHANGELOG.md to include completed task summary.

> **NOTE**: Task changelog content is embedded in commit messages, not separate files.
> Minor/major version CHANGELOG.md files aggregate completed tasks.

## Yolo Mode Differences

In Yolo mode, steps 5-6 are automatic:
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

### Merge Conflict
1. Identify conflicting files
2. Show conflict markers to user
3. Request resolution guidance
4. Apply resolution and continue

### Verification Failure
1. Identify failing tests/build
2. Report to user
3. Options: fix in branch, or revert and re-plan

### Cleanup Failure
1. Log worktree/branch that couldn't be removed
2. Continue with other cleanup
3. Report orphaned resources to user
