# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** {{TIMESTAMP}}

## Resolution Fields (when completed)

When a task is completed, add one of these resolution patterns:

### Standard Completion (implemented)
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Completed:** {{TIMESTAMP}}
```

### Duplicate Task
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v{major}.{minor}-{original-task-name}
- **Completed:** {{TIMESTAMP}}
```

The `Duplicate Of` field contains the task ID that actually implemented the functionality.
This task's work was completed by that task's commit.

### Obsolete Task
```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** obsolete
- **Reason:** {why task is no longer needed}
- **Completed:** {{TIMESTAMP}}
```

## Resolution Types

| Resolution | When to Use | Commit? |
|------------|-------------|---------|
| `implemented` | Task was completed normally | Yes - commit has this task's ID |
| `duplicate` | Another task already did this work | No - use `Duplicate Of` task's commit |
| `obsolete` | Task is no longer needed (requirements changed) | No - just STATE.md update |

## Finding Commits for Duplicate Tasks

For duplicate tasks, the commit that resolved the functionality has the *original* task's ID,
not this task's ID. To find the resolving commit:

```bash
# 1. Read this task's STATE.md to get "Duplicate Of" value
# 2. Search for that task ID
git log --oneline --grep="Task ID: v{major}.{minor}-{original-task-name}"
```
