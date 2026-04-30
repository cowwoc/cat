<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Squash Skill

**Purpose**: Safely squash multiple commits into one with automatic backup, verification, and cleanup.

## Scope Boundary

**This skill covers squash and rebase only.** After squash completes, return results to the caller and stop.

Subsequent operations — `git merge`, `git push`, `git push --force`, worktree removal (`git worktree remove`),
branch deletion (`git branch -D`), `git tag`, and invoking `work-complete` — are NOT part of this skill. Performing
them without explicit user approval is a protocol violation. These operations require the full `work-with-issue`
approval gate before they can proceed.

## MANDATORY: Use This Skill

**NEVER manually run `git reset --soft` for squashing.** Always use this skill.

**Why:** Manual `git reset --soft` captures the working directory, which may contain stale files when the base
advanced. This skill uses `commit-tree` to build commits from HEAD's tree object, ignoring the working directory.

## Read project.md Squash Policy

**Check project.md for configured squash preferences before proceeding.**

```bash
SQUASH_POLICY=$(grep -A10 "### Squash Policy" .cat/project.md 2>/dev/null | grep "Strategy:" | \
  sed 's/.*Strategy:\s*//' | head -1)
```

- `keep all` / `Keep all` / `keep-all`: **MANDATORY** — do NOT proceed until the user explicitly confirms the
  override. Ask: "project.md is configured for 'keep all commits'. Override and squash anyway?" The agent's own
  invocation does NOT count as confirmation.
- `single` / `Single`: all commits squashed into one (not by type)
- `by-type` / `by type`: commits grouped by type prefix

## Default Behavior: Squash by Topic

**When user asks to squash commits, squash by topic (not all into one):**

- Group related commits by their purpose/topic
- Each topic becomes one squashed commit
- Topic is primary — commits on the same topic combine even if they have different type prefixes
- All implementation types (`feature:`, `bugfix:`, `test:`, `refactor:`, `docs:`) for the same issue belong in
  ONE implementation commit — squash them together, choosing the type that describes the primary change
- Keep separate only when commits belong to different categories: implementation vs. config/infrastructure

**Example:** Squash these commits:
```
config: add handler registry
config: fix null handling in registry
config: add if/else convention
docs: update README
```
To:
```
config: add handler registry with null handling and conventions
docs: update README
```

**Cross-type example (same topic, different prefixes):** Commits `bugfix: fix GetSkill argument parsing` and
`refactor: change GetSkill to accept pre-tokenized arguments` both touch `GetSkill.java` and address the same
concern. Squash to one: `bugfix: fix GetSkill argument parsing and pre-tokenize inputs`

**What is NOT the same topic (keep separate):**
- Learning/retrospective changes — meta-work, not issue implementation
- Changes to shared infrastructure (build-verification.md, session instructions)
- Convention updates that don't directly enable the implementation

Even if commits share the same type prefix, they may be different topics. The test: "Would reverting this commit
break the issue implementation?" If no, it's a different topic.

**Analyze ALL files in each commit** (`git show --stat <commit-hash>`). A commit may contain both convention
changes AND implementation changes. Apply the revert test to all files — if reverting removes implementation
changes, it's the same topic.

## Workflow Selection

**CRITICAL: Choose workflow based on commit position.**

If the last commit to squash equals `HEAD` (`git rev-parse <last-commit>` == `git rev-parse HEAD`):
use the **Quick Workflow**. Otherwise use the **Interactive Rebase Workflow**.

## Quick Workflow (Commits at Branch Tip Only)

**Use ONLY when squashing the most recent commits on a branch.**

### Script Invocation

```bash
"${CLAUDE_PLUGIN_DATA}/client/bin/git-squash" "<target-branch>" "$MESSAGE" "$WORKTREE_PATH"
```

The script implements: rebase onto target, backup, commit-tree squash, verify, cleanup. Outputs JSON on success.

### Result Handling

| Status | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `OK` | Squash completed successfully | Verify working tree (see below), then report success |
| `REBASE_CONFLICT` | Conflict during pre-squash rebase | Invoke `cat:git-rebase-agent` and follow its `## Handling Conflicts` numbered steps. |
| `VERIFY_FAILED` | Content changed during squash | Restore from backup branch, investigate diff_stat. Delete backup after investigation. |
| `ERROR` | Rebase or squash failed | Check backup_branch and error message for details. Delete backup after the error is handled. |

### CONCURRENT_MODIFICATION Warnings

When the `OK` response includes a `warnings` array with `type: "CONCURRENT_MODIFICATION"`, the listed files were
modified on both branches and auto-resolved. **This is expected normal behavior when the target branch advanced
since the worktree was created.**

**Correct response to CONCURRENT_MODIFICATION:**
1. Complete the standard Post-Squash Working Tree Verification below
2. For each flagged file, confirm the final content is correct — verify that both the issue branch change and the
   target branch change were preserved correctly
3. For auto-resolved comments, documentation, or test descriptions: verify the merged text accurately describes
   actual code behavior (see Post-Squash Comment Semantic Verification for the full procedure)
4. If auto-resolution is correct and all comments are semantically accurate, proceed normally — no learn session required
5. If a comment is semantically wrong but code is correct: update the comment, then **amend** into the squashed
   commit (`git commit --amend --no-edit`). Do NOT create a separate correction commit
6. If auto-resolution of code is incorrect (wrong logic preserved or lost), restore from backup and resolve manually

**Do NOT trigger `cat:learn` for CONCURRENT_MODIFICATION warnings that were auto-resolved correctly.** These
warnings signal that manual verification is needed, not that a mistake occurred.

### Post-Squash Working Tree Verification (MANDATORY on `OK`)

```bash
cd "$WORKTREE_PATH"
git log --oneline -1       # Must show the squashed commit hash from the OK response
git status --porcelain     # Must be empty — any output indicates a diverged working tree

# If working tree shows diverged state (output not empty):
git reset --hard HEAD
```

**Why:** `backup_verified: true` confirms content correctness but NOT that the working tree was updated. If the
worktree is diverged, `git log` shows the squashed commit but on-disk files differ from HEAD.

### Post-Squash Comment Semantic Verification (MANDATORY on `OK`)

After the working tree verification above passes, verify that comments and documentation in files modified by the
squash still accurately describe the code. This check applies to ALL successful squashes, not only those with
CONCURRENT_MODIFICATION warnings. The rebase performed by the Quick Workflow script may auto-resolve merges that
leave comments describing old behavior without flagging CONCURRENT_MODIFICATION.

```bash
cd "$WORKTREE_PATH"
# List files modified between the target branch and HEAD
git diff --name-only "$TARGET_BRANCH"...HEAD
```

For each modified file with a source code extension (`.java`, `.sh`, `.bash`, `.md`, `.ts`, `.js`, `.py`, `.xml`,
`.yaml`, `.yml`, `.properties`, `.toml`, `.gradle`, `.kt`, `.kts`, or any other text file that contains
comment syntax), verify comments and documentation strings. Binary files, images, and `.json` files are excluded
(JSON has no comments). For every qualifying file:
1. Read the **entire file** — do not stop after the first screen or first few lines. Every comment in the file must
   be checked, regardless of file length.
2. For `.md` files, treat all prose descriptions of code behavior as documentation strings subject to verification
   (e.g., "this function returns X" or "the script does Y").
3. Read each comment or documentation string in the file
4. Read the corresponding code that the comment describes
5. Confirm the comment matches what the code actually does
6. **Common failure mode:** A comment says "Returns null if not found" but the code now throws an exception.
   The rebase carried the comment forward unchanged while the code changed around it.

Do NOT skip a qualifying file by claiming its comments are trivial or unchanged — the rebase may have changed code
around unchanged comments, making them stale. Do NOT perform a shallow read (e.g., reading only the top of a file)
and claim verification is complete.

If a comment is semantically wrong but the code is correct: update the comment to accurately describe the code,
then **re-squash** the correction into the squashed commit using `git commit --amend --no-edit` (or re-invoke this
skill) so the branch remains at a single squashed commit. Do NOT create a separate correction commit and proceed —
the branch must stay at the squash commit count established by the squash operation.

## Interactive Rebase Workflow (Commits in Middle of History)

**Use when commits to squash have other commits after them.**

### Safer Two-Step Approach (MANDATORY when Dropping AND Squashing)

**When you need to both DROP and SQUASH commits in the middle of history, do NOT combine them into one operation.**

Why: Complex interactive rebases that both drop and squash commits can silently lose content from commits before the
squash range.

**Instead, use two separate operations:**

#### Step 1: Drop Unwanted Commits (Simple Rebase)

```bash
COMMITS_TO_DROP="ccfd263f 06635253"
BASE=$(git rev-parse main)  # or your target branch
BACKUP="backup-before-drop-$(date +%Y%m%d-%H%M%S)"
git branch "$BACKUP"

git rebase $BASE  # starts with the commits to drop already excluded

# Verify: diff must only contain changes from the dropped commits
git diff "$BACKUP"
# Every file in the diff must appear in files touched by dropped commits:
git log --oneline --name-only $COMMITS_TO_DROP | sort -u
# If any diff file was NOT modified by a dropped commit, content was lost — restore from backup.
```

#### Step 2: Squash Remaining Commits (Separate Operation)

After dropping, use the Interactive Rebase Workflow below. Each step is independently verifiable, preventing
accidental file loss.

### Safety Pattern: Backup-Verify-Cleanup

**ALWAYS follow this pattern:**
1. Create timestamped backup branch
2. Execute the rebase
3. Handle conflicts if any
4. **Verify immediately** — no changes lost or added
5. Cleanup backup only after verification passes

### Interactive Rebase Steps

```bash
# 1. Find merge-base commit
BASE=$(git merge-base HEAD "$TARGET_BRANCH")

# 2. Create backup
BACKUP="backup-before-squash-$(date +%Y%m%d-%H%M%S)"
git branch "$BACKUP"

# 3. Create isolated temp directory (multi-instance safe)
mkdir -p .cat/work/tmp
SQUASH_TMPDIR=$(mktemp -d -p .cat/work/tmp)

# 4. Create sequence editor script
FIRST_COMMIT="<first-commit-to-squash>"
COMMITS_TO_SQUASH="<second-commit> <third-commit> ..."

cat > "$SQUASH_TMPDIR/squash-editor.sh" << EOF
#!/bin/bash
$(for c in $COMMITS_TO_SQUASH; do echo "sed -i 's/^pick $c/squash $c/' \"\$1\""; done)
EOF
chmod +x "$SQUASH_TMPDIR/squash-editor.sh"

# 5. Craft unified commit message from all commits being squashed
git log --oneline $FIRST_COMMIT^..HEAD
MESSAGE="config: add handler registry with null handling"

# 6. Create commit message editor script
cat > "$SQUASH_TMPDIR/msg-editor.sh" << EOF
#!/bin/bash
cat > "\$1" << 'MSG'
$MESSAGE
MSG
EOF
chmod +x "$SQUASH_TMPDIR/msg-editor.sh"

# 7. Run interactive rebase
# NOTE: Use GIT_EDITOR (not EDITOR) — git uses GIT_EDITOR for commit messages during rebase
BASE_COMMIT=$(git rev-parse $BASE^)
GIT_SEQUENCE_EDITOR="$SQUASH_TMPDIR/squash-editor.sh" GIT_EDITOR="$SQUASH_TMPDIR/msg-editor.sh" git rebase -i $BASE_COMMIT

# 8. Verify no changes lost
git diff "$BACKUP"  # Must be empty

# 9. Post-Squash Comment Semantic Verification (MANDATORY)
# Same verification required by the Quick Workflow — applies to ALL squash workflows.
git diff --name-only "$TARGET_BRANCH"...HEAD
# Follow the EXACT procedure in the Quick Workflow's "Post-Squash Comment Semantic Verification"
# section above. All rules apply identically:
# - Read the ENTIRE file (not just the top)
# - Check every comment and documentation string against actual code behavior
# - Do NOT skip a qualifying file by claiming its comments are trivial or unchanged
# - If a comment is stale, amend the correction into the squashed commit (do NOT create a separate commit)

# 10. Cleanup
git branch -D "$BACKUP"
rm -rf "$SQUASH_TMPDIR"
```

### Delegate Complex Squash Operations

**MANDATORY: Delegate complex squash operations to a subagent.**

Complex squash operations include:
- Squashing by topic when commits are interleaved (different topics mixed together)
- Non-adjacent commit squashing requiring reordering
- Any operation that may cause merge conflicts due to reordering
- Squashing 5+ commits with multiple topics

Delegation isolates conflict resolution from the main context and allows retry with different strategies.

**Simple squash (do NOT delegate):**
- Squashing all commits into one (single topic)
- Adjacent commits that don't require reordering
- Quick workflow with commits already at branch tip

## Critical Rules

### Preserve Commit Type Boundaries When Squashing

**CRITICAL: Follow commit grouping rules from [commit-types.md](../../concepts/commit-types.md).**

| Category | Types | Squash to |
|----------|-------|-----------|
| Implementation | `feature:`, `bugfix:`, `test:`, `refactor:`, `docs:` | ONE commit |
| Config/Infrastructure | `config:` | ONE commit (optional, only for general config) |
| Planning | `planning:` | ONE commit (optional) |

**Key rules when squashing:**
- **Issue index.json** → same commit as implementation
- **Test commits for the issue implementation** → same commit as implementation
- **Same topic, different type prefixes** (`bugfix:` + `refactor:` on same code) → combine; choose type that best
  describes the combined change
- **Different implementation types on the same issue** (`feature:` + `test:` + `refactor:`) → ONE implementation
  commit; use the type that best describes the primary change
- **Implementation vs general config** (`feature:` vs `config:` for a new dependency) → keep separate
- **Related same-type commits** → can combine

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

**When in doubt, use squash** — you can edit the combined message.

## Error Recovery

If anything goes wrong:
- Restore from backup: `git reset --hard $BACKUP`
- Or check reflog: `git reflog` then `git reset --hard HEAD@{N}`

## Success Criteria

- [ ] Backup created before squash
- [ ] No changes lost (diff with backup is empty)
- [ ] Single commit created with all changes (or commits properly grouped by topic) — any post-squash comment
      corrections must be amended into the squash commit, not left as separate commits
- [ ] Meaningful commit message (not "squashed commits")
- [ ] Backup removed after verification
- [ ] For ALL modified source code files: comments and documentation semantically verified against actual code
      behavior (applies to every squash, not only CONCURRENT_MODIFICATION cases)
- [ ] For each CONCURRENT_MODIFICATION flagged file: additional verification that rebase preserved both branch
      changes correctly, and any stale comments corrected and committed before proceeding
