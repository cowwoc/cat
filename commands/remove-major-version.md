---
name: cat:remove-major-version
description: Remove a major version
argument-hint: "[major]"
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

Remove an entire major version from the project. This is a significant operation that removes all
minor versions and tasks within the major. Validates that no incomplete work exists and confirms
with the user.

</objective>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No planning structure found." && exit 1
[ -z "$(ls -d .claude/cat/v[0-9]* 2>/dev/null)" ] && echo "ERROR: No major versions exist." && exit 1
```

</step>

<step name="select_version">

**Determine major version to remove:**

**If $ARGUMENTS provided:**
- Parse as major version number
- Validate version exists

**If $ARGUMENTS empty:**

List all major versions:

```bash
# Find all major versions with statistics
for d in .claude/cat/v[0-9]*/; do
    MAJOR=$(basename "$d" | sed 's/v//')
    MINOR_COUNT=$(ls -1d "$d"v$MAJOR.[0-9]* 2>/dev/null | wc -l)
    TASK_COUNT=$(find "$d" -mindepth 3 -maxdepth 3 -name "STATE.md" 2>/dev/null | wc -l)
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

<step name="validate">

**Validate major version can be removed:**

```bash
MAJOR_PATH=".claude/cat/v$MAJOR"

# Check version exists
[ ! -d "$MAJOR_PATH" ] && echo "ERROR: Major version does not exist" && exit 1
```

**Check for incomplete work:**

```bash
# Find any tasks that are not completed
INCOMPLETE=$(find "$MAJOR_PATH" -mindepth 3 -maxdepth 3 -name "STATE.md" \
    -exec grep -l "Status: pending\|Status: in-progress" {} \; 2>/dev/null)
```

If incomplete tasks exist:

```
ERROR: Cannot remove major version with incomplete work.

Incomplete tasks found:
{list of incomplete tasks}

Options:
1. Complete all tasks first
2. Remove individual minor versions with /cat:remove-minor-version
3. Force removal (see below)
```

Use AskUserQuestion:
- header: "Incomplete Work"
- question: "Major version {major} has incomplete work. What would you like to do?"
- options:
  - "Force remove everything" - Delete despite incomplete work
  - "Cancel" - Stop removal

If "Cancel" -> exit command.

</step>

<step name="check_dependencies">

**Check if later major versions depend on this:**

```bash
# Check if any later major versions reference this one
LATER_MAJORS=$(find .claude/cat -name "STATE.md" -path ".claude/cat/v[$(($MAJOR+1))-9]*/STATE.md" \
    -exec grep -l "Dependencies:.*$MAJOR" {} \; 2>/dev/null)
```

If dependents found:

Use AskUserQuestion:
- header: "Dependency Warning"
- question: "Later major versions may depend on this one completing:\n\n[list]\n\nRemoving may affect project structure. Continue?"
- options:
  - "Yes, remove anyway" - Proceed
  - "No, cancel" - Abort

</step>

<step name="gather_stats">

**Gather removal statistics:**

```bash
MINOR_COUNT=$(ls -1d "$MAJOR_PATH"/v$MAJOR.[0-9]* 2>/dev/null | wc -l)
TASK_COUNT=$(find "$MAJOR_PATH" -mindepth 3 -maxdepth 3 -name "STATE.md" 2>/dev/null | wc -l)
```

</step>

<step name="confirm">

**Final confirmation (extra serious for major version):**

Use AskUserQuestion:
- header: "CONFIRM MAJOR VERSION REMOVAL"
- question: "You are about to remove Major Version {major}.\n\nThis will permanently delete:\n- {minor_count} minor versions\n- {task_count} tasks\n- All associated STATE.md, PLAN.md, CHANGELOG.md files\n\nThis action cannot be undone (except via git). Are you absolutely sure?"
- options:
  - "Yes, permanently remove Major {major}" - Proceed with removal
  - "No, cancel" - Abort removal

If "No, cancel" -> exit command.

</step>

<step name="remove">

**Remove major version directory:**

```bash
rm -rf "$MAJOR_PATH"
```

</step>

<step name="update_roadmap">

**Update ROADMAP.md:**

Remove the entire section for this major version.

</step>

<step name="commit">

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

<step name="done">

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
