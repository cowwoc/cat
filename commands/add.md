---
name: cat:add
description: Add task or version
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
  - Skill
args: "[description]"
---

<objective>

Unified command for adding tasks or versions to the CAT planning structure. Routes to the appropriate
workflow based on user selection.

**Shortcut:** When invoked with a description argument (e.g., `/cat:add make installation easier`),
treats the argument as a task description and skips directly to task creation workflow.

</objective>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/task-state.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/task-plan.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/major-state.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/major-plan.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/minor-state.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/minor-plan.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/changelog.md
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

<step name="check_args">

**Check if description argument was provided:**

If the command was invoked with arguments (e.g., `/cat:add make installation easier`):
- Capture the full argument string as TASK_DESCRIPTION
- Skip directly to step: task_ask_type (bypassing select_type and the freeform description question)

If no arguments provided:
- Continue to step: select_type

</step>

<step name="select_type">

**Ask what to add:**

Use AskUserQuestion:
- header: "Add What?"
- question: "What would you like to add?"
- options:
  - "Task" - Add a task to an existing minor version
  - "Minor version" - Add a minor version to an existing major
  - "Major version" - Add a new major version

</step>

<step name="route">

**Route based on selection:**

**If "Task":**
- Continue to add_task workflow (step: task_gather_intent)

**If "Minor version":**
- Continue to add_minor workflow (step: minor_select_major)

**If "Major version":**
- Continue to add_major workflow (step: major_find_next)

</step>

<!-- ========== TASK WORKFLOW ========== -->

<step name="task_gather_intent">

**Gather task intent BEFORE selecting version:**

The goal is to understand what the user wants to accomplish first, then intelligently suggest which
version it belongs to.

**If TASK_DESCRIPTION already set (from command args):**
- Skip the freeform question
- Continue directly to step: task_ask_type

**Otherwise, ask for description (FREEFORM):**

Ask inline: "What do you want to accomplish? Describe the task you have in mind."

Capture as TASK_DESCRIPTION, then continue to step: task_ask_type.

</step>

<step name="task_ask_type">

**Ask task type:**

Use AskUserQuestion:
- header: "Task Type"
- question: "What type of work is this?"
- options:
  - "Feature" - Add new functionality
  - "Bugfix" - Fix a problem
  - "Refactor" - Improve code structure
  - "Performance" - Improve speed/efficiency

Capture as TASK_TYPE.

</step>

<step name="task_analyze_versions">

**Analyze existing versions and suggest best fit:**

**1. Read all minor version PLAN.md files:**

```bash
# Get all minor versions
VERSIONS=$(find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | sort -V)
```

For each version, read its PLAN.md to extract:
- Goals/objectives
- Requirements (REQ-XXX descriptions)
- Current focus area

**2. Build version summaries:**

Create a mental map of each version's focus:
- Extract the "## Goals" or "## Objectives" section
- Extract requirement descriptions from "## Requirements"
- Note the overall theme/domain

**3. Compare task to version focuses:**

Analyze TASK_DESCRIPTION against each version's focus:
- Keyword matching (e.g., "parser" matches parser-focused versions)
- Domain alignment (e.g., UI task matches UI-focused versions)
- Scope fit (bugfix in active development version vs new feature in upcoming version)

**4. Rank versions by fit:**

Score each version based on:
- Topic alignment (high weight)
- Version status - prefer active/pending over completed (medium weight)
- Logical grouping with existing tasks (low weight)

</step>

<step name="task_suggest_version">

**Present intelligent version recommendation:**

Based on analysis, present options with the best match first:

**If clear best match exists:**

Use AskUserQuestion:
- header: "Suggested Version"
- question: "Based on your task description, I recommend adding this to version {best_match}
  ({brief_reason}). Which version should this task be added to?"
- options:
  - "{best_match} (Recommended)" - {version_focus_summary}
  - "{second_match}" - {version_focus_summary} (if applicable)
  - "Show all versions" - See complete list
  - "Create new minor" - This doesn't fit existing versions

**If "Show all versions" selected:**

List all available minor versions with their focus summaries:

```bash
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | \
    sed 's|.*/v\([0-9]*\)/v\1\.\([0-9]*\)|\1.\2|' | sort -V
```

Use AskUserQuestion:
- header: "All Versions"
- question: "Select a version for this task:"
- options: [List of all versions with focus summaries] + "Create new minor version"

**If no versions exist or "Create new minor version" selected:**
- Go to step: minor_select_major

</step>

<step name="task_validate_version">

**Validate selected version exists:**

```bash
MAJOR="{major}"
MINOR="{minor}"
VERSION_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR"

[ ! -d "$VERSION_PATH" ] && echo "ERROR: Version $MAJOR.$MINOR does not exist" && exit 1
```

</step>

<step name="task_get_name">

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

<step name="task_discuss">

**Gather additional task context:**

Note: Task description and type were already captured in task_gather_intent step.
Use TASK_DESCRIPTION and TASK_TYPE from that step.

**1. Scope:**

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

**2. Dependencies:**

Use AskUserQuestion:
- header: "Dependencies"
- question: "Does this task depend on other tasks completing first?"
- options:
  - "No dependencies" - Can run immediately
  - "Yes, select dependencies" - Show task list

If dependencies needed, list existing tasks in same minor version for selection.

**3. Blockers:**

Use AskUserQuestion:
- header: "Blocks"
- question: "Does this task block any existing tasks?"
- options:
  - "No, doesn't block anything" - Continue
  - "Yes, select tasks to block" - Show task list

If blockers selected, add this new task to their Dependencies list in STATE.md.

**4. Acceptance criteria:**

Ask inline: "What are the acceptance criteria? How will we know this task is complete?"

</step>

<step name="task_select_requirements">

**Select requirements this task satisfies:**

**1. Read parent version requirements:**

```bash
VERSION_PLAN=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/PLAN.md"
```

**2. Present requirements for selection:**

If requirements exist in parent PLAN.md:

Use AskUserQuestion:
- header: "Satisfies"
- question: "Which requirements does this task satisfy? (Select all that apply)"
- multiSelect: true
- options: [List of REQ-XXX from parent PLAN.md] + "None - infrastructure/setup task"

If no requirements defined in parent: Satisfies = None

</step>

<step name="task_create">

**Create task structure:**

```bash
TASK_PATH=".claude/cat/v$MAJOR/v$MAJOR.$MINOR/$TASK_NAME"
mkdir -p "$TASK_PATH"
```

**Create STATE.md:**

```markdown
# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** [{dep1}, {dep2}] or []
- **Last Updated:** {timestamp}
```

**Create PLAN.md based on task type:**

Use appropriate template (Feature, Bugfix, or Refactor) from add-task.md reference.

</step>

<step name="task_update_parent">

**Update parent minor STATE.md:**

Recalculate progress and update task count.

</step>

<step name="task_commit">

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

<step name="task_done">

**Present completion:**

```
Task created:

- Path: .claude/cat/v{major}/v{major}.{minor}/{task-name}/
- Type: {task-type}
- Dependencies: {deps or "None"}

---

## Next Up

**Execute this task:**

`/cat:work {major}.{minor}/{task-name}`

**Or add another:**

`/cat:add`

---
```

</step>

<!-- ========== MINOR VERSION WORKFLOW ========== -->

<step name="minor_select_major">

**Determine target major version:**

Check if major versions exist:

```bash
[ -z "$(ls -d .claude/cat/v[0-9]* 2>/dev/null)" ] && echo "No major versions exist."
```

If no major versions exist:
- Inform user: "No major versions exist. Creating one first."
- Go to step: major_find_next

List available major versions:

```bash
ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -V
```

Use AskUserQuestion:
- header: "Target Major"
- question: "Which major version should this minor be added to?"
- options: [List of available majors] + "Create new major version"

If "Create new major version" -> go to step: major_find_next

</step>

<step name="minor_validate_major">

**Validate selected major exists:**

```bash
MAJOR="{major}"
MAJOR_PATH=".claude/cat/v$MAJOR"

[ ! -d "$MAJOR_PATH" ] && echo "ERROR: Major version $MAJOR does not exist" && exit 1
```

</step>

<step name="minor_ask_number">

**Ask for minor version number:**

**Find existing minors:**

```bash
EXISTING_MINORS=$(ls -1d "$MAJOR_PATH"/v$MAJOR.[0-9]* 2>/dev/null | \
    sed "s|$MAJOR_PATH/v$MAJOR\.||" | sort -V)
NEXT_MINOR=$(echo "$EXISTING_MINORS" | tail -1)
if [ -z "$NEXT_MINOR" ]; then
    NEXT_MINOR=0
else
    NEXT_MINOR=$((NEXT_MINOR + 1))
fi
```

Use AskUserQuestion:
- header: "Version Number"
- question: "Minor version number? (Next available: $MAJOR.$NEXT_MINOR)"
- options:
  - "Use $MAJOR.$NEXT_MINOR (Recommended)" - Auto-increment
  - "Specify different number" - Enter custom number

**If "Specify different number":**

Ask inline: "Enter the minor version number:"

Capture as REQUESTED_MINOR.

</step>

<step name="minor_check_conflict">

**Check if requested number conflicts:**

If user specified a custom number:

```bash
if [ -d "$MAJOR_PATH/v$MAJOR.$REQUESTED_MINOR" ]; then
    echo "Version $MAJOR.$REQUESTED_MINOR already exists."
fi
```

**If version already exists:**

Use AskUserQuestion:
- header: "Version Conflict"
- question: "Version $MAJOR.$REQUESTED_MINOR already exists. What would you like to do?"
- options:
  - "Insert before it" - Create $MAJOR.$REQUESTED_MINOR and renumber existing versions
  - "Use next available ($MAJOR.$NEXT_MINOR)" - Skip to next free number
  - "Cancel" - Abort

**If "Insert before it":**
- Go to step: minor_renumber

**If "Use next available":**
- Set MINOR = NEXT_MINOR
- Continue to step: minor_discuss

**If "Cancel":**
- Exit command

</step>

<step name="minor_renumber">

**Renumber existing minor versions:**

This is a significant operation. Renumber all minor versions >= REQUESTED_MINOR by +1.

```bash
# List versions to renumber (in reverse order to avoid conflicts)
for v in $(ls -1d "$MAJOR_PATH"/v$MAJOR.[0-9]* 2>/dev/null | \
    sed "s|$MAJOR_PATH/v$MAJOR\.||" | sort -rV); do
    if [ "$v" -ge "$REQUESTED_MINOR" ]; then
        NEW_V=$((v + 1))
        echo "Renumbering v$MAJOR.$v -> v$MAJOR.$NEW_V"
        mv "$MAJOR_PATH/v$MAJOR.$v" "$MAJOR_PATH/v$MAJOR.$NEW_V"
        # Update internal references in STATE.md and PLAN.md
        find "$MAJOR_PATH/v$MAJOR.$NEW_V" -name "*.md" -exec \
            sed -i "s/v$MAJOR\.$v/v$MAJOR.$NEW_V/g" {} \;
    fi
done
```

**Update ROADMAP.md with new version numbers.**

Set MINOR = REQUESTED_MINOR and continue.

</step>

<step name="minor_discuss">

**Gather minor version context through collaborative thinking:**

**1. Open - Features First:**

Use AskUserQuestion:
- header: "Focus"
- question: "What do you want to accomplish in minor version $MAJOR.$MINOR?"
- options: ["Bug fixes", "Small features", "Improvements", "Let me describe"]

**2. Explore specifics:**

Based on response, ask follow-up questions using AskUserQuestion.

**3. Boundaries:**

Use AskUserQuestion:
- header: "Scope"
- question: "Is there anything that should wait for a later minor version?"
- options: ["Nothing specific", "Yes, let me list them", "Not sure yet"]

**4. Synthesize and confirm:**

Present synthesis and confirm with user.

</step>

<step name="minor_derive_requirements">

**Derive requirements from focus using backward thinking.**

Apply backward thinking to each key item and generate REQ-001, REQ-002, etc.

Present for review with AskUserQuestion.

</step>

<step name="minor_configure_gates">

**Configure entry and exit gates.**

Use AskUserQuestion for entry gate and exit gate configuration.

</step>

<step name="minor_create">

**Create minor version structure:**

```bash
MINOR_PATH="$MAJOR_PATH/v$MAJOR.$MINOR"
mkdir -p "$MINOR_PATH/task"
```

Create STATE.md, PLAN.md, and CHANGELOG.md.

</step>

<step name="minor_update_roadmap">

**Update ROADMAP.md with new minor version entry.**

</step>

<step name="minor_update_parent">

**Update parent major STATE.md.**

</step>

<step name="minor_commit">

**Commit minor version creation:**

```bash
git add "$MINOR_PATH/"
git add ".claude/cat/ROADMAP.md"
git add "$MAJOR_PATH/STATE.md"
git commit -m "$(cat <<'EOF'
docs: add minor version {major}.{minor}

{One-line description of minor version focus}
EOF
)"
```

</step>

<step name="minor_done">

**Present completion:**

```
Minor version created:

- Version: {major}.{minor}
- Focus: {description}
- Path: .claude/cat/v{major}/v{major}.{minor}/

---

## Next Up

**Add tasks to this minor version:**

`/cat:add`

---
```

</step>

<!-- ========== MAJOR VERSION WORKFLOW ========== -->

<step name="major_find_next">

**Determine next major version number:**

```bash
NEXT_MAJOR=$(ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -V | tail -1)
if [ -z "$NEXT_MAJOR" ]; then
    NEXT_MAJOR=1
else
    NEXT_MAJOR=$((NEXT_MAJOR + 1))
fi
```

</step>

<step name="major_ask_number">

**Ask for major version number:**

Use AskUserQuestion:
- header: "Version Number"
- question: "Major version number? (Next available: $NEXT_MAJOR)"
- options:
  - "Use $NEXT_MAJOR (Recommended)" - Auto-increment
  - "Specify different number" - Enter custom number

**If "Specify different number":**

Ask inline: "Enter the major version number:"

Capture as REQUESTED_MAJOR.

</step>

<step name="major_check_conflict">

**Check if requested number conflicts:**

If user specified a custom number:

```bash
if [ -d ".claude/cat/v$REQUESTED_MAJOR" ]; then
    echo "Version $REQUESTED_MAJOR already exists."
fi
```

**If version already exists:**

Use AskUserQuestion:
- header: "Version Conflict"
- question: "Major version $REQUESTED_MAJOR already exists. What would you like to do?"
- options:
  - "Insert before it" - Create v$REQUESTED_MAJOR and renumber existing majors
  - "Use next available ($NEXT_MAJOR)" - Skip to next free number
  - "Cancel" - Abort

**If "Insert before it":**
- Go to step: major_renumber

**If "Use next available":**
- Set MAJOR = NEXT_MAJOR
- Continue to step: major_discuss

**If "Cancel":**
- Exit command

</step>

<step name="major_renumber">

**Renumber existing major versions:**

This is a significant operation. Renumber all major versions >= REQUESTED_MAJOR by +1.

```bash
# List versions to renumber (in reverse order to avoid conflicts)
for v in $(ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -rV); do
    if [ "$v" -ge "$REQUESTED_MAJOR" ]; then
        NEW_V=$((v + 1))
        echo "Renumbering v$v -> v$NEW_V"
        mv ".claude/cat/v$v" ".claude/cat/v$NEW_V"
        # Update internal references - need to update both major and minor version refs
        find ".claude/cat/v$NEW_V" -name "*.md" -exec \
            sed -i "s/v$v\./v$NEW_V./g; s/Major $v/Major $NEW_V/g" {} \;
    fi
done
```

**Update ROADMAP.md with new version numbers.**

Set MAJOR = REQUESTED_MAJOR and continue.

</step>

<step name="major_discuss">

**Gather major version context through collaborative thinking:**

Follow the discussion workflow from add-major-version.md:
1. Vision - what to build/add/fix
2. Explore features
3. Sharpen core
4. Find boundaries
5. Dependencies
6. Synthesize and confirm

</step>

<step name="major_derive_requirements">

**Derive requirements from goals using backward thinking.**

Apply backward thinking to each goal and generate REQ-001, REQ-002, etc.

Present for review with AskUserQuestion.

</step>

<step name="major_configure_gates">

**Configure entry and exit gates for major version.**

Major gates are inherited by all minor versions within.

</step>

<step name="major_create">

**Create major version structure:**

```bash
MAJOR_PATH=".claude/cat/v$MAJOR"
mkdir -p "$MAJOR_PATH/v$MAJOR.0/task"
```

Create Major STATE.md, PLAN.md, CHANGELOG.md.
Create initial minor version (X.0).

</step>

<step name="major_update_roadmap">

**Update ROADMAP.md with new major version section.**

</step>

<step name="major_commit">

**Commit major version creation:**

```bash
git add ".claude/cat/v$MAJOR/"
git add ".claude/cat/ROADMAP.md"
git commit -m "$(cat <<'EOF'
docs: add major version {major}

{One-line description of major version vision}

Creates Major {major} with initial minor version {major}.0.
EOF
)"
```

</step>

<step name="major_done">

**Present completion:**

```
Major version created:

- Version: Major {major}
- Vision: {vision summary}
- Initial minor: {major}.0
- Path: .claude/cat/v{major}/

---

## Next Up

**Add tasks to get started:**

`/cat:add`

---
```

</step>

</process>

<success_criteria>

**For Task:**
- [ ] Target version selected or created
- [ ] Task name validated (format and uniqueness)
- [ ] Discussion captured task details
- [ ] Requirements selected (or explicitly set to None)
- [ ] STATE.md and PLAN.md created
- [ ] Parent STATE.md updated
- [ ] All committed to git

**For Minor Version:**
- [ ] Target major version validated
- [ ] Version number determined (with renumbering if needed)
- [ ] Discussion captured focus and scope
- [ ] Requirements derived
- [ ] Gates configured
- [ ] Directory structure created
- [ ] Files created and ROADMAP.md updated
- [ ] All committed to git

**For Major Version:**
- [ ] Version number determined (with renumbering if needed)
- [ ] Deep discussion captured vision and scope
- [ ] Requirements derived
- [ ] Gates configured
- [ ] Major and initial minor structure created
- [ ] ROADMAP.md updated
- [ ] All committed to git

</success_criteria>
