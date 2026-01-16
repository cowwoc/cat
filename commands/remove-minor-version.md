---
name: cat:remove-minor-version
description: Remove a minor version
argument-hint: "[major.minor]"
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

Remove a minor version from a major version. Validates that no incomplete tasks exist before
removal and confirms with the user.

</objective>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No planning structure found." && exit 1
```

</step>

<step name="select_version">

**Determine minor version to remove:**

**If $ARGUMENTS provided:**
- Parse as `major.minor` format
- Validate version exists

**If $ARGUMENTS empty:**

List all minor versions:

```bash
# Find all minor versions with task counts
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | while read d; do
    VERSION=$(basename "$d" | sed 's/v//')
    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    TASK_COUNT=$(find "$d/task" -maxdepth 1 -type d 2>/dev/null | wc -l)
    TASK_COUNT=$((TASK_COUNT - 1))  # Subtract 1 for the task directory itself
    echo "$MAJOR.$MINOR ($TASK_COUNT tasks)"
done | sort -V
```

Use AskUserQuestion:
- header: "Select Minor Version"
- question: "Which minor version do you want to remove?"
- options: [List of versions] + "Cancel"

If "Cancel" -> exit command.

</step>

<step name="validate">

**Validate minor version can be removed:**

```bash
MINOR_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR"

# Check version exists
[ ! -d "$MINOR_PATH" ] && echo "ERROR: Minor version does not exist" && exit 1
```

**Check for incomplete tasks:**

```bash
# Find any tasks that are not completed
INCOMPLETE=$(find "$MINOR_PATH/task" -name "STATE.md" -exec grep -l "Status: pending\|Status: in-progress" {} \; 2>/dev/null)
```

If incomplete tasks exist:

```
ERROR: Cannot remove minor version with incomplete tasks.

Incomplete tasks:
{list of incomplete tasks}

Options:
1. Complete all tasks first with /cat:execute-task
2. Remove individual tasks with /cat:remove-task
3. Force removal by setting all task statuses to 'completed'
```

Use AskUserQuestion:
- header: "Incomplete Tasks"
- question: "This minor version has incomplete tasks. What would you like to do?"
- options:
  - "Remove anyway" - Force remove all tasks
  - "Cancel" - Stop removal

If "Cancel" -> exit command.

</step>

<step name="check_dependencies">

**Check if later minor versions depend on this completing:**

Minor versions implicitly depend on previous minor versions within the same major.

If this is not the last minor version:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "Minor version {major}.{minor+1} and later implicitly depend on this version completing. Removing may affect the roadmap. Continue?"
- options:
  - "Yes, remove anyway" - Proceed
  - "No, cancel" - Abort

</step>

<step name="confirm">

**Final confirmation:**

Count files to be removed:

```bash
TASK_COUNT=$(find "$MINOR_PATH/task" -maxdepth 1 -type d 2>/dev/null | wc -l)
TASK_COUNT=$((TASK_COUNT - 1))
```

Use AskUserQuestion:
- header: "Confirm Removal"
- question: "Remove minor version {major}.{minor}?\n\nThis will delete:\n- {task_count} tasks\n- All STATE.md, PLAN.md files"
- options:
  - "Yes, remove it" - Proceed
  - "No, cancel" - Abort

If "No, cancel" -> exit command.

</step>

<step name="remove">

**Remove minor version directory:**

```bash
rm -rf "$MINOR_PATH"
```

</step>

<step name="update_roadmap">

**Update ROADMAP.md:**

Remove the entry for this minor version.

</step>

<step name="update_parent">

**Update parent major STATE.md:**

Recalculate progress without the removed minor version.

</step>

<step name="commit">

**Commit removal:**

```bash
git add -A ".claude/cat/v$MAJOR/"
git add ".claude/cat/ROADMAP.md"
git commit -m "$(cat <<'EOF'
docs: remove minor version {major}.{minor}

Minor version removed by user request.
Removed {task_count} tasks.
EOF
)"
```

</step>

<step name="done">

**Present completion:**

```
Minor version removed:

- Version: {major}.{minor}
- Tasks removed: {task_count}

---

Use `/cat:status` to see current state.

---
```

</step>

</process>

<success_criteria>

- [ ] Minor version identified
- [ ] Incomplete tasks handled (blocked or force-removed)
- [ ] Dependencies checked and warned
- [ ] User confirmation obtained
- [ ] Minor version directory removed
- [ ] ROADMAP.md updated
- [ ] Parent STATE.md updated
- [ ] Removal committed to git

</success_criteria>
