# Workflow: Duplicate Task Handling

## When to Load

Load this workflow when during exploration a task is discovered to be a **duplicate** -
another task already implemented the same functionality.

## Signs of a Duplicate

1. Investigation reveals the functionality already exists
2. Tests for this task's scenarios already pass
3. Another task addressed the same root cause

## Handling Process

### 1. Stop Execution

Skip worktree creation and implementation phases entirely.

### 2. Verify Duplicate

Test the specific scenarios from this task's PLAN.md to confirm they work:

```bash
# Run tests related to this task's scenarios
# If all pass, this is confirmed duplicate
```

### 3. Identify Original

Find which task/commit implemented the fix:

```bash
# Search for when functionality was added
git log --oneline --grep="<related keywords>"

# Check other completed tasks in same version
find .claude/cat/issues/v*/v*.*/task -name "STATE.md" -exec grep -l "completed" {} \;
```

### 4. Update STATE.md

```yaml
- **Status:** completed
- **Progress:** 100%
- **Resolution:** duplicate
- **Duplicate Of:** v{major}.{minor}-{original-task-name}
- **Completed:** {date}
```

### 5. Commit STATE.md Only

Duplicate tasks do NOT get a `Task ID:` footer (reserved for implementation commits):

```bash
git add .claude/cat/issues/v{major}/v{major}.{minor}/{task-name}/STATE.md
git commit -m "$(cat <<'EOF'
config: close duplicate task {task-name}

Duplicate of {original-task} (commit {hash}).
Verification confirmed all scenarios from PLAN.md pass.
EOF
)"
```

### 6. Release Lock and Cleanup

Same as normal task completion:

```bash
# Release task lock
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" release "${CLAUDE_PROJECT_DIR}" "$TASK_ID" "${CLAUDE_SESSION_ID}"

# Remove worktree if created
git worktree remove "$WORKTREE_PATH" --force 2>/dev/null || true
```

### 7. Offer Next Task

Continue to next executable task as normal.

---

## Resolution Values

| Resolution | Meaning |
|------------|---------|
| `implemented` | Task completed with code changes |
| `duplicate` | Functionality already exists |
| `obsolete` | Task no longer needed |

---

## When NOT to Load

- Normal task execution
- Task that modifies existing functionality
- Task that extends existing implementation
