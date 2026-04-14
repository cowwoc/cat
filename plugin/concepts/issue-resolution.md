# Issue Resolution

How issues are marked complete and how to trace their resolving commits.

## Resolution Types

| Resolution | Description | Has Commit? | How to Find |
|------------|-------------|-------------|-------------|
| `implemented` | Issue closed normally | Yes | `git log -- .cat/issues/v{x}/v{x}.{y}/{issue-name}/` |
| `duplicate` | Another issue did this work | No | Check index.json `resolution` field |
| `obsolete` | No longer needed | No | No implementation commit |

**Status values:** `open`, `in-progress`, `closed` (only these three)

## Standard Completion (implemented)

When an issue is closed through normal execution:

1. Work is done and committed
2. index.json updated in the same commit (per M076)
3. index.json updated with `"resolution": "implemented"`

```bash
# Find commits for an issue via index.json history
git log --oneline -- .cat/issues/v1/v1.0/add-feature-x/
```

## Duplicate Issues

An issue is a **duplicate** when another issue already implemented the same functionality.

### When This Happens

1. Issue A and Issue B are created for similar problems
2. Issue A is executed and closed
3. When Issue B is started, investigation reveals Issue A already fixed it
4. Issue B is marked as duplicate of Issue A

### How to Mark a Duplicate

Update the duplicate issue's index.json:

```json
{
  "status": "closed",
  "resolution": "duplicate (v{major}.{minor}-{original-issue-name})",
  "dependencies": [],
  "blocks": []
}
```

**Example:**
```json
{
  "status": "closed",
  "resolution": "duplicate (v0.5-fix-multi-param-lambda)",
  "dependencies": [],
  "blocks": []
}
```

### Finding Commits for Duplicates

The duplicate issue has **no implementation commit**. The work was done by the original issue.

**To find the resolving commit:**

```bash
# 1. Read the duplicate issue's index.json
# 2. Get the "resolution" value (e.g., "duplicate (v0.5-fix-multi-param-lambda)")
# 3. Find commits for that original issue via index.json history
git log --oneline -- .cat/issues/v0/v0.5/fix-multi-param-lambda/
```

### Commit for Duplicate Resolution

When marking an issue as duplicate, commit only the index.json update:

```bash
git add .cat/issues/v{major}/v{major}.{minor}/{issue-name}/index.json
git commit -m "config: close duplicate issue {issue-name}

Duplicate of {original-issue-name} which was resolved in commit {hash}.
"
```

**Note:** Duplicate resolutions have no implementation commit - only the index.json update.

## Obsolete Issues

An issue is **obsolete** when it's no longer needed (requirements changed, feature removed, etc.).

### How to Mark Obsolete

```json
{
  "status": "closed",
  "resolution": "obsolete ({why issue is no longer needed})",
  "dependencies": [],
  "blocks": []
}
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
| "mark as obsolete" | Set `status: "closed"`, `resolution: "obsolete (...)"` in index.json |
| "mark as duplicate" | Set `status: "closed"`, `resolution: "duplicate (...)"` in index.json |

**Abort cleanup:** Release the issue lock, remove the worktree (`git worktree remove --force`),
and delete the branch. No index.json commit is needed on the target branch — the source branch
contains all in-progress changes, and deleting it reverts index.json automatically.

**A stopped issue returns to open state** - ready for future work.
Only issues that reach their goal (or are explicitly declared obsolete/duplicate) become closed.

## Tracing Issue Resolution

### Algorithm

To find what resolved an issue:

```
1. Read issue's index.json
2. Check "resolution" field:
   - If "implemented": git log -- .cat/issues/v{x}/v{x}.{y}/{issue-name}/
   - If "duplicate (...)": find commits for the referenced original issue
   - If "obsolete (...)": no implementation commit exists
```

### Script Example

```bash
#!/bin/bash
# find-issue-commit.sh <issue-path>

ISSUE_PATH="$1"
INDEX_FILE="$ISSUE_PATH/index.json"

# Extract resolution from JSON (no jq available; use grep/sed)
RESOLUTION=$(grep -oE '"resolution"[[:space:]]*:[[:space:]]*"[^"]*"' "$INDEX_FILE" \
  | sed 's/.*"resolution"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

case "$RESOLUTION" in
  implemented)
    # Find commits via index.json file history
    git log --oneline -- "$ISSUE_PATH/"
    ;;
  duplicate*)
    # Extract original issue reference from "duplicate (v0.5-issue-name)"
    DUP_REF=$(echo "$RESOLUTION" | sed 's/duplicate (\(.*\))/\1/')
    DUP_MAJOR=$(echo "$DUP_REF" | sed 's/v\([0-9]*\)\..*/\1/')
    DUP_MINOR=$(echo "$DUP_REF" | sed 's/v\([0-9]*\.[0-9]*\)-.*/\1/')
    DUP_NAME=$(echo "$DUP_REF" | sed 's/v[0-9]*\.[0-9]*-//')
    git log --oneline -- ".cat/issues/v${DUP_MAJOR}/v${DUP_MINOR}/${DUP_NAME}/"
    ;;
  obsolete*)
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
