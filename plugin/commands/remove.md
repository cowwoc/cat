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
  - "Task" - Remove a task from a minor or patch version
  - "Patch version" - Remove a patch version from a minor
  - "Minor version" - Remove a minor version from a major
  - "Major version" - Remove an entire major version

</step>

<step name="route">

**Route based on selection:**

**If "Task":**
- Continue to remove_task workflow (step: task_select)

**If "Patch version":**
- Set VERSION_TYPE="patch", PARENT_TYPE="minor"
- Continue to unified version removal workflow (step: version_select)

**If "Minor version":**
- Set VERSION_TYPE="minor", PARENT_TYPE="major"
- Continue to unified version removal workflow (step: version_select)

**If "Major version":**
- Set VERSION_TYPE="major", PARENT_TYPE="none"
- Continue to unified version removal workflow (step: version_select)

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

**Update parent minor STATE.md - remove task from list:**

```bash
VERSION_STATE=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/STATE.md"

# Remove task from Tasks Pending section
sed -i "/^- $TASK_NAME$/d" "$VERSION_STATE"

# Also remove from Tasks Completed if present
sed -i "/^| $TASK_NAME |/d" "$VERSION_STATE"

# Recalculate progress
TOTAL_TASKS=$(grep -c "^- " "$VERSION_STATE" 2>/dev/null || echo 0)
COMPLETED_TASKS=$(grep -c "^| .* | completed |" "$VERSION_STATE" 2>/dev/null || echo 0)
if [ "$TOTAL_TASKS" -gt 0 ]; then
  PROGRESS=$((COMPLETED_TASKS * 100 / TOTAL_TASKS))
else
  PROGRESS=0
fi
sed -i "s/Progress:.*$/Progress:** $PROGRESS%/" "$VERSION_STATE"

# Verify task removed
! grep -q "^- $TASK_NAME$" "$VERSION_STATE" || echo "ERROR: Task not removed from STATE.md"
```

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

<!-- ========== UNIFIED VERSION REMOVAL WORKFLOW ========== -->
<!--
  This workflow handles major, minor, and patch version removal with parameterization.
  Variables set by route step:
    VERSION_TYPE: "major" | "minor" | "patch"
    PARENT_TYPE: "none" | "major" | "minor"
-->

<step name="version_select">

**Determine version to remove:**

**If VERSION_TYPE is "major":**

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
- options: [List of major versions with stats] + "Cancel"

**If VERSION_TYPE is "minor":**

List all minor versions:

```bash
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | while read d; do
    VERSION=$(basename "$d" | sed 's/v//')
    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    TASK_COUNT=$(find "$d" -mindepth 1 -maxdepth 1 -type d ! -name "task" ! -name "v*" 2>/dev/null | wc -l)
    echo "$MAJOR.$MINOR ($TASK_COUNT tasks)"
done | sort -V
```

Use AskUserQuestion:
- header: "Select Minor Version"
- question: "Which minor version do you want to remove?"
- options: [List of versions] + "Cancel"

**If VERSION_TYPE is "patch":**

List all patch versions:

```bash
find .claude/cat -maxdepth 3 -type d -name "v[0-9]*.[0-9]*.[0-9]*" 2>/dev/null | while read d; do
    VERSION=$(basename "$d" | sed 's/v//')
    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    PATCH=$(echo "$VERSION" | cut -d. -f3)
    TASK_COUNT=$(find "$d" -mindepth 1 -maxdepth 1 -type d ! -name "v*" 2>/dev/null | wc -l)
    STATUS=$(grep -oP '(?<=\*\*Status:\*\* )\w+' "$d/STATE.md" 2>/dev/null || echo "pending")
    echo "$MAJOR.$MINOR.$PATCH ($TASK_COUNT tasks, $STATUS)"
done | sort -V
```

Use AskUserQuestion:
- header: "Select Patch Version"
- question: "Which patch version do you want to remove?"
- options: [List of patch versions] + "Cancel"

If "Cancel" -> exit command.

</step>

<step name="version_validate">

**Validate version can be removed:**

**If VERSION_TYPE is "major":**

```bash
VERSION_PATH=".claude/cat/v$MAJOR"
[ ! -d "$VERSION_PATH" ] && echo "ERROR: Major version does not exist" && exit 1
```

Check for incomplete work:

```bash
INCOMPLETE=$(find "$VERSION_PATH" -mindepth 2 -maxdepth 2 -type d ! -name "v*" ! -name "task" | while read d; do
    [ -f "$d/STATE.md" ] && grep -q "Status: pending\|Status: in-progress" "$d/STATE.md" && echo "$d"
done)
```

**If VERSION_TYPE is "minor":**

```bash
VERSION_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR"
[ ! -d "$VERSION_PATH" ] && echo "ERROR: Minor version does not exist" && exit 1
```

Check for incomplete tasks:

```bash
INCOMPLETE=$(find "$VERSION_PATH" -mindepth 1 -maxdepth 1 -type d ! -name "task" ! -name "v*" \
    -exec test -f {}/STATE.md \; \
    -exec grep -l "Status: pending\|Status: in-progress" {}/STATE.md \; 2>/dev/null)
```

**If VERSION_TYPE is "patch":**

```bash
VERSION_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/v$MAJOR.$MINOR.$PATCH"
[ ! -d "$VERSION_PATH" ] && echo "ERROR: Patch version does not exist" && exit 1
```

Check for incomplete tasks:

```bash
INCOMPLETE=$(find "$VERSION_PATH" -mindepth 1 -maxdepth 1 -type d ! -name "v*" \
    -exec test -f {}/STATE.md \; \
    -exec grep -l "Status: pending\|Status: in-progress" {}/STATE.md \; 2>/dev/null)
```

**If incomplete work/tasks exist:**

Use AskUserQuestion:
- header: "Incomplete Work"
- question: "This {VERSION_TYPE} version has incomplete work:\n\n{list}\n\nWhat would you like to do?"
- options:
  - "Force remove everything" - Delete despite incomplete work
  - "Cancel" - Stop removal

If "Cancel" -> exit command.

</step>

<step name="version_check_dependencies">

**Check for dependencies:**

**If VERSION_TYPE is "major":**

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

**If VERSION_TYPE is "minor":**

If this is not the last minor version:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "Minor version {major}.{minor+1} and later implicitly depend on this version. Removing may affect the roadmap. Continue?"
- options:
  - "Yes, remove anyway" - Proceed
  - "No, cancel" - Abort

**If VERSION_TYPE is "patch":**

```bash
LATER_PATCHES=$(find ".claude/cat/v$MAJOR/v$MAJOR.$MINOR" -maxdepth 1 -type d -name "v$MAJOR.$MINOR.*" | \
    sed "s|.*/v$MAJOR.$MINOR.||" | while read p; do
        [ "$p" -gt "$PATCH" ] && echo "v$MAJOR.$MINOR.$p"
    done)
```

If later patches exist:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "Later patch versions exist:\n\n{list}\n\nRemoving v$MAJOR.$MINOR.$PATCH may affect version sequence. Continue?"
- options:
  - "Yes, remove anyway" - Proceed
  - "No, cancel" - Abort

If "No, cancel" -> exit command.

</step>

<step name="version_gather_stats">

**Gather removal statistics (for major version only):**

**If VERSION_TYPE is "major":**

```bash
MINOR_COUNT=$(ls -1d "$VERSION_PATH"/v$MAJOR.[0-9]* 2>/dev/null | wc -l)
TASK_COUNT=$(find "$VERSION_PATH" -mindepth 2 -maxdepth 2 -type d ! -name "v*" ! -name "task" 2>/dev/null | wc -l)
```

**If VERSION_TYPE is "minor" or "patch":**

```bash
TASK_COUNT=$(find "$VERSION_PATH" -mindepth 1 -maxdepth 1 -type d ! -name "task" ! -name "v*" 2>/dev/null | wc -l)
```

</step>

<step name="version_confirm">

**Final confirmation:**

**If VERSION_TYPE is "major":**

Use AskUserQuestion:
- header: "CONFIRM MAJOR VERSION REMOVAL"
- question: "You are about to remove Major Version {major}.\n\nThis will permanently delete:\n- {minor_count} minor versions\n- {task_count} tasks\n- All associated STATE.md, PLAN.md, CHANGELOG.md files\n\nThis action cannot be undone (except via git). Are you absolutely sure?"
- options:
  - "Yes, permanently remove Major {major}" - Proceed with removal
  - "No, cancel" - Abort removal

**If VERSION_TYPE is "minor":**

Use AskUserQuestion:
- header: "Confirm Removal"
- question: "Remove minor version {major}.{minor}?\n\nThis will delete:\n- {task_count} tasks\n- All STATE.md, PLAN.md files"
- options:
  - "Yes, remove it" - Proceed
  - "No, cancel" - Abort

**If VERSION_TYPE is "patch":**

Use AskUserQuestion:
- header: "Confirm Removal"
- question: "Remove patch version $MAJOR.$MINOR.$PATCH?\n\nThis will delete:\n- $TASK_COUNT tasks\n- All STATE.md, PLAN.md, CHANGELOG.md files"
- options:
  - "Yes, remove it" - Proceed
  - "No, cancel" - Abort

If "No, cancel" -> exit command.

</step>

<step name="version_remove">

**Remove version directory:**

```bash
rm -rf "$VERSION_PATH"
```

</step>

<step name="version_update_roadmap">

**Update ROADMAP.md:**

```bash
ROADMAP=".claude/cat/ROADMAP.md"
```

**If VERSION_TYPE is "major":**

Remove the entire section for this major version.

**If VERSION_TYPE is "minor":**

```bash
# Remove the minor version entry from ROADMAP.md
# Format being removed: - **X.Y:** Description (STATUS)
sed -i "/^- \*\*$MAJOR\.$MINOR:\*\*/d" "$ROADMAP"

# Verify removal
! grep -q "^- \*\*$MAJOR\.$MINOR:\*\*" "$ROADMAP" || echo "ERROR: Minor version entry not removed from ROADMAP.md"
```

**If VERSION_TYPE is "patch":**

```bash
# Remove the patch version entry from ROADMAP.md
# Format being removed:   - **X.Y.Z:** Description (STATUS)
sed -i "/^[[:space:]]*- \*\*$MAJOR\.$MINOR\.$PATCH:\*\*/d" "$ROADMAP"

# Verify removal
! grep -q "$MAJOR\.$MINOR\.$PATCH" "$ROADMAP" || echo "ERROR: Patch version entry not removed from ROADMAP.md"
```

</step>

<step name="version_update_parent">

**Update parent STATE.md (skip if VERSION_TYPE="major"):**

**If VERSION_TYPE is "major":**
- Skip to step: version_commit (no parent to update)

**If VERSION_TYPE is "minor":**

```bash
PARENT_STATE=".claude/cat/v$MAJOR/STATE.md"

# Remove minor version from "## Minor Versions" section
sed -i "/^- v$MAJOR.$MINOR$/d" "$PARENT_STATE"

# Recalculate progress based on remaining minor versions
TOTAL_MINORS=$(grep -c "^- v$MAJOR\." "$PARENT_STATE" 2>/dev/null || echo 0)
COMPLETED_MINORS=0
for minor_entry in $(grep "^- v$MAJOR\." "$PARENT_STATE" | sed 's/- v//'); do
  MINOR_STATE=".claude/cat/v$MAJOR/v$minor_entry/STATE.md"
  if [ -f "$MINOR_STATE" ] && grep -q "status:.*completed" "$MINOR_STATE"; then
    COMPLETED_MINORS=$((COMPLETED_MINORS + 1))
  fi
done
if [ "$TOTAL_MINORS" -gt 0 ]; then
  PROGRESS=$((COMPLETED_MINORS * 100 / TOTAL_MINORS))
else
  PROGRESS=0
fi
sed -i "s/Progress:.*$/Progress:** $PROGRESS%/" "$PARENT_STATE"

# Verify minor removed
! grep -q "^- v$MAJOR.$MINOR$" "$PARENT_STATE" || echo "ERROR: Minor version not removed from major STATE.md"
```

**If VERSION_TYPE is "patch":**

```bash
PARENT_STATE=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/STATE.md"

# Remove patch version from "## Patch Versions" section
sed -i "/^- v$MAJOR.$MINOR.$PATCH$/d" "$PARENT_STATE"

# Verify patch removed
! grep -q "^- v$MAJOR.$MINOR.$PATCH$" "$PARENT_STATE" || echo "ERROR: Patch version not removed from minor STATE.md"
```

</step>

<step name="version_commit">

**Commit removal:**

**If VERSION_TYPE is "major":**

```bash
git add -A ".claude/cat/"
git commit -m "$(cat <<'EOF'
docs: remove major version {major}

Major version removed by user request.
Removed {minor_count} minor versions and {task_count} tasks.
EOF
)"
```

**If VERSION_TYPE is "minor":**

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

**If VERSION_TYPE is "patch":**

```bash
git add -A ".claude/cat/v$MAJOR/v$MAJOR.$MINOR/"
git add ".claude/cat/ROADMAP.md"
git commit -m "$(cat <<'EOF'
docs: remove patch version {major}.{minor}.{patch}

Patch version removed by user request.
Removed {task_count} tasks.
EOF
)"
```

</step>

<step name="version_done">

**Present completion:**

**If VERSION_TYPE is "major":**

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

**If VERSION_TYPE is "minor":**

```
Minor version removed:

- Version: {major}.{minor}
- Tasks removed: {task_count}

---

Use `/cat:status` to see current state.

---
```

**If VERSION_TYPE is "patch":**

```
Patch version removed:

- Version: {major}.{minor}.{patch}
- Tasks removed: {task_count}

---

Use `/cat:status` to see current state.

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

**For Version (Major/Minor/Patch):**
- [ ] Version identified
- [ ] Incomplete work handled (blocked or force-removed)
- [ ] Dependencies/later versions checked and warned
- [ ] Statistics gathered for user awareness
- [ ] User confirmation obtained
- [ ] Version directory removed
- [ ] ROADMAP.md updated
- [ ] Parent STATE.md updated (if applicable)
- [ ] Removal committed to git
- [ ] Recovery instructions provided (for major)

</success_criteria>
