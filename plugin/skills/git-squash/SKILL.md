---
description: "MANDATORY: Use instead of `git rebase -i` for squashing - unified commit messages"
user-invocable: false
---

# Git Squash Skill

**Purpose**: Safely squash multiple commits into one with automatic backup, verification, and cleanup.

## Parallel Initial Investigation

**OPTIMIZATION: Run initial git commands in parallel to reduce round-trips.**

Before starting any squash workflow, gather information concurrently:

```bash
# Run these commands in parallel (use & and wait)
git rev-parse HEAD &
git status --porcelain &
git log --oneline <base>..HEAD &
git diff --stat <base>..HEAD &
wait

# All results now available for workflow selection
```

This reduces the initial investigation from 4+ sequential commands to a single parallel batch.

## Safety Pattern: Backup-Verify-Cleanup

**ALWAYS follow this pattern:**
1. Create timestamped backup branch
2. Execute the squash
3. **Verify immediately** - no changes lost or added
4. Cleanup backup only after verification passes

## Read PROJECT.md Squash Policy

**Check PROJECT.md for configured squash preferences before proceeding.**

```bash
# Read squash policy from PROJECT.md
SQUASH_POLICY=$(grep -A10 "### Squash Policy" .claude/cat/PROJECT.md 2>/dev/null | grep "Strategy:" | sed 's/.*Strategy:\s*//' | head -1)

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

**Rationale:** Squashing by topic preserves meaningful history while reducing noise from incremental fixes.

## Workflow Selection

**CRITICAL: Choose workflow based on commit position.**

```bash
# Check if commits are at tip of branch
LAST_COMMIT="<last-commit-to-squash>"
BRANCH_TIP=$(git rev-parse HEAD)

if [ "$(git rev-parse $LAST_COMMIT)" = "$BRANCH_TIP" ]; then
    echo "Commits at tip → Use Quick Workflow (commit-tree)"
else
    echo "Commits in middle of history → Use Interactive Rebase Workflow"
fi
```

## Planning Commit Pattern Detection

**Detect common "feature + planning STATE.md update" pattern.**

Before squashing, check if the commit sequence follows this pattern:
1. Implementation commit(s): `feature:`, `bugfix:`, `refactor:`, etc.
2. Final commit(s): `planning:` or `config:` with only `.claude/cat/issues/` changes

**Detection logic:**
```bash
# Get the last commit's type and files
LAST_COMMIT=$(git log -1 --format="%s" HEAD)
LAST_FILES=$(git diff-tree --no-commit-id --name-only -r HEAD)

# Check if last commit is planning-only
if [[ "$LAST_COMMIT" =~ ^planning: ]] && \
   [[ "$LAST_FILES" =~ \.claude/cat/issues/ ]] && \
   ! echo "$LAST_FILES" | grep -qv "\.claude/cat/"; then
    echo "PATTERN DETECTED: Final commit is planning-only STATE.md update"
    # This pattern indicates STATE.md should be preserved in squash
fi
```

**When pattern detected:**
- Extract final STATE.md content before squash
- After squash, ensure STATE.md reflects final state (not intermediate)
- Include planning changes in implementation commit per M076

## Quick Workflow (Commits at Branch Tip Only)

**Use ONLY when squashing the most recent commits on a branch.**

```bash
# 1. Verify commits are at tip
git log --oneline -1  # Should show <last-commit-to-squash>

# 2. Create backup
BACKUP="backup-before-squash-$(date +%Y%m%d-%H%M%S)"
git branch "$BACKUP"
```

### Verification Gate (M191): Backup Created

**BLOCKING: Do NOT proceed until backup is verified.**

```bash
if ! git show-ref --verify --quiet "refs/heads/$BACKUP"; then
  echo "ERROR: Backup branch '$BACKUP' was not created"
  echo "Do NOT proceed with squash without backup."
  exit 1
fi
echo "✓ Backup verified: $BACKUP"
```

```bash
# 3. Verify clean working directory
git status --porcelain  # Must be empty

# 4. Create squashed commit using commit-tree (M305)
# This uses COMMIT content directly, ignoring working directory state.
# Prevents stale worktree files from being captured in the squash.
TREE=$(git rev-parse HEAD^{tree})
MESSAGE="Unified message describing what code does"
NEW_COMMIT=$(git commit-tree "$TREE" -p <base-commit> -m "$MESSAGE")

# 5. Move branch to new squashed commit
git reset --hard "$NEW_COMMIT"

# 6. Verify result
git diff "$BACKUP"  # Must be empty (same content)
git rev-list --count <base-commit>..HEAD  # Must be 1

# 7. Cleanup backup
git branch -D "$BACKUP"
```

**Why commit-tree instead of soft reset? (M305)**

The old approach (`git reset --soft` + `git commit`) captured working directory state.
If the worktree diverged from its base branch, stale file versions would be included.

`git commit-tree` creates a commit directly from HEAD's tree object, which contains
exactly what the commits contain - ignoring working directory entirely.

## Interactive Rebase Workflow (Commits in Middle of History)

**Use when commits to squash have other commits after them.**

```bash
# 1. Create backup of current branch
BACKUP="backup-before-squash-$(date +%Y%m%d-%H%M%S)"
git branch "$BACKUP"

# 2. Create sequence editor script
FIRST_COMMIT="<first-commit-to-squash>"  # Keep this one, squash others into it
COMMITS_TO_SQUASH="<second-commit> <third-commit> ..."  # These get squashed

cat > /tmp/squash-editor.sh << EOF
#!/bin/bash
$(for c in $COMMITS_TO_SQUASH; do echo "sed -i 's/^pick $c/squash $c/' \"\$1\""; done)
EOF
chmod +x /tmp/squash-editor.sh

# 3. Create commit message editor script
cat > /tmp/msg-editor.sh << 'EOF'
#!/bin/bash
cat > "$1" << 'MSG'
<your unified commit message here>
MSG
EOF
chmod +x /tmp/msg-editor.sh

# 4. Run interactive rebase
# NOTE: Use GIT_EDITOR (not EDITOR) - git uses GIT_EDITOR for commit messages during rebase
BASE_COMMIT="<parent-of-first-commit>"
GIT_SEQUENCE_EDITOR=/tmp/squash-editor.sh GIT_EDITOR=/tmp/msg-editor.sh git rebase -i $BASE_COMMIT

# 5. Verify no changes lost
git diff "$BACKUP"  # Must be empty

# 6. Cleanup
git branch -D "$BACKUP"
rm /tmp/squash-editor.sh /tmp/msg-editor.sh
```

## Critical Rules

### Check for Unintended Deletions (M238)

**NOTE: The Quick Workflow now uses `commit-tree` (M305), which avoids this issue entirely
by using commit content directly instead of working directory state.**

This section applies only if you manually use `git reset --soft`:

When using `git reset --soft <base>`, the index reflects your working tree state. If the base
branch has files your branch never received (e.g., new files added to base after your branch
diverged), the soft reset will stage those files as DELETIONS.

**Before committing after soft reset:**

```bash
# Check what will be deleted relative to base
git diff --name-status HEAD | grep "^D"

# If unexpected deletions appear, restore from base:
git checkout <base> -- <path-to-unexpected-deleted-file>

# Then amend the commit
git commit --amend --no-edit
```

**Why this happens:**
1. Branch created from older base commit
2. New files added to base branch later
3. Worktree never received these files (no merge/rebase from base)
4. Soft reset stages "delete files that exist on base but not in working tree"

### Preserve Commit Type Boundaries When Squashing

**CRITICAL: Follow commit grouping rules from [commit-types.md](../../concepts/commit-types.md).**

Key rules when squashing:
- **Task STATE.md** → same commit as implementation (M076)
- **Different commit types** (`feature:` vs `docs:`) → keep separate
- **Related same-type commits** → can combine

**Before squashing, analyze commit types:**

```bash
# List commits with their types
git log --oneline <base>..HEAD | while read hash msg; do
    type=$(echo "$msg" | cut -d: -f1)
    echo "$type: $hash ${msg#*: }"
done | sort -t: -k1

# Group by type to determine squash strategy
git log --format="%s" <base>..HEAD | cut -d: -f1 | sort | uniq -c
```

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

**Why this matters:**
- Squashing can revert STATE.md to earlier commit's version
- Final state (status: completed, progress: 100%) must be preserved
- Per M076: STATE.md belongs in same commit as implementation

### Use Correct Workflow for Commit Position

```bash
# WRONG - Using soft reset workflow for mid-history commits
git checkout <mid-history-commit>
git reset --soft <base>
git branch -f main HEAD  # LOSES all commits after the squash range!

# CORRECT - Use interactive rebase for mid-history commits
GIT_SEQUENCE_EDITOR=... git rebase -i <base>  # Preserves all commits
```

### Position HEAD First (Quick Workflow Only)

**NOTE: With commit-tree approach (M305), HEAD must be at the last commit to squash,
since we use `HEAD^{tree}` to get the final tree state.**

```bash
# Verify HEAD is at the last commit you want included
git log --oneline -1  # Should show <last-commit-to-squash>

# If not, checkout first
git checkout <last-commit-to-squash>
```

### Write Meaningful Commit Messages with Task ID

```bash
# WRONG - Concatenated messages, no Task ID
feature(auth): add login
feature(auth): add validation
bugfix(auth): fix typo

# CORRECT - Unified message with Task ID footer
feature: add login form with validation

- Email/password form with client-side validation
- Server-side validation with descriptive messages

Task ID: v1.1-implement-user-auth
```

**MANDATORY**: Include `Task ID: v{major}.{minor}-{task-name}` as the last line.

See `git-commit` skill for detailed message guidance.

### Verify Immediately After Commit

```bash
# Check no changes lost
git diff "$BACKUP"  # Empty = success

# Check commit count
git rev-list --count <base>..HEAD  # Should be 1
```

## Squash vs Fixup

| Command | Message Behavior | When to Use |
|---------|-----------------|-------------|
| `squash` | Combines all messages | Different features being combined |
| `fixup` | Discards secondary messages | Trivial fixes (typos, forgotten files) |

**When in doubt, use squash** - you can edit the combined message.

## Non-Adjacent Commits

For commits separated by others, use interactive rebase:

```bash
git rebase -i <base-commit>

# In editor: Move commits to be adjacent, mark with 'squash'
# Example:
#   pick abc123 Target commit
#   squash def456 Commit to combine (MOVED here)
#   pick ghi789 Other commit (unchanged)
```

### Conflict Pre-Computation for Non-Adjacent Commits

**Before attempting to squash non-adjacent commits, analyze potential conflicts:**

```bash
# Identify files modified by multiple commits in the range
echo "Conflict risk analysis:"
git diff --name-only <base>..HEAD | while read file; do
    commits=$(git log --oneline <base>..HEAD -- "$file" | wc -l)
    if [[ $commits -gt 1 ]]; then
        echo "  ⚠️ $file: modified by $commits commits"
    fi
done

# Check for same-line modifications (highest risk)
for file in $(git diff --name-only <base>..HEAD); do
    git log -p <base>..HEAD -- "$file" | grep -E "^@@" | sort | uniq -d && \
        echo "  🔴 $file: same lines modified by multiple commits - HIGH RISK"
done
```

**Risk Levels:**
| Risk | Pattern | Recommendation |
|------|---------|----------------|
| LOW | Different files per commit | Safe to squash |
| MEDIUM | Same file, different lines | Usually safe |
| HIGH | Same file, same lines | Manual resolution likely needed |

## Error Recovery

```bash
# If anything goes wrong:
git reset --hard $BACKUP

# Or check reflog:
git reflog
git reset --hard HEAD@{N}
```

## Success Criteria

- [ ] Backup created before squash
- [ ] HEAD positioned at correct last commit
- [ ] No changes lost (diff with backup is empty)
- [ ] Single commit created with all changes
- [ ] Meaningful commit message (not "squashed commits")
- [ ] Backup removed after verification
