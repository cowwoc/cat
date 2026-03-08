<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Linear Merge Skill

Merge source branch to its target branch using WORKTREE_PATH parameter. Fast-forwards target branch without checking out.

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

- After source branch has passed review and user approval
- When merging completed work to target branch (main, v1.10, etc.)
- To maintain clean, linear git history

## Prerequisites

- [ ] User approval obtained
- [ ] Working directory is clean (commit or stash changes)
- [ ] WORKTREE_PATH is set to the source worktree path

## Script Invocation

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/git-merge-linear" \
  "$SOURCE_BRANCH" --target "$TARGET_BRANCH"
```

The Java tool implements all steps: check divergence, check suspicious deletions, verify
merge-base, fast-forward merge. Outputs JSON on success.

## Result Handling

On success, the tool prints JSON to stdout (exit code 0) with `"status": "success"`. On failure, it prints a JSON error
to stderr (exit code 1) with `"status": "error"` and a `"message"` field containing the error description.

| Output | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `"status": "success"` (stdout) | Merge completed successfully | Report `merged_commit` and `source_branch`, continue |
| `"status": "error"`: Must be on {target} branch | Wrong branch checked out | `git checkout {target_branch}` first |
| `"status": "error"`: Working directory is not clean | Uncommitted changes | Commit or stash changes before merging |
| `"status": "error"`: Source branch must have exactly 1 commit | Multiple commits | Squash commits first |
| `"status": "error"`: Source branch is behind {target} | Target has commits not in source branch | Rebase source branch onto target before merging |
| `"status": "error"`: Fast-forward merge failed | History diverged | Rebase source branch onto target first |
| `"status": "error"`: Merge commit detected | Non-linear history after merge | Investigate merge state, should not occur with ff-only |
| `"status": "error"`: Missing required argument: --target | Target branch not provided | Pass `--target {branch}` explicitly |

## Common Issues

### Issue 1: "failed to push some refs"
**Cause**: Target branch has moved ahead since issue branch was created
**Solution**: Rebase issue branch onto target first:
```bash
cd "$WORKTREE_PATH"
git fetch origin "$TARGET_BRANCH"
git rebase "origin/$TARGET_BRANCH"
# Then retry merge script
```

### Issue 2: "not a valid ref"
**Cause**: Branch name has special characters or doesn't exist
**Solution**: Verify branch name with `git branch -a`

### Issue 3: Wrong target branch detected
**Cause**: Target branch detection failed (worktree metadata missing)
**Solution**: Pass `--target <branch>` explicitly to the git-merge-linear command

### Issue 4: `git rebase --dry-run` does not exist
**Cause**: `git rebase` has no `--dry-run` flag. Agents sometimes attempt `git rebase --dry-run` by analogy with
commands like `git fetch --dry-run`, but git returns `error: unknown option 'dry-run'`.

**Solution**: There is no way to probe for rebase conflicts without attempting the actual rebase. The correct approach
is to attempt the rebase directly:

```bash
git rebase "$TARGET_BRANCH"
```

- If the rebase succeeds cleanly, continue as normal.
- If conflicts arise, resolve each conflicted file, stage the resolutions with `git add`, then run
  `git rebase --continue` to proceed.
- If the conflicts cannot be resolved, abort the rebase and restore the original state with
  `git rebase --abort`.

## Success Criteria

- [ ] Target branch points to source commit
- [ ] Linear history maintained (no merge commits)
