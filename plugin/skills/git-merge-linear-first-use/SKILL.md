---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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
"$(git rev-parse --show-toplevel)/plugin/scripts/git-merge-linear.sh" "$WORKTREE_PATH" "$COMMIT_MSG"
```

The script implements all steps: pin base, check divergence, check deletions, squash commits if needed, verify
merge-base, fast-forward, verify, and cleanup. Outputs JSON on success.

## Result Handling

| Status | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `OK` | Merge completed successfully | Report success with commit hash and files changed |
| `NOT_LINEAR` | History is not linear | Rebase issue branch onto base first, then retry merge |
| `FF_FAILED` | Base branch advanced during merge | Rebase issue branch onto updated base, then retry merge |
| `ERROR: Base branch has diverged` | Base has commits not in issue branch | Rebase onto base before merging. These commits would be LOST if squashed now |
| `ERROR: Suspicious deletions detected` | Issue branch deletes infrastructure files | Likely from incorrect rebase conflict resolution. Re-rebase with correct resolution |
| `ERROR: No commits to merge` | Issue branch is already at base | Nothing to merge |
| `ERROR: Uncommitted changes detected` | Working directory not clean | Commit or stash changes before merging |

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
