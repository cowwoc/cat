---
name: cat:remove-task
description: Remove a task
argument-hint: "[major.minor-task-name]"
model: haiku
context: fork
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
---

<objective>

Remove a task from a minor version. Validates that no work is in progress before removal and
confirms with the user.

</objective>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No planning structure found." && exit 1
```

</step>

<step name="select_task">

**Determine task to remove:**

**If $ARGUMENTS provided:**
- Parse as `major.minor-task-name` format (e.g., `1.0-parse-tokens`)
- Validate task exists

**If $ARGUMENTS empty:**

List all tasks:

```bash
# Find all tasks with their status (tasks are directories under v{n}.{m}/ that aren't named v*)
find .claude/cat/v*/v*.* -mindepth 1 -maxdepth 1 -type d ! -name "v*" 2>/dev/null | while read d; do
    [ -f "$d/STATE.md" ] || continue
    TASK_NAME=$(basename "$d")
    MAJOR=$(echo "$d" | sed 's|.*/v\([0-9]*\)/v[0-9]*\.[0-9]*/.*|\1|')
    MINOR=$(echo "$d" | sed 's|.*/v[0-9]*/v[0-9]*\.\([0-9]*\)/.*|\1|')
    STATUS=$(grep "Status:" "$d/STATE.md" | sed 's/.*: //')
    echo "$MAJOR.$MINOR-$TASK_NAME ($STATUS)"
done
```

Use AskUserQuestion:
- header: "Select Task"
- question: "Which task do you want to remove?"
- options: [List of tasks with status] + "Cancel"

If "Cancel" -> exit command.

</step>

<step name="validate">

**Validate task can be removed:**

```bash
TASK_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/$TASK_NAME"

# Check task exists
[ ! -d "$TASK_PATH" ] && echo "ERROR: Task does not exist" && exit 1

# Check status
STATUS=$(grep "Status:" "$TASK_PATH/STATE.md" | sed 's/.*: //')
```

**Block removal if in-progress:**

If status is `in-progress`:

```
ERROR: Cannot remove task that is in-progress.

Current status: in-progress
Progress: [==========>         ] {progress}%

Options:
1. Complete the task first with /cat:execute-task
2. Manually reset status to 'pending' in STATE.md if you want to discard work
```

Exit command.

**Warn if completed:**

If status is `completed`:

Use AskUserQuestion:
- header: "Warning"
- question: "This task is already completed. Removing it will lose the recorded work. Continue?"
- options:
  - "Yes, remove anyway" - Proceed with removal
  - "No, keep it" - Cancel removal

If "No, keep it" -> exit command.

</step>

<step name="check_dependencies">

**Check if other tasks depend on this one:**

```bash
# Find tasks that list this task as a dependency
find .claude/cat/v*/v*.* -mindepth 1 -maxdepth 1 -type d ! -name "v*" -exec grep -l "Dependencies:.*$TASK_NAME" {}/STATE.md \; 2>/dev/null
```

If dependents found:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "The following tasks depend on this task:\n\n[list]\n\nRemoving will leave these tasks with unmet dependencies. Continue?"
- options:
  - "Yes, remove anyway" - Proceed, dependent tasks will be blocked
  - "No, keep it" - Cancel removal

</step>

<step name="confirm">

**Final confirmation:**

Use AskUserQuestion:
- header: "Confirm Removal"
- question: "Remove task '{task-name}' from {major}.{minor}?\n\nThis will delete:\n- STATE.md\n- PLAN.md"
- options:
  - "Yes, remove it" - Proceed
  - "No, cancel" - Abort

If "No, cancel" -> exit command.

</step>

<step name="remove">

**Remove task directory:**

```bash
rm -rf "$TASK_PATH"
```

</step>

<step name="update_dependents">

**Update tasks that depended on this task:**

For each task in the same minor version:
- Check if their Dependencies list includes the removed task
- If so, remove it from their Dependencies list in STATE.md
- If the task was blocking them and now has no unmet dependencies, it becomes executable

```bash
# Find and update tasks that listed this as a dependency
find .claude/cat/v$MAJOR/v$MAJOR.$MINOR -mindepth 1 -maxdepth 1 -type d ! -name "v*" | while read d; do
    if grep -q "Dependencies:.*$TASK_NAME" "$d/STATE.md" 2>/dev/null; then
        # Remove this task from Dependencies list
        sed -i "s/$TASK_NAME, //g; s/, $TASK_NAME//g; s/$TASK_NAME//g" "$d/STATE.md"
    fi
done
```

</step>

<step name="update_parent">

**Update parent minor STATE.md:**

Recalculate progress without the removed task.

</step>

<step name="commit">

**Commit removal:**

```bash
git add -A ".claude/cat/v$MAJOR/v$MAJOR.$MINOR/"
git commit -m "$(cat <<'EOF'
docs: remove task {task-name} from {major}.{minor}

Task removed by user request.
EOF
)"
```

</step>

<step name="done">

**Present completion:**

```
Task removed:

- Task: {task-name}
- Version: {major}.{minor}

---

Use `/cat:status` to see current state.

---
```

</step>

</process>

<success_criteria>

- [ ] Task identified
- [ ] In-progress tasks blocked from removal
- [ ] Dependencies checked and warned
- [ ] User confirmation obtained
- [ ] Task directory removed
- [ ] Parent STATE.md updated
- [ ] Removal committed to git

</success_criteria>
