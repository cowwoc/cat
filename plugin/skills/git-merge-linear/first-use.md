---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Git Linear Merge Skill

Merge issue branch to its base branch using WORKTREE_PATH parameter. Uses `git -C` for all operations to avoid cd into
worktree. Fast-forwards base branch without checking out.

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

## Script Invocation

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/git-merge-linear" \
  "$TASK_BRANCH" --base "$BASE_BRANCH"
```

The Java tool implements all steps: detect base branch, check divergence, check suspicious deletions, verify
merge-base, fast-forward merge, cleanup (optional). Outputs JSON on success.

## Result Handling

On success, the tool prints JSON to stdout (exit code 0) with `"status": "success"`. On failure, it prints a JSON error
to stderr (exit code 1) with `"status": "error"` and a `"message"` field containing the error description.

| Output | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `"status": "success"` (stdout) | Merge completed successfully | Report `commit_sha` and `task_branch`, continue |
| `"status": "error"`: Must be on {base} branch | Wrong branch checked out | `git checkout {base_branch}` first |
| `"status": "error"`: Working directory is not clean | Uncommitted changes | Commit or stash changes before merging |
| `"status": "error"`: Task branch must have exactly 1 commit | Multiple commits | Squash commits first |
| `"status": "error"`: Task branch is behind {base} | Base has commits not in issue branch | Rebase onto base before merging |
| `"status": "error"`: Fast-forward merge failed | History diverged | Rebase issue branch onto base first |
| `"status": "error"`: Merge commit detected | Non-linear history after merge | Investigate merge state, should not occur with ff-only |
| `"status": "error"`: cat-base file not found | Cannot auto-detect base branch | Pass `--base {branch}` explicitly or recreate worktree |

## Common Issues

### Issue 1: "failed to push some refs"
**Cause**: Base branch has moved ahead since issue branch was created
**Solution**: Rebase issue branch onto base first:
```bash
git -C "$WORKTREE_PATH" fetch origin "$BASE_BRANCH"
git -C "$WORKTREE_PATH" rebase "origin/$BASE_BRANCH"
# Then retry merge script
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
