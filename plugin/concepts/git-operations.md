<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Operations Reference

## Git Skills vs Raw Commands

CAT provides git skills that handle edge cases correctly. Prefer these over raw git commands:

| Operation | Use Skill | Instead of |
|-----------|-----------|------------|
| Merge issue to base | `/cat:git-merge-linear-agent` | `git checkout && git merge` |
| Amend commits | `/cat:git-amend-agent` | `git commit --amend` |
| Squash commits | `/cat:git-squash-agent` | `git rebase -i` |
| Rebase | `/cat:git-rebase-agent` | `git rebase` |

**Why skills are preferred:**
- Handle pre-flight checks (divergence, dirty state)
- Respect hook constraints
- Include rollback/recovery on failure
- Work correctly from issue worktrees

## Blocked Operations

These operations are blocked by hooks. Know them upfront to avoid wasted attempts:

| Operation | Hook | Why Blocked | Correct Approach |
|-----------|------|-------------|------------------|
| `git checkout` in main worktree | M205 | Protects main workspace | Use worktrees or `git branch -f` |
| `git merge` without `--ff-only` | M047 | Enforces linear history | Rebase first, then `--ff-only` |
| `git push --force` to main/master | Various | Protects shared branches | Never force-push to main |

## Command Efficiency

### Combine Related Commands

```bash
# Instead of separate calls:
git status
git log --oneline -3
git diff --stat

# Combine:
git status && git log --oneline -3 && git diff --stat
```

### Working Directory During Implementation

When executing an issue, run git commands from inside the issue worktree using the single-call `cd` pattern.

```bash
# Correct: cd and git in a single Bash call — cwd persists within the call
cd "${WORKTREE_PATH}" && git log --oneline -3
cd "${WORKTREE_PATH}" && git add "${WORKTREE_PATH}/plugin/skills/foo.md" && git commit -m "feature: add thing"

# Wrong: running git -C /workspace from outside the worktree
git -C /workspace log --oneline -3     # targets main workspace, not issue branch
git -C /workspace add file.txt         # stages in main workspace
```

Using `git -C /workspace` from outside the worktree targets the main workspace git state, not the issue
branch. Commits, adds, and log output will reflect the main workspace — bypassing worktree isolation.

**Why single-call cd:** The Bash tool resets cwd to `/workspace` on every invocation. A bare `cd` in one
call does not persist to the next call. Always combine `cd ${WORKTREE_PATH} && git ...` in a single Bash call.

### Worktree Directory Safety

Before removing a worktree (via `rm`, `git worktree remove`, etc.), ensure your shell is NOT inside the directory
being removed. With the single-call `cd` pattern this is generally safe, but verify cwd before any removal.

```bash
# Safe pattern when removing a worktree:
git worktree remove "${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/issue"
```

See `/cat:safe-rm-agent` for detailed guidance on safe directory removal.

## User Rebase Requests

When the user asks to "rebase on `<branch>`", use the **local** version of that branch (`git rebase <branch>`), not the
remote (`git rebase origin/<branch>`). Do not fetch before rebasing unless the user explicitly asks to rebase on the
remote.

## Linear History Workflow

To merge an source branch to its target branch with linear history:

```bash
# 1. Rebase source branch onto target (from source worktree)
git rebase {target-branch}

# 2. Fast-forward target branch (from source worktree, no checkout needed)
git push . HEAD:{target-branch}

# 3. Cleanup worktree and branch
```

Or use `/cat:git-merge-linear-agent` which does all of this correctly.
