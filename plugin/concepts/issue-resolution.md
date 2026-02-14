# Issue Resolution

How issues are marked complete and how to trace their resolving commits.

## Resolution Types

| Resolution | Description | Has Commit? | How to Find |
|------------|-------------|-------------|-------------|
| `implemented` | Issue completed normally | Yes | `git log -- .claude/cat/issues/v{x}/v{x}.{y}/{issue-name}/` |
| `duplicate` | Another issue did this work | No | Check STATE.md "Duplicate Of" field |
| `obsolete` | No longer needed | No | No implementation commit |

**Status values:** `open`, `in-progress`, `closed` (only these three)

## Standard Completion (implemented)

When an issue is completed through normal execution:

1. Work is done and committed
2. STATE.md updated in the same commit (per M076)
3. STATE.md updated with `Resolution: implemented`

```bash
# Find commits for an issue via STATE.md history
git log --oneline -- .claude/cat/issues/v1/v1.0/add-feature-x/
```

## Duplicate Issues

An issue is a **duplicate** when another issue already implemented the same functionality.

### When This Happens

1. Issue A and Issue B are created for similar problems
2. Issue A is executed and completed
3. When Issue B is started, investigation reveals Issue A already fixed it
4. Issue B is marked as duplicate of Issue A

### How to Mark a Duplicate

Update the duplicate issue's STATE.md:

```yaml
- **Status:** closed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v{major}.{minor}-{original-issue-name}
- **Completed:** {date}
```

**Example:**
```yaml
- **Status:** closed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v0.5-fix-multi-param-lambda
- **Completed:** 2026-01-14
```

### Finding Commits for Duplicates

The duplicate issue has **no implementation commit**. The work was done by the original issue.

**To find the resolving commit:**

```bash
# 1. Read the duplicate issue's STATE.md
# 2. Get the "Duplicate Of" value (e.g., v0.5-fix-multi-param-lambda)
# 3. Find commits for that original issue via STATE.md history
git log --oneline -- .claude/cat/issues/v0/v0.5/fix-multi-param-lambda/
```

### Commit for Duplicate Resolution

When marking an issue as duplicate, commit only the STATE.md update:

```bash
git add .claude/cat/issues/v{major}/v{major}.{minor}/{issue-name}/STATE.md
git commit -m "config: close duplicate issue {issue-name}

Duplicate of {original-issue-name} which was resolved in commit {hash}.
"
```

**Note:** Duplicate resolutions have no implementation commit - only the STATE.md update.

## Obsolete Issues

An issue is **obsolete** when it's no longer needed (requirements changed, feature removed, etc.).

### How to Mark Obsolete

```yaml
- **Status:** closed
- **Progress:** 100%
- **Resolution:** obsolete
- **Reason:** {why issue is no longer needed}
- **Completed:** {date}
```

### Commit for Obsolete Resolution

```bash
git commit -m "config: close obsolete issue {issue-name}

{Reason why issue is no longer needed}
"
```

## Stopping Work on an Issue

**CRITICAL:** When user says "abort the issue" or "stop the issue", this means
"stop working now, restore to open" - NOT "mark as closed".

| User Says | Action |
|-----------|--------|
| "abort/stop the issue" | Release lock, delete worktree and branch |
| "mark as obsolete" | Set Status: `closed`, Resolution: `obsolete` |
| "mark as duplicate" | Set Status: `closed`, Resolution: `duplicate` |

**Abort cleanup:** Release the issue lock, remove the worktree (`git worktree remove --force`),
and delete the branch. No STATE.md commit is needed on the base branch — the worktree branch
contains all in-progress changes, and deleting it reverts STATE.md automatically.

**A stopped issue returns to open state** - ready for future work.
Only issues that reach their goal (or are explicitly declared obsolete/duplicate) become closed.

## Tracing Issue Resolution

### Algorithm

To find what resolved an issue:

```
1. Read issue's STATE.md
2. Check Resolution field:
   - If "implemented": git log -- .claude/cat/issues/v{x}/v{x}.{y}/{issue-name}/
   - If "duplicate": find commits for the "Duplicate Of" issue
   - If "obsolete": no implementation commit exists
```

### Script Example

```bash
#!/bin/bash
# find-issue-commit.sh <issue-path>

ISSUE_PATH="$1"
STATE_FILE="$ISSUE_PATH/STATE.md"

RESOLUTION=$(grep "Resolution:" "$STATE_FILE" | sed 's/.*Resolution: *//')
ISSUE_NAME=$(basename "$ISSUE_PATH")
MINOR_PATH=$(dirname "$ISSUE_PATH" | xargs dirname)
MINOR=$(basename "$MINOR_PATH")
MAJOR_PATH=$(dirname "$MINOR_PATH")
MAJOR=$(basename "$MAJOR_PATH" | sed 's/v//')

case "$RESOLUTION" in
  implemented)
    # Find commits via STATE.md file history
    git log --oneline -- "$ISSUE_PATH/"
    ;;
  duplicate)
    DUPLICATE_OF=$(grep "Duplicate Of:" "$STATE_FILE" | sed 's/.*Duplicate Of: *//')
    # Parse version and issue name from duplicate reference
    DUP_MAJOR=$(echo "$DUPLICATE_OF" | sed 's/v\([0-9]*\)\..*/\1/')
    DUP_MINOR=$(echo "$DUPLICATE_OF" | sed 's/v\([0-9]*\.[0-9]*\)-.*/\1/')
    DUP_NAME=$(echo "$DUPLICATE_OF" | sed 's/v[0-9]*\.[0-9]*-//')
    git log --oneline -- ".claude/cat/issues/v${DUP_MAJOR}/v${DUP_MINOR}/${DUP_NAME}/"
    ;;
  obsolete)
    echo "Issue was obsolete - no implementation commit"
    ;;
  *)
    echo "Unknown resolution: $RESOLUTION"
    ;;
esac
```

## Validation Issue Completion

**MANDATORY**: Validation issues with non-zero errors are NOT complete until either:
1. **All errors are resolved** (0 errors), OR
2. **New issues are created** for each remaining error category

Validation issues (like `validate-spring-framework-parsing`) exist to verify parser/tool behavior
against real-world codebases. When validation reveals errors:

| Scenario | Action |
|----------|--------|
| 0 errors | Mark issue complete with `Resolution: implemented` |
| N errors remain | Create new issues for error categories, mark validation issue blocked by them |

**Anti-pattern:** Documenting remaining errors as "known limitations" and marking complete.

**Correct pattern:**
1. Run validation, find N errors
2. Categorize errors by root cause
3. Create new issue for each error category
4. Update validation issue dependencies to include new issues
5. Validation issue remains pending/blocked until new issues complete
6. Re-run validation after fixes

**User decides** what constitutes acceptable limitations. Never unilaterally close a validation issue
with errors remaining - create the issues and let user prioritize or close them as won't-fix.

## Common Patterns

### Same Error, Different Root Cause

Two issues may report the same error but have different root causes:

```
Issue A: "Error X" caused by problem P1 → Fix P1
Issue B: "Error X" caused by problem P2 → NOT a duplicate (different root cause)
```

**Verify before marking duplicate:** Test the specific scenarios from both issues.

### Superseded vs Duplicate

| Scenario | Resolution |
|----------|------------|
| Issue B does exactly what Issue A did | `duplicate` of Issue A |
| Issue B's scope was absorbed into Issue A | `duplicate` of Issue A |
| Issue A was split into Issues B, C, D | Issue A is `obsolete`, B/C/D are `implemented` |
