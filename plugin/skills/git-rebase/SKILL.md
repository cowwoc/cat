---
name: git-rebase
description: Safely rebase with automatic backup and conflict handling
---

# Git Rebase Skill

**Purpose**: Safely rebase branches with automatic backup, conflict detection, and recovery guidance.

## Safety Pattern: Backup-Verify-Cleanup

**ALWAYS follow this pattern:**
1. Create timestamped backup branch
2. Execute the rebase
3. Handle conflicts if any
4. **Verify immediately** - history is correct
5. Cleanup backup only after verification passes

## Quick Workflow

```bash
# 1. Create backup
BACKUP="backup-before-rebase-$(date +%Y%m%d-%H%M%S)"
git branch "$BACKUP"

# 2. Verify clean working directory
git status --porcelain  # Must be empty

# 3. Execute rebase
git rebase <target-branch>

# 4. Handle conflicts if any (see below)

# 5. Verify result
git log --oneline -10
git diff "$BACKUP"  # Content should match (just different history)

# 6. Cleanup backup
git branch -D "$BACKUP"
```

## Handling Conflicts

```bash
# When rebase stops due to conflict:

# 1. Check which files have conflicts
git status

# 2. Edit files to resolve conflicts (look for <<<<<<< markers)

# 3. Stage resolved files
git add <resolved-files>

# 4. Continue rebase
git rebase --continue

# If you want to abort:
git rebase --abort
git reset --hard "$BACKUP"
```

## Common Operations

### Rebase onto main
```bash
git checkout feature-branch
git rebase main
```

### Interactive rebase (reorder, edit, squash)
```bash
git rebase -i <base-commit>

# In editor:
# pick   - use commit as-is
# reword - change commit message
# edit   - stop to amend commit
# squash - combine with previous commit
# fixup  - like squash but discard message
# drop   - remove commit
```

## Dangerous Operations to Avoid

```bash
# NEVER rebase shared/public branches
git rebase main  # While ON main - rewrites shared history!

# NEVER use --all flag
git rebase --all  # Rewrites ALL branches!

# SAFE - rebase feature branch onto main
git checkout feature
git rebase main
```

## Error Recovery

```bash
# If rebase went wrong:
git rebase --abort

# Or restore from backup:
git reset --hard $BACKUP

# Or check reflog:
git reflog
git reset --hard HEAD@{N}
```

## Success Criteria

- [ ] Backup created before rebase
- [ ] Working directory was clean
- [ ] Conflicts resolved (if any)
- [ ] History looks correct
- [ ] No commits lost
- [ ] Backup removed after verification
