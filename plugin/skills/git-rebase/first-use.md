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

## Common Operations

### Rebase onto base branch (Deterministic Script)

For deterministic execution with automatic backup and conflict detection:

```bash
"$(git rev-parse --show-toplevel)/plugin/scripts/git-rebase-safe.sh" "$WORKTREE_PATH" "$BASE_BRANCH"
```

If BASE_BRANCH not provided, the script reads from the cat-base file. The script outputs JSON.

#### Result Handling

| Status | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `OK` | Rebase completed successfully | Report commits rebased, verify no content changes |
| `CONFLICT` | Rebase stopped due to conflicts | Agent examines conflicting_files and decides: manual resolution, alternative strategy, or abort. Backup preserved at backup_branch |
| `ERROR` | Rebase failed (not a conflict) | Check backup_branch and error message. Restore from backup if needed |

**On CONFLICT status:** Agent should examine the conflicting files to determine the best strategy:
- If conflicts are simple (whitespace, formatting), attempt manual resolution
- If conflicts involve complex logic changes, consider alternative merge strategy
- If uncertain, abort and restore from backup

### Interactive rebase (reorder, edit, squash)

For operations requiring judgment (reordering commits, editing history, complex squashing):

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

## Handling Conflicts

**CRITICAL: Persist through conflicts. Never switch to cherry-pick mid-rebase (M241).**

Rebase conflicts are normal and expected when branches have diverged. The solution is to resolve conflicts and continue,
not to abandon rebase for cherry-picking.

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

When rebasing, git may report "skipped previously applied commit" for commits whose changes already exist on the target
branch (perhaps added via separate commits). This is normal - git detects content duplication and skips redundant
commits. Continue the rebase.

### Why Rebase Over Cherry-Pick

| Approach | Pros | Cons |
|----------|------|------|
| Rebase | Preserves linear history, single operation | Must resolve conflicts sequentially |
| Cherry-pick | Can select specific commits | Creates duplicate commits, complex history |

**Rule:** Always complete a rebase. Cherry-pick is only appropriate for extracting a single commit to a different
branch, not for integrating branch changes.

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

If rebase went wrong:
- Abort rebase: `git rebase --abort`
- Restore from backup: `git reset --hard $BACKUP`
- Check reflog: `git reflog` then `git reset --hard HEAD@{N}`

## Verification After Amend/Fixup Operations

**CRITICAL: When using rebase to amend or fixup a historical commit, verify the target commit actually contains the
expected changes.**

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

**Common failure mode (M244):** Rebase reports "Successfully rebased" but the fixup commit was dropped due to
conflicts. Always verify the target commit's contents before proceeding.

## Success Criteria

- [ ] Backup created before rebase
- [ ] Working directory was clean
- [ ] Conflicts resolved (if any)
- [ ] History looks correct
- [ ] No commits lost
- [ ] **Target commit contains expected changes (for amend/fixup)**
- [ ] Backup removed after verification
