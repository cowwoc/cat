---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Git Squash Skill

**Purpose**: Safely squash multiple commits into one with automatic backup, verification, and cleanup.

## MANDATORY: Use This Skill

**NEVER manually run `git reset --soft` for squashing.** Always use this skill.

Manual `git reset --soft` captures working directory state, which may contain stale files if the worktree diverged from
its base branch. This skill uses `commit-tree` which creates commits from committed tree objects, ignoring working
directory entirely.

**What goes wrong with manual reset:**
1. Worktree created from base at commit A
2. Base advances to commit D (with fixes)
3. Manual `git reset --soft base` keeps stale working directory
4. Commit captures pre-fix file versions, reverting the fixes

**This skill prevents this** by using `commit-tree` to build from HEAD's tree object.

## Read PROJECT.md Squash Policy

**Check PROJECT.md for configured squash preferences before proceeding.**

```bash
# Read squash policy from PROJECT.md
SQUASH_POLICY=$(grep -A10 "### Squash Policy" .claude/cat/PROJECT.md 2>/dev/null | grep "Strategy:" | \
  sed 's/.*Strategy:\s*//' | head -1)

if [[ "$SQUASH_POLICY" == *"keep all"* || "$SQUASH_POLICY" == *"Keep all"* || "$SQUASH_POLICY" == *"keep-all"* ]]; then
  echo "⚠️ PROJECT.md configured for 'Keep all commits'"
  echo "Squashing will override this preference."
  echo ""
  echo "Options:"
  echo "  1. Proceed with squash (override PROJECT.md preference)"
  echo "  2. Cancel to preserve commits as configured"
  echo ""
  echo "To proceed, continue with this skill."
  echo "To honor PROJECT.md preference, abort the squash operation."
fi

if [[ "$SQUASH_POLICY" == *"single"* || "$SQUASH_POLICY" == *"Single"* ]]; then
  echo "📋 PROJECT.md configured for 'Single commit' squashing"
  echo "All commits will be squashed into one (not by type)."
fi

if [[ "$SQUASH_POLICY" == *"by-type"* || "$SQUASH_POLICY" == *"by type"* ]]; then
  echo "📋 PROJECT.md configured for 'Squash by type'"
  echo "Commits will be grouped by type prefix."
fi
```

## Default Behavior: Squash by Topic

**When user asks to squash commits, squash by topic (not all into one):**

- Group related commits by their purpose/topic
- Each topic becomes one squashed commit
- Preserve commit type boundaries (e.g., `feature:` vs `config:`)

**Example:** If branch has these commits:
```
config: add handler registry
config: fix null handling in registry
config: add if/else convention
docs: update README
```

Squash to:
```
config: add handler registry with null handling and conventions
docs: update README
```

**What is NOT the same topic (keep separate):**
- Learning/retrospective changes - these are meta-work, not issue implementation
- Changes to shared infrastructure (build-verification.md, session instructions)
- Convention updates that don't directly enable the implementation

Even if commits share the same type prefix (e.g., `config:`), they may be different topics. The test: "Would reverting
this commit break the issue implementation?" If no, it's a different topic.

**Analyze ALL files in each commit:**

When determining commit topics, examine EVERY file modified by the commit, not just one file type:

```bash
# For each commit, list ALL modified files
git show --stat <commit-hash>

# Don't stop after seeing one file type
# A commit may contain BOTH convention changes AND implementation changes
```

Apply the revert test to ALL files: If reverting the commit would remove implementation changes, it breaks the
implementation - therefore it's the same topic.

## Workflow Selection

**CRITICAL: Choose workflow based on commit position.**

```bash
# Check if commits are at tip of branch
LAST_COMMIT="<last-commit-to-squash>"
BRANCH_TIP=$(git rev-parse HEAD)

if [ "$(git rev-parse $LAST_COMMIT)" = "$BRANCH_TIP" ]; then
    echo "Commits at tip → Use Quick Workflow (script)"
else
    echo "Commits in middle of history → Use Interactive Rebase Workflow"
fi
```

## Quick Workflow (Commits at Branch Tip Only)

**Use ONLY when squashing the most recent commits on a branch.**

### Script Invocation

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/git-squash" "<base-branch>" "$MESSAGE" "$WORKTREE_PATH"
```

The script implements: rebase onto base, backup, commit-tree squash, verify, cleanup. Outputs JSON on success.

### Result Handling

| Status | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `OK` | Squash completed successfully | Report success with commit hash |
| `REBASE_CONFLICT` | Conflict during pre-squash rebase | Agent decides: resolve conflict and retry, or abort |
| `VERIFY_FAILED` | Content changed during squash | Restore from backup branch, investigate diff_stat |
| `ERROR` | Rebase or squash failed | Check backup_branch and error message for details |

## Interactive Rebase Workflow (Commits in Middle of History)

**Use when commits to squash have other commits after them.**

### Safety Pattern: Backup-Verify-Cleanup

**ALWAYS follow this pattern:**
1. Create timestamped backup branch
2. Execute the rebase
3. Handle conflicts if any
4. **Verify immediately** - no changes lost or added
5. Cleanup backup only after verification passes

### Interactive Rebase Steps

```bash
# 1. Pin base branch reference to prevent race conditions
BASE=$(git rev-parse <base-branch>)

# 2. Create backup of current branch
BACKUP="backup-before-squash-$(date +%Y%m%d-%H%M%S)"
git branch "$BACKUP"

# 3. Create sequence editor script
FIRST_COMMIT="<first-commit-to-squash>"  # Keep this one, squash others into it
COMMITS_TO_SQUASH="<second-commit> <third-commit> ..."  # These get squashed

cat > /tmp/squash-editor.sh << EOF
#!/bin/bash
$(for c in $COMMITS_TO_SQUASH; do echo "sed -i 's/^pick $c/squash $c/' \"\$1\""; done)
EOF
chmod +x /tmp/squash-editor.sh

# 4. Generate unified commit message from existing commits
# Review all commit messages being squashed
git log --oneline $FIRST_COMMIT^..HEAD
# Example: abc1234 config: add handler registry
#          def5678 config: fix null handling
# Craft unified message that describes the complete change
MESSAGE="config: add handler registry with null handling"

# 5. Create commit message editor script
cat > /tmp/msg-editor.sh << EOF
#!/bin/bash
cat > "\$1" << 'MSG'
$MESSAGE
MSG
EOF
chmod +x /tmp/msg-editor.sh

# 7. Run interactive rebase
# NOTE: Use GIT_EDITOR (not EDITOR) - git uses GIT_EDITOR for commit messages during rebase
# Use pinned base ref to ensure consistency
BASE_COMMIT=$(git rev-parse $BASE^)  # Parent of base for rebase
GIT_SEQUENCE_EDITOR=/tmp/squash-editor.sh GIT_EDITOR=/tmp/msg-editor.sh git rebase -i $BASE_COMMIT

# 8. Verify no changes lost
git diff "$BACKUP"  # Must be empty

# 9. Cleanup
git branch -D "$BACKUP"
rm /tmp/squash-editor.sh /tmp/msg-editor.sh
```

### Delegate Complex Squash Operations

**MANDATORY: Delegate complex squash operations to a subagent.**

Complex squash operations include:
- Squashing by topic when commits are interleaved (different topics mixed together)
- Non-adjacent commit squashing requiring reordering
- Any operation that may cause merge conflicts due to reordering
- Squashing 5+ commits with multiple topics

**Why delegate:**
- Subagent isolation prevents main context pollution from conflict resolution
- Failed squash attempts don't waste main agent context
- Subagent can retry with different strategies if conflicts occur

**Simple squash (do NOT delegate):**
- Squashing all commits into one (single topic)
- Adjacent commits that don't require reordering
- Quick workflow with commits already at branch tip

## Critical Rules

### Pin Base Branch Reference (Race Condition Prevention)

**MANDATORY: Pin base branch reference before rebase. Do NOT call git rev-parse on the base branch separately for
rebase and commit-tree.**

The base branch can advance between operations, causing race conditions. Always pin once at the start and use the
pinned variable throughout:
```bash
BASE=$(git rev-parse <base-branch>)  # Pin once
git rebase $BASE                      # Use pinned ref
# ... later ...
git commit-tree "$TREE" -p $BASE -m "$MESSAGE"  # Use same pinned ref
```

### Preserve Commit Type Boundaries When Squashing

**CRITICAL: Follow commit grouping rules from [commit-types.md](../../concepts/commit-types.md).**

Key rules when squashing:
- **Issue STATE.md** → same commit as implementation
- **Different commit types** (`feature:` vs `docs:`) → keep separate
- **Related same-type commits** → can combine
- **Implementation + refactor of same code** → combine into one commit

### Automatic STATE.md Preservation

**CRITICAL: Preserve final STATE.md state when squashing planning commits.**

When squashing commits that include STATE.md updates:

1. **Before squash:** Record the final STATE.md content
   ```bash
   # Store final state before squash
   TASK_STATE=".claude/cat/issues/v*/v*.*/*/STATE.md"
   git show HEAD:$TASK_STATE > /tmp/final-state.md 2>/dev/null || true
   ```

2. **After squash:** Verify STATE.md wasn't reverted to intermediate state
   ```bash
   # Check if STATE.md was affected
   if [[ -f /tmp/final-state.md ]]; then
       # Compare current vs final
       if ! diff -q "$TASK_STATE" /tmp/final-state.md >/dev/null 2>&1; then
           echo "⚠️ STATE.md reverted to intermediate state - restoring final state"
           cp /tmp/final-state.md "$TASK_STATE"
           git add "$TASK_STATE"
           git commit --amend --no-edit
       fi
   fi
   ```

### Write Meaningful Commit Messages

```bash
# WRONG - Concatenated messages
feature(auth): add login
feature(auth): add validation
bugfix(auth): fix typo

# CORRECT - Unified message describing what the code does
feature: add login form with validation

- Email/password form with client-side validation
- Server-side validation with descriptive messages
```

See `git-commit` skill for detailed message guidance.

## Squash vs Fixup

| Command | Message Behavior | When to Use |
|---------|-----------------|-------------|
| `squash` | Combines all messages | Different features being combined |
| `fixup` | Discards secondary messages | Trivial fixes (typos, forgotten files) |

**When in doubt, use squash** - you can edit the combined message.

## Error Recovery

If anything goes wrong:
- Restore from backup: `git reset --hard $BACKUP`
- Or check reflog: `git reflog` then `git reset --hard HEAD@{N}`

## Success Criteria

- [ ] Backup created before squash
- [ ] No changes lost (diff with backup is empty)
- [ ] Single commit created with all changes (or commits properly grouped by topic)
- [ ] Meaningful commit message (not "squashed commits")
- [ ] Backup removed after verification
