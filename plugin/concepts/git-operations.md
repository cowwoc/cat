# Git Operations Reference

## Git Skills vs Raw Commands

CAT provides git skills that handle edge cases correctly. Prefer these over raw git commands:

| Operation | Use Skill | Instead of |
|-----------|-----------|------------|
| Merge issue to base | `/cat:git-merge-linear` | `git checkout && git merge` |
| Amend commits | `/cat:git-amend` | `git commit --amend` |
| Squash commits | `/cat:git-squash` | `git rebase -i` |
| Rebase | `/cat:git-rebase` | `git rebase` |

**Why skills are preferred:**
- Handle pre-flight checks (divergence, dirty state)
- Respect hook constraints (M205, M047)
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

### Use Absolute Paths

```bash
# Use git -C to operate on worktrees without changing directory:
git -C /workspace/.claude/cat/worktrees/issue status
```

## Linear History Workflow

To merge an issue branch to its base branch with linear history:

```bash
# 1. Rebase issue branch onto base (from issue worktree)
git rebase {base-branch}

# 2. Fast-forward base branch (from issue worktree, no checkout needed)
git push . HEAD:{base-branch}

# 3. Cleanup worktree and branch
```

Or use `/cat:git-merge-linear` which does all of this correctly.
