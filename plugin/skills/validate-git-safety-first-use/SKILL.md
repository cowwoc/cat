---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Validate Git Safety Skill

**Purpose**: Validate git history-rewriting operations won't affect protected branches or cause unintended data loss.

## When to Use

- Before `git filter-branch`
- Before `git rebase` with `--all` or `--branches`
- Before any history-rewriting operation
- Before deleting branches

## Protected Branch Patterns

### Version Branches (NEVER modify)
- Pattern: `v[0-9]+` (e.g., v1, v13, v21)
- Protection: NEVER delete, rewrite history, or force push

### Main/Master (careful modifications only)
- Only fast-forward merges
- No history rewriting

### Issue Branches
- Treat as immutable after completion

## Dangerous Commands

```bash
# NEVER use --all with history rewriting
git filter-branch --all
git rebase --all

# NEVER force push without explicit request
git push --force
git push -f

# NEVER delete version branches
git branch -D v21

# NEVER rebase shared branches
git checkout main
git rebase feature  # Rewrites main!
```

## Safe Alternatives

```bash
# Target specific branch (not --all)
git filter-branch main

# Rebase feature onto main (not main onto feature)
git checkout feature
git rebase main

# Use --force-with-lease instead of --force
git push --force-with-lease

# Move version branch pointer forward (not rewrite)
git branch -f v21 <new-commit>
```

## Worktree Verification

**During CAT issue execution, NEVER operate on the main `/workspace` worktree.**

```bash
# Before ANY git operation in a CAT issue, verify location:
CURRENT_DIR=$(pwd)
if [[ "$CURRENT_DIR" == "/workspace" ]] && [[ -d "/workspace/.claude/cat/worktrees" ]]; then
  echo "BLOCKED: Currently in main worktree while issue worktrees exist"
  echo "Expected: /workspace/.claude/cat/worktrees/<issue-name>"
  echo "Current:  $CURRENT_DIR"
  exit 1
fi
```

**Check for active issue worktrees:**
```bash
# If any worktrees exist, you should be in one of them (not main)
if ls -d /workspace/.claude/cat/worktrees/*/ 2>/dev/null | head -1 | grep -q .; then
  # Worktrees exist - verify we're in one
  if [[ "$(pwd)" == "/workspace" ]]; then
    echo "ERROR: Issue worktrees exist but operating on main"
    ls /workspace/.claude/cat/worktrees/
    exit 1
  fi
fi
```

## Pre-Operation Checklist

Before any history-rewriting operation:

1. [ ] List all branches: `git branch -a`
2. [ ] Identify protected branches: `git branch | grep -E "^  v[0-9]+"`
3. [ ] Verify command targets specific branch (not --all)
4. [ ] Create backup: `git branch backup-before-op-$(date +%Y%m%d-%H%M%S)`
5. [ ] Execute operation
6. [ ] Verify protected branches unchanged
7. [ ] Cleanup backup

## Validation Before Dangerous Operations

```bash
# Before running dangerous command, check:
COMMAND="git filter-branch --tree-filter 'rm secrets.txt' HEAD"

# Check for dangerous flags
if echo "$COMMAND" | grep -qE "(--all|--branches)"; then
  echo "BLOCKED: --all/--branches affects protected branches"
  echo "Use: git filter-branch <branch-name>"
  exit 1
fi

# Check target isn't version branch
TARGET=$(echo "$COMMAND" | grep -oE "v[0-9]+")
if [[ -n "$TARGET" ]]; then
  echo "BLOCKED: Cannot modify version branch $TARGET"
  exit 1
fi

echo "Command appears safe"
```

## Recovery If Protected Branch Modified

```bash
# If version branch history was modified:

# 1. Check reflog for original position
git reflog show v21

# 2. Reset to original commit
git branch -f v21 v21@{1}

# 3. Verify restored
git log v21 -5 --oneline
```

## Success Criteria

- [ ] Identified all protected branches
- [ ] Command doesn't use --all or --branches
- [ ] Command doesn't target version branches
- [ ] Backup created before operation
- [ ] Protected branches unchanged after operation
