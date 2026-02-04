# Workflow: Merge and Cleanup

## Overview
Post-issue workflow for merging completed work and cleaning up worktrees.

## Prerequisites
- Issue execution completed
- Subagent branches ready for merge
- Approval received (interactive mode)

## Workflow Steps

### 1. Collect Subagent Branches

Identify all subagent branches for the issue:
```
{major}.{minor}-{issue-name}-sub-*
```

### 2. Sequential Subagent Merge

For each subagent branch:
```bash
# In issue worktree
git checkout {major}.{minor}-{issue-name}
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
## Ready for Merge: {issue-name}

**Branch:** {major}.{minor}-{issue-name}
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

### 6. Update Issue STATE.md (BEFORE merge)

**CRITICAL (M070): STATE.md must be in same commit as implementation.**

Before squashing/merging, update issue STATE.md to completed in the issue branch:

```bash
# In issue worktree - update STATE.md
# .claude/cat/issues/v{major}/v{major}.{minor}/{issue-name}/STATE.md:
#   status: completed
#   progress: 100%
#   completed: {date}

# Include in implementation commit
git add .claude/cat/issues/v{major}/v{major}.{minor}/{issue-name}/STATE.md
git commit --amend --no-edit
```

### 7. Merge to Main

After approval and STATE.md update:
```bash
# Ensure main is up to date
git checkout main
git pull origin main

# Merge issue branch (STATE.md already included)
git merge {major}.{minor}-{issue-name} --no-ff

# Push to remote
git push origin main
```

#### Merging When Base Branch Is Checked Out in Main Workspace

**Special case:** When working from a worktree and the base branch is already checked out in the main workspace.

**Don't attempt checkout:**
- M205 blocks checkouts in main worktree to prevent accidental branch switches
- Main workspace maintains base branch (e.g., v2.1) for parallel work coordination

**Correct pattern:**
```bash
# Work from main workspace directly (base branch already checked out)
cd /workspace

# Verify you're on the base branch
git branch --show-current  # Should show base branch (e.g., v2.1)

# Merge task branch without checkout (already on base)
git merge --ff-only {issue-branch}

# If fast-forward not possible, use no-ff merge
git merge {issue-branch} --no-ff
```

**Why this works:**
- Main workspace stays on base branch for stability
- Worktrees are for task branches
- No checkout needed when base is already active

### 8. Worktree Cleanup

**CRITICAL (M324): Change directory to main workspace BEFORE removing worktree.**

The shell's current working directory persists between Bash tool calls. If cwd is inside the
worktree being removed, the deletion will succeed but leave the shell in an invalid state
where all subsequent commands fail silently.

```bash
# ALWAYS change to main workspace first
cd /workspace

# Then remove the worktree
git worktree remove .worktrees/{issue-name}
```

If worktree removal fails:
```bash
cd /workspace
git worktree remove --force .worktrees/{issue-name}
```

### 9. Branch Cleanup

Delete merged branches:
```bash
# Delete issue branch
git branch -d {major}.{minor}-{issue-name}

# Delete all subagent branches for this issue
git branch -d {major}.{minor}-{issue-name}-sub-*
```

### 10. Update Parent State Files

Update minor and major STATE.md progress (issue STATE.md already updated in step 6):

- Minor STATE.md: recalculate progress based on completed issues
- Major STATE.md: recalculate progress based on completed minor versions
- ROADMAP.md: update if version status changed

### 11. Update Changelogs

Update minor/major CHANGELOG.md to include completed issue summary.

> **NOTE**: Issue changelog content is embedded in commit messages, not separate files.
> Minor/major version CHANGELOG.md files aggregate completed issues.

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
git branch --list "{major}.{minor}-{issue-name}*"

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

**Expected:** Issue worktree (`/workspace/.worktrees/<issue>`) on issue branch
**WRONG:** Main worktree (`/workspace`) on main branch

**Common mistake:** After conflict resolution attempts (abort, reset), agent ends up in main
worktree and makes edits there. This corrupts main and other parallel issues.

**Recovery if in wrong location:**
```bash
cd /workspace/.worktrees/<issue-name>  # Return to issue worktree
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
