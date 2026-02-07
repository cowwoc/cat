# Git Amend Skill

**Purpose**: Safely amend the most recent commit with proper verification checks.

## Safety Rules

### Only Amend When ALL Conditions Are Met

1. **HEAD commit was created by you** in this session
2. **Commit has NOT been pushed** to remote
3. **You intend to modify HEAD** (not an earlier commit)

```bash
# Check if pushed:
git status
# Look for: "Your branch is ahead of 'origin/main' by X commits"
# "up to date" = already pushed (amending creates divergent history)
```

## Quick Workflow

### Verification Gate (M191): HEAD Safe to Amend

**BLOCKING: Do NOT proceed until safety is verified.**

```bash
# 1. Verify HEAD is the commit you want to amend
git log --oneline -1

# 2. Verify not pushed to remote
PUSH_STATUS=$(git status --porcelain -b | head -1)
if echo "$PUSH_STATUS" | grep -q "ahead"; then
  echo "✓ Commit not pushed - safe to amend"
elif echo "$PUSH_STATUS" | grep -q "behind\|up to date"; then
  echo "ERROR: HEAD commit appears to be pushed to remote"
  echo "Amending will create divergent history requiring force push."
  echo ""
  echo "Options:"
  echo "1. Create a new commit instead of amending"
  echo "2. Use git commit --amend with explicit force push (dangerous)"
  exit 1
else
  echo "⚠ Could not determine push status - verify manually before amending"
fi
```

```bash
# 3. Make your changes (edit files, stage new files)
git add <files>

# 4. Amend the commit
git commit --amend

# Or with new message:
git commit --amend -m "New message"

# Or keep same message:
git commit --amend --no-edit
```

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

```bash
# If amend was wrong, check reflog for original:
git reflog
git reset --hard HEAD@{1}  # Go back to before amend
```

## Success Criteria

- [ ] Verified HEAD is the commit to amend
- [ ] Verified commit not pushed to remote
- [ ] Changes staged before amend
- [ ] Commit message is appropriate
- [ ] No force push required (unless explicitly intended)
