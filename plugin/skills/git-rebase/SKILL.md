---
name: git-rebase
description: "MANDATORY: Use instead of `git rebase` - provides automatic backup and conflict recovery"
---

# Git Rebase Skill

**Purpose**: Safely rebase branches with automatic backup, conflict detection, and recovery guidance.

## PROJECT.md Merge Policy Check

**Check PROJECT.md for configured merge preferences before rebasing.**

```bash
# Check if Git Workflow section exists in PROJECT.md
MERGE_POLICY=$(grep -A10 "### Merge Policy" .claude/cat/PROJECT.md 2>/dev/null)

if echo "$MERGE_POLICY" | grep -qi "MUST.*merge commit"; then
  echo "⚠️ WARNING: PROJECT.md prefers merge commits over rebase"
  echo "Rebasing may conflict with configured workflow."
  echo ""
  echo "PROJECT.md specifies merge commits should be used, which preserves"
  echo "branch history. Rebasing rewrites history to be linear."
  echo ""
  echo "Proceed only if you understand the implications:"
  echo "  - Rebasing will create linear history (no merge commits)"
  echo "  - This overrides the PROJECT.md preference"
  echo ""
  echo "To honor PROJECT.md preference, use 'git merge --no-ff' instead."
fi
```

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

**CRITICAL: Persist through conflicts. Never switch to cherry-pick mid-rebase (M241).**

Rebase conflicts are normal and expected when branches have diverged. The solution is to resolve
conflicts and continue, not to abandon rebase for cherry-picking.

```bash
# When rebase stops due to conflict:

# 1. Check which files have conflicts
git status

# 2. Edit files to resolve conflicts (look for <<<<<<< markers)

# 3. Stage resolved files
git add <resolved-files>

# 4. Continue rebase
git rebase --continue

# 5. Repeat steps 1-4 for each conflicting commit

# If you want to abort:
git rebase --abort
git reset --hard "$BACKUP"
```

### "Skipped previously applied" Messages

When rebasing, git may report "skipped previously applied commit" for commits whose changes already
exist on the target branch (perhaps added via separate commits). This is normal - git detects
content duplication and skips redundant commits. Continue the rebase.

### Why Rebase Over Cherry-Pick

| Approach | Pros | Cons |
|----------|------|------|
| Rebase | Preserves linear history, single operation | Must resolve conflicts sequentially |
| Cherry-pick | Can select specific commits | Creates duplicate commits, complex history |

**Rule:** Always complete a rebase. Cherry-pick is only appropriate for extracting a single commit
to a different branch, not for integrating branch changes.

## Common Operations

### Rebase onto base branch
```bash
# Detect base branch from worktree metadata (fail-fast if missing)
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: cat-base file not found. Recreate worktree with /cat:work." >&2
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

git rebase "$BASE_BRANCH"
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

## Safe Rebase Patterns

```bash
# Only rebase local/feature branches (not shared ones)
git rebase main  # While ON main - rewrites shared history!

# Avoid --all flag (rewrites ALL branches)
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

## Verification After Amend/Fixup Operations

**CRITICAL: When using rebase to amend or fixup a historical commit, verify the target commit
actually contains the expected changes.**

```bash
# After rebase completes, verify the target commit has expected files
TARGET_COMMIT="<original-hash>"  # Note: hash changes after rebase!

# Find new commit with same message
NEW_COMMIT=$(git log --oneline --all | grep "<partial-message>" | head -1 | cut -d' ' -f1)

# Verify it contains expected files
git show "$NEW_COMMIT" --stat

# Check specific file exists in commit
git show "$NEW_COMMIT" -- path/to/expected/file.md

# If file is NOT in the commit, the amend/fixup FAILED silently
```

**Common failure mode (M244):** Rebase reports "Successfully rebased" but the fixup commit was
dropped due to conflicts. Always verify the target commit's contents before proceeding.

## Success Criteria

- [ ] Backup created before rebase
- [ ] Working directory was clean
- [ ] Conflicts resolved (if any)
- [ ] History looks correct
- [ ] No commits lost
- [ ] **Target commit contains expected changes (for amend/fixup)**
- [ ] Backup removed after verification
