---
name: cat:remove
description: Remove task or version
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

Unified command for removing tasks or versions from the CAT planning structure. Routes to the
appropriate workflow based on user selection.

</objective>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No planning structure found." && exit 1
```

</step>

<step name="select_type">

**Ask what to remove:**

Use AskUserQuestion:
- header: "Remove What?"
- question: "What would you like to remove?"
- options:
  - "Task" - Remove a task from a minor version
  - "Minor version" - Remove a minor version from a major
  - "Major version" - Remove an entire major version

</step>

<step name="route">

**Route based on selection:**

**If "Task":**
- Continue to remove_task workflow (step: task_select)

**If "Minor version":**
- Continue to remove_minor workflow (step: minor_select)

**If "Major version":**
- Continue to remove_major workflow (step: major_select)

</step>

<!-- ========== TASK REMOVAL WORKFLOW ========== -->

<step name="task_select">

**Determine task to remove:**

List all tasks:

```bash
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

<step name="task_validate">

**Validate task can be removed:**

```bash
TASK_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/$TASK_NAME"

[ ! -d "$TASK_PATH" ] && echo "ERROR: Task does not exist" && exit 1

STATUS=$(grep "Status:" "$TASK_PATH/STATE.md" | sed 's/.*: //')
```

**Block removal if in-progress:**

If status is `in-progress`:

```
ERROR: Cannot remove task that is in-progress.

Current status: in-progress
Progress: {progress}%

Options:
1. Complete the task first with /cat:work
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

<step name="task_check_dependencies">

**Check if other tasks depend on this one:**

```bash
find .claude/cat/v*/v*.* -mindepth 1 -maxdepth 1 -type d ! -name "v*" \
    -exec grep -l "Dependencies:.*$TASK_NAME" {}/STATE.md \; 2>/dev/null
```

If dependents found:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "The following tasks depend on this task:\n\n[list]\n\nRemoving will leave these tasks with unmet dependencies. Continue?"
- options:
  - "Yes, remove anyway" - Proceed
  - "No, keep it" - Cancel removal

</step>

<step name="task_confirm">

**Final confirmation:**

Use AskUserQuestion:
- header: "Confirm Removal"
- question: "Remove task '{task-name}' from {major}.{minor}?\n\nThis will delete:\n- STATE.md\n- PLAN.md"
- options:
  - "Yes, remove it" - Proceed
  - "No, cancel" - Abort

If "No, cancel" -> exit command.

</step>

<step name="task_remove">

**Remove task directory:**

```bash
rm -rf "$TASK_PATH"
```

**Update tasks that depended on this task:**

```bash
find .claude/cat/v$MAJOR/v$MAJOR.$MINOR -mindepth 1 -maxdepth 1 -type d ! -name "v*" | while read d; do
    if grep -q "Dependencies:.*$TASK_NAME" "$d/STATE.md" 2>/dev/null; then
        sed -i "s/$TASK_NAME, //g; s/, $TASK_NAME//g; s/$TASK_NAME//g" "$d/STATE.md"
    fi
done
```

</step>

<step name="task_update_parent">

**Update parent minor STATE.md:**

Recalculate progress without the removed task.

</step>

<step name="task_commit">

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

<step name="task_done">

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

<!-- ========== MINOR VERSION REMOVAL WORKFLOW ========== -->

<step name="minor_select">

**Determine minor version to remove:**

List all minor versions:

```bash
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | while read d; do
    VERSION=$(basename "$d" | sed 's/v//')
    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    TASK_COUNT=$(find "$d" -mindepth 1 -maxdepth 1 -type d ! -name "task" 2>/dev/null | wc -l)
    echo "$MAJOR.$MINOR ($TASK_COUNT tasks)"
done | sort -V
```

Use AskUserQuestion:
- header: "Select Minor Version"
- question: "Which minor version do you want to remove?"
- options: [List of versions] + "Cancel"

If "Cancel" -> exit command.

</step>

<step name="minor_validate">

**Validate minor version can be removed:**

```bash
MINOR_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR"

[ ! -d "$MINOR_PATH" ] && echo "ERROR: Minor version does not exist" && exit 1
```

**Check for incomplete tasks:**

```bash
INCOMPLETE=$(find "$MINOR_PATH" -mindepth 1 -maxdepth 1 -type d ! -name "task" \
    -exec test -f {}/STATE.md \; \
    -exec grep -l "Status: pending\|Status: in-progress" {}/STATE.md \; 2>/dev/null)
```

If incomplete tasks exist:

Use AskUserQuestion:
- header: "Incomplete Tasks"
- question: "This minor version has incomplete tasks:\n\n{list}\n\nWhat would you like to do?"
- options:
  - "Remove anyway" - Force remove all tasks
  - "Cancel" - Stop removal

If "Cancel" -> exit command.

</step>

<step name="minor_check_dependencies">

**Check if later minor versions depend on this completing:**

If this is not the last minor version:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "Minor version {major}.{minor+1} and later implicitly depend on this version. Removing may affect the roadmap. Continue?"
- options:
  - "Yes, remove anyway" - Proceed
  - "No, cancel" - Abort

</step>

<step name="minor_confirm">

**Final confirmation:**

```bash
TASK_COUNT=$(find "$MINOR_PATH" -mindepth 1 -maxdepth 1 -type d ! -name "task" 2>/dev/null | wc -l)
```

Use AskUserQuestion:
- header: "Confirm Removal"
- question: "Remove minor version {major}.{minor}?\n\nThis will delete:\n- {task_count} tasks\n- All STATE.md, PLAN.md files"
- options:
  - "Yes, remove it" - Proceed
  - "No, cancel" - Abort

If "No, cancel" -> exit command.

</step>

<step name="minor_remove">

**Remove minor version directory:**

```bash
rm -rf "$MINOR_PATH"
```

</step>

<step name="minor_update_roadmap">

**Update ROADMAP.md:**

Remove the entry for this minor version.

</step>

<step name="minor_update_parent">

**Update parent major STATE.md:**

Recalculate progress without the removed minor version.

</step>

<step name="minor_commit">

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

<step name="minor_done">

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

<!-- ========== MAJOR VERSION REMOVAL WORKFLOW ========== -->

<step name="major_select">

**Determine major version to remove:**

```bash
[ -z "$(ls -d .claude/cat/v[0-9]* 2>/dev/null)" ] && echo "ERROR: No major versions exist." && exit 1
```

List all major versions:

```bash
for d in .claude/cat/v[0-9]*/; do
    MAJOR=$(basename "$d" | sed 's/v//')
    MINOR_COUNT=$(ls -1d "$d"v$MAJOR.[0-9]* 2>/dev/null | wc -l)
    TASK_COUNT=$(find "$d" -mindepth 2 -maxdepth 2 -type d ! -name "v*" ! -name "task" 2>/dev/null | wc -l)
    STATUS=$(grep "Status:" "$d/STATE.md" 2>/dev/null | sed 's/.*: //' || echo "unknown")
    echo "Major $MAJOR: $MINOR_COUNT minor versions, $TASK_COUNT tasks ($STATUS)"
done
```

Use AskUserQuestion:
- header: "Select Major Version"
- question: "Which major version do you want to remove?"
- options: [List of majors with stats] + "Cancel"

If "Cancel" -> exit command.

</step>

<step name="major_validate">

**Validate major version can be removed:**

```bash
MAJOR_PATH=".claude/cat/v$MAJOR"

[ ! -d "$MAJOR_PATH" ] && echo "ERROR: Major version does not exist" && exit 1
```

**Check for incomplete work:**

```bash
INCOMPLETE=$(find "$MAJOR_PATH" -mindepth 2 -maxdepth 2 -type d ! -name "v*" ! -name "task" | while read d; do
    [ -f "$d/STATE.md" ] && grep -q "Status: pending\|Status: in-progress" "$d/STATE.md" && echo "$d"
done)
```

If incomplete tasks exist:

Use AskUserQuestion:
- header: "Incomplete Work"
- question: "Major version {major} has incomplete work:\n\n{list}\n\nWhat would you like to do?"
- options:
  - "Force remove everything" - Delete despite incomplete work
  - "Cancel" - Stop removal

If "Cancel" -> exit command.

</step>

<step name="major_check_dependencies">

**Check if later major versions depend on this:**

```bash
LATER_MAJORS=$(find .claude/cat -name "STATE.md" -path ".claude/cat/v[$(($MAJOR+1))-9]*/STATE.md" \
    -exec grep -l "Dependencies:.*$MAJOR" {} \; 2>/dev/null)
```

If dependents found:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "Later major versions may depend on this one:\n\n[list]\n\nRemoving may affect project structure. Continue?"
- options:
  - "Yes, remove anyway" - Proceed
  - "No, cancel" - Abort

</step>

<step name="major_gather_stats">

**Gather removal statistics:**

```bash
MINOR_COUNT=$(ls -1d "$MAJOR_PATH"/v$MAJOR.[0-9]* 2>/dev/null | wc -l)
TASK_COUNT=$(find "$MAJOR_PATH" -mindepth 2 -maxdepth 2 -type d ! -name "v*" ! -name "task" 2>/dev/null | wc -l)
```

</step>

<step name="major_confirm">

**Final confirmation (extra serious for major version):**

Use AskUserQuestion:
- header: "CONFIRM MAJOR VERSION REMOVAL"
- question: "You are about to remove Major Version {major}.\n\nThis will permanently delete:\n- {minor_count} minor versions\n- {task_count} tasks\n- All associated STATE.md, PLAN.md, CHANGELOG.md files\n\nThis action cannot be undone (except via git). Are you absolutely sure?"
- options:
  - "Yes, permanently remove Major {major}" - Proceed with removal
  - "No, cancel" - Abort removal

If "No, cancel" -> exit command.

</step>

<step name="major_remove">

**Remove major version directory:**

```bash
rm -rf "$MAJOR_PATH"
```

</step>

<step name="major_update_roadmap">

**Update ROADMAP.md:**

Remove the entire section for this major version.

</step>

<step name="major_commit">

**Commit removal:**

```bash
git add -A ".claude/cat/"
git commit -m "$(cat <<'EOF'
docs: remove major version {major}

Major version removed by user request.
Removed {minor_count} minor versions and {task_count} tasks.
EOF
)"
```

</step>

<step name="major_done">

**Present completion:**

```
Major version removed:

- Version: Major {major}
- Minor versions removed: {minor_count}
- Tasks removed: {task_count}

---

Use `/cat:status` to see current state.

**Note:** You can recover this via git if needed:
```bash
git revert HEAD  # Undo the removal commit
```

---
```

</step>

</process>

<success_criteria>

**For Task:**
- [ ] Task identified
- [ ] In-progress tasks blocked from removal
- [ ] Dependencies checked and warned
- [ ] User confirmation obtained
- [ ] Task directory removed
- [ ] Parent STATE.md updated
- [ ] Removal committed to git

**For Minor Version:**
- [ ] Minor version identified
- [ ] Incomplete tasks handled (blocked or force-removed)
- [ ] Dependencies checked and warned
- [ ] User confirmation obtained
- [ ] Minor version directory removed
- [ ] ROADMAP.md updated
- [ ] Parent STATE.md updated
- [ ] Removal committed to git

**For Major Version:**
- [ ] Major version identified
- [ ] Incomplete work handled (blocked or force-removed)
- [ ] Dependencies checked and warned
- [ ] Statistics gathered for user awareness
- [ ] User confirmation obtained (with extra warning)
- [ ] Major version directory removed
- [ ] ROADMAP.md updated
- [ ] Removal committed to git
- [ ] Recovery instructions provided

</success_criteria>
