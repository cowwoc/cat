---
name: git-merge-linear
description: Merge task branch to main with linear history and pre/post-merge verification
allowed-tools: Bash, Read
---

# Git Linear Merge Skill

**When to Use**:
- After task branch has passed review
- When merging completed task to main branch
- To maintain clean, linear git history

## Prerequisites

Before using this skill, verify:
- [ ] Task branch has exactly 1 commit (squashed)
- [ ] All quality checks pass (build, tests, checkstyle, PMD)
- [ ] User approval obtained for changes
- [ ] Working directory is clean

## Skill Workflow

### Step 1: Validation

**Verify Task Branch State**:
```bash
# Ensure we're on main branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  echo "ERROR: Must be on main branch to merge"
  echo "Current branch: $CURRENT_BRANCH"
  exit 1
fi

# Verify task branch exists
TASK_BRANCH="<task-name>"
if ! git rev-parse --verify "$TASK_BRANCH" >/dev/null 2>&1; then
  echo "ERROR: Task branch '$TASK_BRANCH' not found"
  exit 1
fi

# Count commits on task branch
COMMIT_COUNT=$(git rev-list --count main.."$TASK_BRANCH")
echo "Task branch has $COMMIT_COUNT commit(s)"

if [[ "$COMMIT_COUNT" -ne 1 ]]; then
  echo "ERROR: Task branch must have exactly 1 commit"
  echo "Found: $COMMIT_COUNT commits"
  echo ""
  echo "SOLUTION: Squash commits first using:"
  echo "  git checkout $TASK_BRANCH"
  echo "  git rebase -i main"
  exit 1
fi

echo "Task branch ready for merge"
```

### Step 2: Fast-Forward Merge

**Execute Linear Merge**:
```bash
# Merge with --ff-only to ensure linear history
git merge --ff-only "$TASK_BRANCH"

if [[ $? -ne 0 ]]; then
  echo "ERROR: Fast-forward merge failed"
  echo ""
  echo "This usually means main has moved ahead since task branch was created."
  echo ""
  echo "SOLUTION: Rebase task branch onto latest main:"
  echo "  git merge --abort  # Cancel this merge"
  echo "  git checkout $TASK_BRANCH"
  echo "  git rebase main"
  echo "  git checkout main"
  echo "  git merge --ff-only $TASK_BRANCH"
  exit 1
fi

echo "Linear merge successful"
```

### Step 3: Verification

**Verify Linear History**:
```bash
# Check that history is linear (no merge commits)
git log --oneline --graph -5

# Verify the task commit is now on main
LATEST_COMMIT=$(git log -1 --format=%s)
echo "Latest commit on main: $LATEST_COMMIT"

# Confirm no merge commit created
if git log -1 --format=%p | grep -q " "; then
  echo "WARNING: Merge commit detected! History is not linear."
  echo "This should not happen with --ff-only"
  exit 1
fi

echo "Linear history verified"
```

### Step 4: Cleanup (Optional)

**Remove Worktree and Delete Branch**:
```bash
# IMPORTANT: Remove worktree BEFORE deleting branch
# Git prevents deleting a branch that's checked out in an active worktree
TASK_WORKTREE="/path/to/tasks/$TASK_BRANCH/code"
if [[ -d "$TASK_WORKTREE" ]]; then
  git worktree remove "$TASK_WORKTREE"
  rm -rf "/path/to/tasks/$TASK_BRANCH"
  echo "Task worktree cleaned up"
fi

# Delete task branch (after worktree removal)
git branch -d "$TASK_BRANCH"
echo "Task branch deleted"
```

## Complete Workflow Script

```bash
#!/bin/bash
set -euo pipefail

TASK_BRANCH="$1"

if [[ -z "$TASK_BRANCH" ]]; then
  echo "Usage: merge-linear <task-branch-name>"
  exit 1
fi

echo "=== Linear Merge: $TASK_BRANCH -> main ==="
echo ""

# Step 1: Validation
echo "Step 1: Validating task branch..."
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  echo "ERROR: Must be on main branch (currently on: $CURRENT_BRANCH)"
  exit 1
fi

if ! git rev-parse --verify "$TASK_BRANCH" >/dev/null 2>&1; then
  echo "ERROR: Branch '$TASK_BRANCH' not found"
  exit 1
fi

COMMIT_COUNT=$(git rev-list --count main.."$TASK_BRANCH")
if [[ "$COMMIT_COUNT" -ne 1 ]]; then
  echo "ERROR: Task branch has $COMMIT_COUNT commits, need exactly 1"
  echo "Run: git checkout $TASK_BRANCH && git rebase -i main"
  exit 1
fi

echo "Validation passed"
echo ""

# Step 2: Fast-Forward Merge
echo "Step 2: Merging with --ff-only..."
if ! git merge --ff-only "$TASK_BRANCH"; then
  echo ""
  echo "ERROR: Fast-forward failed. Main has likely moved ahead."
  echo "Rebase required:"
  echo "  git merge --abort"
  echo "  git checkout $TASK_BRANCH && git rebase main"
  echo "  git checkout main && git merge --ff-only $TASK_BRANCH"
  exit 1
fi

echo "Merge successful"
echo ""

# Step 3: Verification
echo "Step 3: Verifying linear history..."
git log --oneline --graph -3

# Check for merge commit
if git log -1 --format=%p | grep -q " "; then
  echo "ERROR: Merge commit detected! This should not happen."
  exit 1
fi

echo "Linear history confirmed"
echo ""

# Step 4: Cleanup (Optional - ask user)
echo "Cleanup task branch? (y/n)"
read -r CLEANUP
if [[ "$CLEANUP" == "y" ]]; then
  # IMPORTANT: Remove worktree BEFORE deleting branch
  # Git prevents deleting a branch that's checked out in an active worktree
  TASK_WORKTREE="/path/to/tasks/$TASK_BRANCH/code"
  if [[ -d "$TASK_WORKTREE" ]]; then
    git worktree remove "$TASK_WORKTREE" 2>/dev/null || true
    rm -rf "/path/to/tasks/$TASK_BRANCH"
    echo "Task worktree removed"
  fi

  git branch -d "$TASK_BRANCH"
  echo "Task branch deleted"
fi

echo ""
echo "=== Linear merge complete ==="
```

## Usage Examples

### Standard Merge

```bash
# Ensure you're on main branch
git checkout main

# Execute merge
# Follow the step-by-step workflow in this skill
```

### With Validation Only
```bash
# Just validate without merging
git rev-list --count main..{major}.{minor}-{task-name}
# Should output: 1
```

### Handling Rebase
```bash
# If fast-forward fails
git checkout {major}.{minor}-{task-name}
git rebase main
git checkout main
git merge --ff-only {major}.{minor}-{task-name}
```

## Common Issues

### Issue 1: "Not possible to fast-forward"
**Cause**: Main branch has moved ahead since task branch was created
**Solution**: Rebase task branch onto main first

### Issue 2: "Task branch has multiple commits"
**Cause**: Forgot to squash commits
**Solution**: Use interactive rebase to squash all commits into one

### Issue 3: "Merge commit created despite --ff-only"
**Cause**: This should never happen with --ff-only
**Solution**: If it does, this is a bug - report immediately

## Success Criteria

- Task branch merged to main
- History remains linear (no merge commits)
- Exactly 1 commit added to main
- All validations passed
- Task branch optionally cleaned up
