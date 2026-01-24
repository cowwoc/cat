---
name: cat:git-merge-linear
description: Merge task branch to base branch with linear history (works from task worktree)
allowed-tools: Bash, Read
---

# Git Linear Merge Skill

Merge task branch to its base branch while staying in the task worktree. Uses `git push . HEAD:<base>`
to fast-forward the base branch without checking out.

## When to Use

- After task branch has passed review and user approval
- When merging completed task to base branch (main, v1.10, etc.)
- To maintain clean, linear git history

## Prerequisites

- [ ] User approval obtained
- [ ] Working directory is clean (commit or stash changes)
- [ ] You are in the task worktree (not main repo)

## Workflow

### Step 1: Verify Location and Detect Base Branch

```bash
# Verify we're in a worktree (not main repo)
WORKTREE_PATH=$(pwd)
MAIN_REPO=$(git worktree list | head -1 | awk '{print $1}')

if [[ "$WORKTREE_PATH" == "$MAIN_REPO" ]]; then
  echo "ERROR: Must run from task worktree, not main repo"
  echo "Navigate to: /workspace/.worktrees/<task-name>"
  exit 1
fi

# Get current branch
TASK_BRANCH=$(git rev-parse --abbrev-ref HEAD)

# Detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: cat-base file not found: $CAT_BASE_FILE"
  echo "This worktree was not created properly. Recreate with /cat:work."
  echo "Or set manually: echo '<base-branch>' > \"$CAT_BASE_FILE\""
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

echo "Task branch: $TASK_BRANCH"
echo "Base branch: $BASE_BRANCH"
echo "Worktree: $WORKTREE_PATH"

# Check for uncommitted changes
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "ERROR: Uncommitted changes detected"
  echo "Commit or stash changes before merging"
  exit 1
fi
```

### Step 2: Check for Base Branch Divergence (MANDATORY - M199)

**CRITICAL: Squashing without this check can delete commits added to base after worktree creation.**

```bash
# Check if base branch has commits not in our history
DIVERGED_COMMITS=$(git rev-list --count "HEAD..${BASE_BRANCH}")

if [[ "$DIVERGED_COMMITS" -gt 0 ]]; then
  echo "ERROR: Base branch has diverged!"
  echo ""
  echo "$BASE_BRANCH has $DIVERGED_COMMITS commit(s) not in your branch."
  echo "These commits would be LOST if you squash now."
  echo ""
  echo "Commits on $BASE_BRANCH not in HEAD:"
  git log --oneline "HEAD..${BASE_BRANCH}"
  echo ""
  echo "Solution: Rebase onto $BASE_BRANCH first:"
  echo "  git rebase $BASE_BRANCH"
  echo "  # Then retry merge"
  exit 1
fi

echo "Base branch has not diverged - safe to proceed"
```

### Step 2b: Check for Base File Deletions (MANDATORY - M233)

**CRITICAL: Even after rebase, incorrect conflict resolution can delete base branch files.**

```bash
# Check if task branch deletes files that exist in base branch
DELETED_FILES=$(git diff --name-status "${BASE_BRANCH}..HEAD" | grep "^D" | cut -f2)

if [[ -n "$DELETED_FILES" ]]; then
  echo "WARNING: Task branch deletes files from base branch:"
  echo "$DELETED_FILES"
  echo ""

  # Check if these are intentional deletions or rebase artifacts
  # Files in .claude/cat/ or plugin/ directories are likely unintentional
  SUSPICIOUS=$(echo "$DELETED_FILES" | grep -E "^(\.claude/cat/|plugin/)" || true)

  if [[ -n "$SUSPICIOUS" ]]; then
    echo "ERROR: Suspicious deletions detected in infrastructure paths:"
    echo "$SUSPICIOUS"
    echo ""
    echo "These deletions are likely from incorrect rebase conflict resolution."
    echo ""
    echo "Solution: Re-rebase with correct conflict resolution:"
    echo "  git reset --hard origin/${TASK_BRANCH}  # If remote has clean state"
    echo "  # Or reset to merge-base and cherry-pick task commits"
    echo "  git checkout ${BASE_BRANCH}"
    echo "  git checkout -B ${TASK_BRANCH}"
    echo "  # Then cherry-pick your actual task commits"
    exit 1
  fi

  echo "Deletions appear intentional (not in infrastructure paths)"
fi

echo "No suspicious file deletions - safe to proceed"
```

### Step 3: Squash Commits (if needed)

```bash
# Count commits ahead of base branch
COMMIT_COUNT=$(git rev-list --count "${BASE_BRANCH}..HEAD")
echo "Commits to merge: $COMMIT_COUNT"

if [[ "$COMMIT_COUNT" -eq 0 ]]; then
  echo "ERROR: No commits to merge"
  exit 1
fi

if [[ "$COMMIT_COUNT" -gt 1 ]]; then
  echo "Squashing $COMMIT_COUNT commits into 1..."

  # Get combined commit message from all commits
  COMBINED_MSG=$(git log --reverse --format="- %s" "${BASE_BRANCH}..HEAD")
  FIRST_MSG=$(git log -1 --format="%s" "${BASE_BRANCH}..HEAD" | head -1)

  # Soft reset to base and create single commit
  git reset --soft "$BASE_BRANCH"
  git commit -m "$FIRST_MSG

Changes:
$COMBINED_MSG

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"

  echo "Squashed into 1 commit"
fi
```

### Step 4: Fast-Forward Base Branch (from worktree)

```bash
# Fast-forward base branch to current HEAD without checking out
# This updates the base branch ref to point to our current commit
git push . "HEAD:${BASE_BRANCH}"

if [[ $? -ne 0 ]]; then
  echo "ERROR: Fast-forward failed"
  echo ""
  echo "This usually means $BASE_BRANCH has moved ahead."
  echo "Solution: Rebase onto $BASE_BRANCH first:"
  echo "  git fetch origin $BASE_BRANCH"
  echo "  git rebase origin/$BASE_BRANCH"
  echo "  # Then retry merge"
  exit 1
fi

echo "$BASE_BRANCH fast-forwarded to $(git rev-parse --short HEAD)"
```

### Step 5: Verify Merge

```bash
# Verify base branch was updated
BASE_SHA=$(git rev-parse "$BASE_BRANCH")
HEAD_SHA=$(git rev-parse HEAD)

if [[ "$BASE_SHA" != "$HEAD_SHA" ]]; then
  echo "ERROR: $BASE_BRANCH not at expected commit"
  echo "$BASE_BRANCH: $BASE_SHA"
  echo "HEAD: $HEAD_SHA"
  exit 1
fi

echo "Verified: $BASE_BRANCH is at $(git rev-parse --short "$BASE_BRANCH")"

# Show final state
echo ""
echo "=== Merge Complete ==="
git log --oneline -3 "$BASE_BRANCH"
```

### Step 6: Cleanup Worktree and Branch

```bash
# Navigate to main repo for cleanup
cd "$MAIN_REPO"

# Remove worktree
git worktree remove "$WORKTREE_PATH" --force 2>/dev/null || true

# Delete task branch (now safe since worktree removed)
git branch -D "$TASK_BRANCH" 2>/dev/null || true

# Clean up empty worktrees directory
rmdir /workspace/.worktrees 2>/dev/null || true

echo "Cleanup complete"
```

## Single Command Version

For experienced users, combine all steps (run from task worktree):

```bash
# Detect branches and paths
TASK_BRANCH=$(git rev-parse --abbrev-ref HEAD)
MAIN_REPO=$(git worktree list | head -1 | awk '{print $1}')
WORKTREE_PATH=$(pwd)

# Detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: cat-base file not found. Recreate worktree with /cat:work." >&2
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

# Check for divergence FIRST (M199)
DIVERGED=$(git rev-list --count "HEAD..${BASE_BRANCH}")
if [[ "$DIVERGED" -gt 0 ]]; then
  echo "ERROR: Base branch has $DIVERGED commit(s) not in HEAD. Rebase first." >&2
  exit 1
fi

# Check for suspicious file deletions (M233)
SUSPICIOUS_DELETIONS=$(git diff --name-status "${BASE_BRANCH}..HEAD" | grep "^D" | cut -f2 | grep -E "^(\.claude/cat/|plugin/)" || true)
if [[ -n "$SUSPICIOUS_DELETIONS" ]]; then
  echo "ERROR: Task branch deletes infrastructure files from base:" >&2
  echo "$SUSPICIOUS_DELETIONS" >&2
  echo "Likely incorrect rebase conflict resolution. Re-rebase with correct resolution." >&2
  exit 1
fi

# Squash if multiple commits
COMMIT_COUNT=$(git rev-list --count "${BASE_BRANCH}..HEAD")
if [[ "$COMMIT_COUNT" -gt 1 ]]; then
  FIRST_MSG=$(git log --format="%s" "${BASE_BRANCH}..HEAD" | tail -1)
  git reset --soft "$BASE_BRANCH"
  git commit -m "$FIRST_MSG"
fi

# Fast-forward base branch
git push . "HEAD:${BASE_BRANCH}"

# Cleanup
cd "$MAIN_REPO"
git worktree remove "$WORKTREE_PATH" --force 2>/dev/null
git branch -D "$TASK_BRANCH" 2>/dev/null
```

## Common Issues

### Issue 1: "failed to push some refs"
**Cause**: Base branch has moved ahead since task branch was created
**Solution**: Rebase task branch onto base first:
```bash
git fetch origin "$BASE_BRANCH"
git rebase "origin/$BASE_BRANCH"
# Then retry: git push . "HEAD:${BASE_BRANCH}"
```

### Issue 2: "not a valid ref"
**Cause**: Branch name has special characters or doesn't exist
**Solution**: Verify branch name with `git branch -a`

### Issue 3: Worktree removal fails
**Cause**: Uncommitted changes or process using directory
**Solution**: Use `--force` flag or manually clean up

### Issue 4: Wrong base branch detected
**Cause**: Base branch detection failed (worktree metadata missing)
**Solution**: Set explicitly with `echo "<base-branch>" > "$(git rev-parse --git-dir)/cat-base"`

## Success Criteria

- [ ] Base branch points to task commit
- [ ] Linear history maintained (no merge commits)
- [ ] Task worktree removed
- [ ] Task branch deleted
