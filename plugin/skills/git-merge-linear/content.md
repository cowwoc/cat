# Git Linear Merge Skill

Merge issue branch to its base branch using WORKTREE_PATH parameter. Uses `git -C` for all operations
to avoid cd into worktree. Fast-forwards base branch without checking out.

## Step 0: Read Git Workflow Preferences

**Check PROJECT.md for configured merge preferences before proceeding.**

```bash
# Check if Git Workflow section exists in PROJECT.md
WORKFLOW_SECTION=$(grep -A30 "^## Git Workflow" .claude/cat/PROJECT.md 2>/dev/null)

if [[ -n "$WORKFLOW_SECTION" ]]; then
  # Check if linear merge is allowed by workflow config
  MERGE_METHOD=$(echo "$WORKFLOW_SECTION" | grep "MUST use" | head -1)

  if echo "$MERGE_METHOD" | grep -qi "merge commit"; then
    echo "⚠️ WARNING: PROJECT.md specifies merge commits, but this skill uses fast-forward."
    echo "Consider using standard 'git merge --no-ff' instead."
    echo ""
    echo "To proceed anyway, continue with this skill."
    echo "To honor PROJECT.md preference, abort and use: git merge --no-ff {branch}"
    # Don't exit - user may choose to override
  fi

  if echo "$MERGE_METHOD" | grep -qi "squash"; then
    echo "⚠️ WARNING: PROJECT.md specifies squash merge, but this skill uses fast-forward."
    echo "Consider using 'git merge --squash' instead."
    echo ""
    # Don't exit - user may choose to override
  fi
fi
```

## When to Use

- After issue branch has passed review and user approval
- When merging completed issue to base branch (main, v1.10, etc.)
- To maintain clean, linear git history

## Prerequisites

- [ ] User approval obtained
- [ ] Working directory is clean (commit or stash changes)
- [ ] WORKTREE_PATH is set to the issue worktree path

## Workflow

### Step 1: Verify Location and Detect Base Branch

```bash
# Verify WORKTREE_PATH is set (required parameter)
if [[ -z "$WORKTREE_PATH" ]]; then
  echo "ERROR: WORKTREE_PATH not set"
  echo "Set to the issue worktree path: /workspace/.claude/cat/worktrees/<issue-name>"
  exit 1
fi

# Detect location, branches, and check for clean state in one block
MAIN_REPO=$(git worktree list | head -1 | awk '{print $1}') &&
  TASK_BRANCH=$(git -C "$WORKTREE_PATH" rev-parse --abbrev-ref HEAD)

if [[ "$WORKTREE_PATH" == "$MAIN_REPO" ]]; then
  echo "ERROR: WORKTREE_PATH points to main repo, not issue worktree"
  echo "Set to: /workspace/.claude/cat/worktrees/<issue-name>"
  exit 1
fi

# Detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git -C "$WORKTREE_PATH" rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: cat-base file not found: $CAT_BASE_FILE"
  echo "This worktree was not created properly. Recreate with /cat:work."
  echo "Or set manually: echo '<base-branch>' > \"$CAT_BASE_FILE\""
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

echo "Issue branch: $TASK_BRANCH | Base branch: $BASE_BRANCH | Worktree: $WORKTREE_PATH"

# Check for uncommitted changes
if ! git -C "$WORKTREE_PATH" diff --quiet || ! git -C "$WORKTREE_PATH" diff --cached --quiet; then
  echo "ERROR: Uncommitted changes detected. Commit or stash before merging."
  exit 1
fi
```

### Step 2: Check for Base Branch Divergence (MANDATORY - M199)

**CRITICAL: Squashing without this check can delete commits added to base after worktree creation.**

```bash
# Check if base branch has commits not in our history
DIVERGED_COMMITS=$(git -C "$WORKTREE_PATH" rev-list --count "HEAD..${BASE_BRANCH}")

if [[ "$DIVERGED_COMMITS" -gt 0 ]]; then
  echo "ERROR: Base branch has diverged!"
  echo ""
  echo "$BASE_BRANCH has $DIVERGED_COMMITS commit(s) not in your branch."
  echo "These commits would be LOST if you squash now."
  echo ""
  echo "Commits on $BASE_BRANCH not in HEAD:"
  git -C "$WORKTREE_PATH" log --oneline "HEAD..${BASE_BRANCH}"
  echo ""
  echo "Solution: Rebase onto $BASE_BRANCH first:"
  echo "  git -C $WORKTREE_PATH rebase $BASE_BRANCH"
  echo "  # Then retry merge"
  exit 1
fi

echo "Base branch has not diverged - safe to proceed"
```

### Step 2b: Check for Base File Deletions (MANDATORY - M233)

**CRITICAL: Even after rebase, incorrect conflict resolution can delete base branch files.**

```bash
# Check if issue branch deletes files that exist in base branch
DELETED_FILES=$(git -C "$WORKTREE_PATH" diff --name-status "${BASE_BRANCH}..HEAD" | grep "^D" | cut -f2)

if [[ -n "$DELETED_FILES" ]]; then
  echo "WARNING: Issue branch deletes files from base branch:"
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
    echo "  git -C $WORKTREE_PATH reset --hard origin/${TASK_BRANCH}  # If remote has clean state"
    echo "  # Or reset to merge-base and cherry-pick issue commits"
    echo "  git -C $WORKTREE_PATH checkout ${BASE_BRANCH}"
    echo "  git -C $WORKTREE_PATH checkout -B ${TASK_BRANCH}"
    echo "  # Then cherry-pick your actual issue commits"
    exit 1
  fi

  echo "Deletions appear intentional (not in infrastructure paths)"
fi

echo "No suspicious file deletions - safe to proceed"
```

### Step 3: Squash Commits (if needed)

```bash
# Count commits ahead of base branch
COMMIT_COUNT=$(git -C "$WORKTREE_PATH" rev-list --count "${BASE_BRANCH}..HEAD")
echo "Commits to merge: $COMMIT_COUNT"

if [[ "$COMMIT_COUNT" -eq 0 ]]; then
  echo "ERROR: No commits to merge"
  exit 1
fi

if [[ "$COMMIT_COUNT" -gt 1 ]]; then
  echo "Squashing $COMMIT_COUNT commits into 1..."

  # Get combined commit message from all commits
  COMBINED_MSG=$(git -C "$WORKTREE_PATH" log --reverse --format="- %s" "${BASE_BRANCH}..HEAD")
  FIRST_MSG=$(git -C "$WORKTREE_PATH" log -1 --format="%s" "${BASE_BRANCH}..HEAD" | head -1)

  # Soft reset to base and create single commit
  git -C "$WORKTREE_PATH" reset --soft "$BASE_BRANCH"
  git -C "$WORKTREE_PATH" commit -m "$FIRST_MSG

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
git -C "$WORKTREE_PATH" push . "HEAD:${BASE_BRANCH}"

if [[ $? -ne 0 ]]; then
  echo "ERROR: Fast-forward failed"
  echo ""
  echo "This usually means $BASE_BRANCH has moved ahead."
  echo "Solution: Rebase onto $BASE_BRANCH first:"
  echo "  git -C $WORKTREE_PATH fetch origin $BASE_BRANCH"
  echo "  git -C $WORKTREE_PATH rebase origin/$BASE_BRANCH"
  echo "  # Then retry merge"
  exit 1
fi

echo "$BASE_BRANCH fast-forwarded to $(git -C "$WORKTREE_PATH" rev-parse --short HEAD)"
```

### Step 5: Verify Merge and Cleanup

```bash
# Verify base branch was updated
BASE_SHA=$(git -C "$WORKTREE_PATH" rev-parse "$BASE_BRANCH") &&
  HEAD_SHA=$(git -C "$WORKTREE_PATH" rev-parse HEAD)

if [[ "$BASE_SHA" != "$HEAD_SHA" ]]; then
  echo "ERROR: $BASE_BRANCH not at expected commit"
  echo "$BASE_BRANCH: $BASE_SHA | HEAD: $HEAD_SHA"
  exit 1
fi

echo "Verified: $BASE_BRANCH is at $(git -C "$WORKTREE_PATH" rev-parse --short "$BASE_BRANCH")"
git -C "$WORKTREE_PATH" log --oneline -3 "$BASE_BRANCH"

# Cleanup: worktree, branch, empty directory (from main repo, not worktree)
git -C "$MAIN_REPO" worktree remove "$WORKTREE_PATH" --force 2>/dev/null || true
git -C "$MAIN_REPO" branch -D "$TASK_BRANCH" 2>/dev/null || true
rmdir /workspace/.claude/cat/worktrees 2>/dev/null || true
echo "Cleanup complete"
```

## Single Command Version

For experienced users, combine all steps (WORKTREE_PATH must be set):

```bash
# Verify WORKTREE_PATH is set
if [[ -z "$WORKTREE_PATH" ]]; then
  echo "ERROR: WORKTREE_PATH not set" >&2
  exit 1
fi

# Detect branches and paths
TASK_BRANCH=$(git -C "$WORKTREE_PATH" rev-parse --abbrev-ref HEAD) &&
  MAIN_REPO=$(git worktree list | head -1 | awk '{print $1}')

# Detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git -C "$WORKTREE_PATH" rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: cat-base file not found. Recreate worktree with /cat:work." >&2
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

# Check for divergence FIRST (M199)
DIVERGED=$(git -C "$WORKTREE_PATH" rev-list --count "HEAD..${BASE_BRANCH}")
if [[ "$DIVERGED" -gt 0 ]]; then
  echo "ERROR: Base branch has $DIVERGED commit(s) not in HEAD. Rebase first." >&2
  exit 1
fi

# Check for suspicious file deletions (M233)
SUSPICIOUS_DELETIONS=$(git -C "$WORKTREE_PATH" diff --name-status "${BASE_BRANCH}..HEAD" | grep "^D" | cut -f2 | grep -E "^(\.claude/cat/|plugin/)" || true)
if [[ -n "$SUSPICIOUS_DELETIONS" ]]; then
  echo "ERROR: Issue branch deletes infrastructure files from base:" >&2
  echo "$SUSPICIOUS_DELETIONS" >&2
  echo "Likely incorrect rebase conflict resolution. Re-rebase with correct resolution." >&2
  exit 1
fi

# Squash if multiple commits
COMMIT_COUNT=$(git -C "$WORKTREE_PATH" rev-list --count "${BASE_BRANCH}..HEAD")
if [[ "$COMMIT_COUNT" -gt 1 ]]; then
  FIRST_MSG=$(git -C "$WORKTREE_PATH" log --format="%s" "${BASE_BRANCH}..HEAD" | tail -1)
  git -C "$WORKTREE_PATH" reset --soft "$BASE_BRANCH"
  git -C "$WORKTREE_PATH" commit -m "$FIRST_MSG"
fi

# Fast-forward base branch
git -C "$WORKTREE_PATH" push . "HEAD:${BASE_BRANCH}"

# Cleanup (from main repo, not worktree)
git -C "$MAIN_REPO" worktree remove "$WORKTREE_PATH" --force 2>/dev/null
git -C "$MAIN_REPO" branch -D "$TASK_BRANCH" 2>/dev/null
```

## Common Issues

### Issue 1: "failed to push some refs"
**Cause**: Base branch has moved ahead since issue branch was created
**Solution**: Rebase issue branch onto base first:
```bash
git -C "$WORKTREE_PATH" fetch origin "$BASE_BRANCH"
git -C "$WORKTREE_PATH" rebase "origin/$BASE_BRANCH"
# Then retry: git -C "$WORKTREE_PATH" push . "HEAD:${BASE_BRANCH}"
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

- [ ] Base branch points to issue commit
- [ ] Linear history maintained (no merge commits)
- [ ] Issue worktree removed
- [ ] Issue branch deleted
