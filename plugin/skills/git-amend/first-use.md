<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Amend Skill

**Purpose**: Safely amend the most recent commit with proper verification checks.

## When to Use

Only amend when ALL conditions are met:

1. **HEAD commit was created by you** in this session
2. **Commit has NOT been pushed** to remote
3. **You intend to modify HEAD** (not an earlier commit)

## Script Invocation

For deterministic amend with TOCTOU race detection:

```bash
"$(git rev-parse --show-toplevel)/plugin/scripts/git-amend-safe.sh" --no-edit "$WORKTREE_PATH"
# Or with new message:
"$(git rev-parse --show-toplevel)/plugin/scripts/git-amend-safe.sh" --message "new msg" "$WORKTREE_PATH"
```

The script verifies push status before amending and detects if the original commit was pushed during the amend window.

### Result Handling

| Status | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `OK` | Amend completed successfully | Report new_head, confirm no race detected |
| `RACE_DETECTED` | Original commit was pushed during amend | Inform user that force-with-lease push is needed: `git push --force-with-lease` |
| `ALREADY_PUSHED` | Commit already pushed to remote before amend | Inform user that amending would create divergent history. Recommend creating new commit instead |
| `ERROR` | Amend operation failed | Check error message for details |

**On RACE_DETECTED status:** The amend succeeded, but the original commit was pushed to remote during the operation.
User must use `git push --force-with-lease` to update remote. This is safer than `--force` as it prevents overwriting
work pushed by others.

**On ALREADY_PUSHED status:** Do NOT proceed with amend. Advise user to create a new commit instead of amending to
avoid rewriting history that may have been pulled by others.

## Common Use Cases

### Fix a typo in the last commit
```bash
# Edit the file
vim file.txt

# Stage and amend
git add file.txt
git commit --amend --no-edit
```

### Add forgotten file to last commit
```bash
git add forgotten-file.txt
git commit --amend --no-edit
```

### Change commit message only
```bash
git commit --amend -m "Better commit message"
```

## Dangerous Situations

```bash
# Only amend unpushed commits
git push origin main
git commit --amend  # Creates divergent history!

# Only amend your own commits
git pull  # Pulls teammate's commit
git commit --amend  # Rewrites their work!

# If you must amend after push (with explicit permission):
git commit --amend
git push --force-with-lease  # Safer than --force
```

## Amending Earlier Commits

To modify a commit that's NOT at HEAD, use interactive rebase:

```bash
# 1. Start interactive rebase
git rebase -i <commit>^  # Parent of commit to edit

# 2. Change 'pick' to 'edit' for the target commit

# 3. Make changes when rebase stops
git add <files>
git commit --amend

# 4. Continue rebase
git rebase --continue
```

## Error Recovery

If amend was wrong, check reflog for original:
```bash
git reflog
git reset --hard HEAD@{1}  # Go back to before amend
```

## Success Criteria

- [ ] Verified HEAD is the commit to amend
- [ ] Verified commit not pushed to remote
- [ ] Changes staged before amend
- [ ] Commit message is appropriate
- [ ] No force push required (unless explicitly intended)
