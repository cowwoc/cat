---
name: cat:add-task
description: Add task to minor version
argument-hint: "[major.minor]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
---

<objective>

Add a new task to a minor version. Uses a discussion workflow to gather context and automatically
generates a comprehensive PLAN.md for the task.

Tasks are the atomic unit of work in CAT - each task should be completable within a single context
window (target: 40% of limit).

</objective>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/task-state.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/task-plan.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/questioning.md

</execution_context>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No planning structure. Run /cat:init first." && exit 1
[ ! -f .claude/cat/ROADMAP.md ] && echo "ERROR: No ROADMAP.md. Run /cat:init first." && exit 1
```

</step>

<step name="select_version">

**Determine target minor version:**

**If $ARGUMENTS provided:**
- Parse as `major.minor` format (e.g., "1.0", "2.1")
- Validate version exists

**If $ARGUMENTS empty:**

List available minor versions:

```bash
# Find all minor versions
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | \
    sed 's|.claude/cat/v\([0-9]*\)/v\1\.\([0-9]*\)|\1.\2|' | sort -V
```

Use AskUserQuestion:
- header: "Target Version"
- question: "Which minor version should this task be added to?"
- options: [List of available versions] + "Create new minor version"

If "Create new minor version" -> invoke `/cat:add-minor-version` and return.

</step>

<step name="validate_version">

**Validate selected version exists:**

```bash
MAJOR="{major}"
MINOR="{minor}"
VERSION_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR"

[ ! -d "$VERSION_PATH" ] && echo "ERROR: Version $MAJOR.$MINOR does not exist" && exit 1
```

</step>

<step name="get_task_name">

**Get task name from user:**

Ask inline (FREEFORM): "What should this task be called? (lowercase, hyphens only, max 50 chars)"

**Validate task name:**

```bash
TASK_NAME="{user input}"

# Validate format
if ! echo "$TASK_NAME" | grep -qE '^[a-z][a-z0-9-]{0,48}[a-z0-9]$'; then
    echo "ERROR: Invalid task name. Use lowercase letters, numbers, and hyphens only."
    echo "Example: parse-tokens, fix-memory-leak, add-user-auth"
    exit 1
fi

# Check uniqueness within minor version
if [ -d ".claude/cat/v$MAJOR/v$MAJOR.$MINOR/$TASK_NAME" ]; then
    echo "ERROR: Task '$TASK_NAME' already exists in version $MAJOR.$MINOR"
    exit 1
fi
```

If validation fails, prompt user for different name.

</step>

<step name="discuss">

**Gather task context through discussion:**

**1. What is this task? (FREEFORM):**

Ask inline: "Describe what this task should accomplish."

**2. Task type:**

Use AskUserQuestion:
- header: "Task Type"
- question: "What type of work is this?"
- options:
  - "Feature" - Add new functionality
  - "Bugfix" - Fix a problem
  - "Refactor" - Improve code structure
  - "Performance" - Improve speed/efficiency
  - "Documentation" - Add/update docs
  - "Test" - Add/improve tests

**3. Scope:**

Use AskUserQuestion:
- header: "Scope"
- question: "How many files will this likely touch?"
- options:
  - "1-2 files" - Very focused change
  - "3-5 files" - Moderate scope
  - "6+ files" - Broad change (consider splitting)

If "6+ files":
Use AskUserQuestion:
- header: "Task Size"
- question: "This seems like a large task. Should we split it into multiple smaller tasks?"
- options:
  - "Split into multiple tasks" - Create several focused tasks
  - "Keep as single task" - I understand the token risk

If "Split into multiple tasks" -> guide user to define multiple tasks, loop this command.

**4. Dependencies:**

Use AskUserQuestion:
- header: "Dependencies"
- question: "Does this task depend on other tasks completing first?"
- options:
  - "No dependencies" - Can run immediately
  - "Yes, select dependencies" - Show task list

If dependencies needed, list existing tasks in same minor version for selection.

**5. Blockers:**

Use AskUserQuestion:
- header: "Blocks"
- question: "Does this task block any existing tasks? (Will add this task as a dependency to selected tasks)"
- options:
  - "No, doesn't block anything" - Continue
  - "Yes, select tasks to block" - Show task list

If blockers selected:
- For each selected task, add this new task to their Dependencies list in STATE.md
- This ensures those tasks won't execute until this task completes

**6. Acceptance criteria:**

Ask inline: "What are the acceptance criteria? How will we know this task is complete?"

</step>

<step name="create_structure">

**Create task directory structure:**

```bash
TASK_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/$TASK_NAME"
mkdir -p "$TASK_PATH"
```

</step>

<step name="create_state">

**Create STATE.md:**

```markdown
# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** [{dep1}, {dep2}] or []
- **Last Updated:** {timestamp}
```

</step>

<step name="create_plan">

**Create PLAN.md based on task type:**

**For Feature tasks:**

```markdown
# Plan: {task-name}

## Goal
{goal from discussion}

## Approach
{approach - synthesized from discussion}

## Risk Assessment
- **Risk Level:** LOW | MEDIUM | HIGH
- **Concerns:** {potential issues}
- **Mitigation:** {how to address}

## Dependencies
- {task-name} - {why needed}

## Execution Steps

### Step 1: {action name}
**Files:** path/to/file1.ext, path/to/file2.ext
**Action:** {Specific implementation - what to do, how to do it, what to avoid and WHY}
**Verify:** {Command or check to prove it worked}
**Done:** {Measurable acceptance criterion}

### Step 2: {action name}
**Files:** path/to/file3.ext
**Action:** {Specific implementation}
**Verify:** {Command or check}
**Done:** {Acceptance criterion}

## Acceptance Criteria
- [ ] {criterion 1}
- [ ] {criterion 2}
- [ ] {criterion 3}
```

**For Bugfix tasks:**

```markdown
# Plan: {task-name}

## Problem
{problem description}

## Root Cause
{analysis of cause - may be "To be determined during execution"}

## Fix Approach
{how the fix will work}

## Risk Assessment
- **Risk Level:** LOW | MEDIUM | HIGH
- **Regression Risk:** {what could break}
- **Mitigation:** {how to verify}

## Execution Steps

### Step 1: {action name}
**Files:** path/to/file.ext
**Action:** {Specific fix - what to change, why this fixes it, what to avoid}
**Verify:** {Test or check to prove bug is fixed}
**Done:** {Bug no longer reproducible}

## Test Cases
- [ ] Original bug scenario - now passes
- [ ] Related edge cases - still work
- [ ] Regression tests - still pass
```

**For Refactor tasks:**

```markdown
# Plan: {task-name}

## Current State
{what exists now}

## Target State
{what it should become}

## Rationale
{why this refactor is needed}

## Risk Assessment
- **Risk Level:** LOW | MEDIUM | HIGH
- **Breaking Changes:** {API changes, behavior changes}
- **Mitigation:** {incremental approach, tests}

## Execution Steps

### Step 1: {action name}
**Files:** path/to/file.ext
**Action:** {Specific refactoring - what to change, pattern to follow, what to preserve}
**Verify:** {Tests still pass, behavior unchanged}
**Done:** {Code matches target pattern}

### Step 2: {action name}
**Files:** path/to/related.ext
**Action:** {Update dependents}
**Verify:** {Build succeeds, tests pass}
**Done:** {All references updated}

## Verification
- [ ] All tests pass
- [ ] No behavior changes (unless intended)
- [ ] Code follows target pattern
```

</step>

<step name="update_parent">

**Update parent minor STATE.md:**

Recalculate progress and update task count.

</step>

<step name="commit">

**Commit task creation:**

```bash
git add ".claude/cat/v$MAJOR/v$MAJOR.$MINOR/$TASK_NAME/"
git add ".claude/cat/v$MAJOR/v$MAJOR.$MINOR/STATE.md"
git commit -m "$(cat <<'EOF'
docs: add task {task-name} to {major}.{minor}

{One-line description of task goal}
EOF
)"
```

</step>

<step name="done">

**Present completion:**

```
Task created:

- Path: .claude/cat/v{major}/v{major}.{minor}/{task-name}/
- Type: {task-type}
- Dependencies: {deps or "None"}

---

## Next Up

**Execute this task:**

`/cat:execute-task {major}.{minor}/{task-name}`

**Or add another task:**

`/cat:add-task {major}.{minor}`

---
```

</step>

</process>

<validation_rules>

**Task name rules:**
- Lowercase letters and hyphens only
- Maximum 50 characters
- Must be unique within minor version
- Examples: `parse-tokens`, `fix-memory-leak`, `add-user-auth`

**Invalid examples:**
- `Parse_Tokens` (uppercase, underscores)
- `fix memory leak` (spaces)
- `add-user-authentication-system-with-oauth-integration` (too long)

</validation_rules>

<success_criteria>

- [ ] Target version selected or created
- [ ] Task name validated (format and uniqueness)
- [ ] Discussion captured task details
- [ ] STATE.md created with correct dependencies
- [ ] PLAN.md created with appropriate template
- [ ] Parent STATE.md updated
- [ ] All committed to git

</success_criteria>
