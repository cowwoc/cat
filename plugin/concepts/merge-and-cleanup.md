<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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

For each subagent branch (subagent branches within an issue worktree may use --ff-only):
```bash
# In issue worktree
git checkout {major}.{minor}-{issue-name}
git merge --ff-only {subagent-branch}
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

Use `/cat:git-squash` skill which uses `commit-tree` for safe squashing.

**NEVER use `git rebase -i`** (requires interactive input) or manual `git reset --soft`
(captures stale working directory state, can revert fixes from base branch — see M385).

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

**CRITICAL: STATE.md must be in same commit as implementation.**

Before squashing/merging, update issue STATE.md to closed in the issue branch:

```bash
# In issue worktree - update STATE.md
# .claude/cat/issues/v{major}/v{major}.{minor}/{issue-name}/STATE.md:
#   status: closed
#   progress: 100%
#   completed: {date}

# Include in implementation commit
git add .claude/cat/issues/v{major}/v{major}.{minor}/{issue-name}/STATE.md
git commit --amend --no-edit
```

### 7. Merge to Main

After approval and STATE.md update, use the `merge-and-cleanup` Java tool which performs a
fast-forward-only merge:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/merge-and-cleanup" \
  "${CLAUDE_PROJECT_DIR}" "${ISSUE_ID}" "${SESSION_ID}" --worktree "${WORKTREE_PATH}"
```

**Linear history is mandatory.** Merge commits are prohibited. The `merge-and-cleanup` tool
enforces fast-forward-only merging. If fast-forward is not possible, rebase the issue branch
onto the base branch first (see Step 3 of work-merge-first-use skill).

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

# Merge task branch using fast-forward only (enforces linear history)
git merge --ff-only {issue-branch}
# If fast-forward not possible, rebase the issue branch first:
#   git rebase v2.1  (in the worktree), then retry --ff-only
```

**Why this works:**
- Main workspace stays on base branch for stability
- Worktrees are for task branches
- No checkout needed when base is already active

### 8. Worktree Cleanup

**CRITICAL: Change directory to main workspace BEFORE removing worktree.**

The shell's current working directory persists between Bash tool calls. If cwd is inside the
worktree being removed, the deletion will succeed but leave the shell in an invalid state
where all subsequent commands fail silently.

```bash
# ALWAYS change to main workspace first
cd /workspace

# Then remove the worktree
git worktree remove .claude/cat/worktrees/{issue-name}
```

If worktree removal fails:
```bash
cd /workspace
git worktree remove --force .claude/cat/worktrees/{issue-name}
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

- Minor STATE.md: recalculate progress based on closed issues
- Major STATE.md: recalculate progress based on closed minor versions
- ROADMAP.md: update if version status changed

### 11. Update Changelogs

Update minor/major CHANGELOG.md to include closed issue summary.

> **NOTE**: Issue changelog content is embedded in commit messages, not separate files.
> Minor/major version CHANGELOG.md files aggregate closed issues.

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

### CRITICAL: Worktree Location During Error Recovery

**When handling errors (especially merge conflicts), verify you're in the correct worktree:**

```bash
# BEFORE any error recovery:
pwd
git branch --show-current
```

**Expected:** Issue worktree (`/workspace/.claude/cat/worktrees/<issue>`) on issue branch
**WRONG:** Main worktree (`/workspace`) on main branch

**Common mistake:** After conflict resolution attempts (abort, reset), agent ends up in main
worktree and makes edits there. This corrupts main and other parallel issues.

**Recovery if in wrong location:**
```bash
# Verify current location
pwd

# If in wrong location, cd to the correct directory
cd /workspace/.claude/cat/worktrees/<issue-name>
git status
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
