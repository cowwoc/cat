# Task Resolution

How tasks are marked complete and how to trace their resolving commits.

## Resolution Types

| Resolution | Description | Has Commit? | Commit Footer |
|------------|-------------|-------------|---------------|
| `implemented` | Task completed normally | Yes | `Task ID: v{x}.{y}-{task-name}` |
| `duplicate` | Another task did this work | No | Use original task's commit |
| `obsolete` | No longer needed | No | None |

## Standard Completion (implemented)

When a task is completed through normal execution:

1. Work is done and committed
2. Commit message footer contains: `Task ID: v{major}.{minor}-{task-name}`
3. STATE.md updated with `Resolution: implemented`

```bash
# Find the commit
git log --oneline --grep="Task ID: v1.0-add-feature-x"
```

## Duplicate Tasks

A task is a **duplicate** when another task already implemented the same functionality.

### When This Happens

1. Task A and Task B are created for similar issues
2. Task A is executed and completed
3. When Task B is started, investigation reveals Task A already fixed it
4. Task B is marked as duplicate of Task A

### How to Mark a Duplicate

Update the duplicate task's STATE.md:

```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v{major}.{minor}-{original-task-name}
- **Completed:** {date}
```

**Example:**
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v0.5-fix-multi-param-lambda
- **Completed:** 2026-01-14
```

### Finding Commits for Duplicates

The duplicate task has **no commit with its Task ID**. The work was done by the original task.

**To find the resolving commit:**

```bash
# 1. Read the duplicate task's STATE.md
# 2. Get the "Duplicate Of" value (e.g., v0.5-fix-multi-param-lambda)
# 3. Search for that task ID
git log --oneline --grep="Task ID: v0.5-fix-multi-param-lambda"
```

### Commit for Duplicate Resolution

When marking a task as duplicate, commit only the STATE.md update:

```bash
git add .claude/cat/v{major}/v{major}.{minor}/{task-name}/STATE.md
git commit -m "config: close duplicate task {task-name}

Duplicate of {original-task-name} which was resolved in commit {hash}.
"
```

**Note:** This commit does NOT include `Task ID:` footer because there's no implementation.

## Obsolete Tasks

A task is **obsolete** when it's no longer needed (requirements changed, feature removed, etc.).

### How to Mark Obsolete

```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** obsolete
- **Reason:** {why task is no longer needed}
- **Completed:** {date}
```

### Commit for Obsolete Resolution

```bash
git commit -m "config: close obsolete task {task-name}

{Reason why task is no longer needed}
"
```

## Tracing Task Resolution

### Algorithm

To find what resolved a task:

```
1. Read task's STATE.md
2. Check Resolution field:
   - If "implemented": grep for "Task ID: v{x}.{y}-{this-task}"
   - If "duplicate": grep for "Task ID: {Duplicate Of value}"
   - If "obsolete": no implementation commit exists
```

### Script Example

```bash
#!/bin/bash
# find-task-commit.sh <task-path>

TASK_PATH="$1"
STATE_FILE="$TASK_PATH/STATE.md"

RESOLUTION=$(grep "Resolution:" "$STATE_FILE" | sed 's/.*Resolution: *//')
TASK_NAME=$(basename "$TASK_PATH")
MINOR_PATH=$(dirname "$TASK_PATH" | xargs dirname)
MINOR=$(basename "$MINOR_PATH")
MAJOR_PATH=$(dirname "$MINOR_PATH")
MAJOR=$(basename "$MAJOR_PATH" | sed 's/v//')

case "$RESOLUTION" in
  implemented)
    TASK_ID="v${MAJOR}.${MINOR#v*}-${TASK_NAME}"
    git log --oneline --grep="Task ID: $TASK_ID"
    ;;
  duplicate)
    DUPLICATE_OF=$(grep "Duplicate Of:" "$STATE_FILE" | sed 's/.*Duplicate Of: *//')
    git log --oneline --grep="Task ID: $DUPLICATE_OF"
    ;;
  obsolete)
    echo "Task was obsolete - no implementation commit"
    ;;
  *)
    echo "Unknown resolution: $RESOLUTION"
    ;;
esac
```

## Validation Task Completion (M126)

**MANDATORY**: Validation tasks with non-zero errors are NOT complete until either:
1. **All errors are resolved** (0 errors), OR
2. **New tasks are created** for each remaining error category

Validation tasks (like `validate-spring-framework-parsing`) exist to verify parser/tool behavior
against real-world codebases. When validation reveals errors:

| Scenario | Action |
|----------|--------|
| 0 errors | Mark task complete with `Resolution: implemented` |
| N errors remain | Create new tasks for error categories, mark validation task blocked by them |

**Anti-pattern:** Documenting remaining errors as "known limitations" and marking complete.

**Correct pattern:**
1. Run validation, find N errors
2. Categorize errors by root cause
3. Create new task for each error category
4. Update validation task dependencies to include new tasks
5. Validation task remains pending/blocked until new tasks complete
6. Re-run validation after fixes

**User decides** what constitutes acceptable limitations. Never unilaterally close a validation task
with errors remaining - create the tasks and let user prioritize or close them as won't-fix.

## Common Patterns

### Same Error, Different Root Cause

Two tasks may report the same error but have different root causes:

```
Task A: "Error X" caused by problem P1 → Fix P1
Task B: "Error X" caused by problem P2 → NOT a duplicate (different root cause)
```

**Verify before marking duplicate:** Test the specific scenarios from both tasks.

### Superseded vs Duplicate

| Scenario | Resolution |
|----------|------------|
| Task B does exactly what Task A did | `duplicate` of Task A |
| Task B's scope was absorbed into Task A | `duplicate` of Task A |
| Task A was split into Tasks B, C, D | Task A is `obsolete`, B/C/D are `implemented` |
