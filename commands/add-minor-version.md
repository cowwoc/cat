---
name: cat:add-minor-version
description: Add minor version to major
argument-hint: "[major]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
---

<objective>

Add a new minor version to an existing major version. Minor versions represent bugfixes and smaller
feature additions within a major version. Uses collaborative thinking to clarify scope.

</objective>

<philosophy>

**You are a thinking partner, not an interviewer.**

Help the user articulate what this minor version should accomplish. Ask questions that sharpen
their vision rather than interrogating for technical details.

**Features first — scope derives from what they want to build.**

</philosophy>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/minor-state.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/minor-plan.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/changelog.md

</execution_context>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No planning structure. Run /cat:new-project first." && exit 1
[ -z "$(ls -d .claude/cat/v[0-9]* 2>/dev/null)" ] && echo "ERROR: No major versions exist. Run /cat:add-major-version first." && exit 1
```

</step>

<step name="select_major">

**Determine target major version:**

**If $ARGUMENTS provided:**
- Parse as major version number
- Validate major version exists

**If $ARGUMENTS empty:**

List available major versions:

```bash
# Find all major versions
ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -V
```

Use AskUserQuestion:
- header: "Target Major Version"
- question: "Which major version should this minor be added to?"
- options: [List of available majors] + "Create new major version"

If "Create new major version" -> invoke `/cat:add-major-version` and return.

</step>

<step name="validate_major">

**Validate selected major exists:**

```bash
MAJOR="{major}"
MAJOR_PATH=".claude/cat/v$MAJOR"

[ ! -d "$MAJOR_PATH" ] && echo "ERROR: Major version $MAJOR does not exist" && exit 1
```

</step>

<step name="find_next_minor">

**Determine next minor version number:**

```bash
# Find highest existing minor version
NEXT_MINOR=$(ls -1d "$MAJOR_PATH"/v$MAJOR.[0-9]* 2>/dev/null | sed "s|$MAJOR_PATH/v$MAJOR\.||" | sort -V | tail -1)
if [ -z "$NEXT_MINOR" ]; then
    NEXT_MINOR=0
else
    NEXT_MINOR=$((NEXT_MINOR + 1))
fi
echo "Next minor version: $NEXT_MINOR"
```

</step>

<step name="discuss">

**Gather minor version context through collaborative thinking:**

**CRITICAL: ALL questions use AskUserQuestion. Never ask inline text questions.**

**1. Open - Features First:**

Use AskUserQuestion:
- header: "Focus"
- question: "What do you want to accomplish in minor version $MAJOR.$NEXT_MINOR?"
- options: ["Bug fixes", "Small features", "Improvements", "Let me describe"]

Wait for response.

**2. Explore specifics:**

Based on their response, use AskUserQuestion:

If they mentioned specific items:
- header: "Details"
- question: "Tell me more about [item] - what should change?"
- options: [Contextual options + "Let me describe it"]

If general direction:
- header: "Examples"
- question: "What's an example of what you'd like to see?"
- options: [Inferred examples + "Something else"]

**3. Boundaries:**

Use AskUserQuestion:
- header: "Scope"
- question: "Is there anything that should wait for a later minor version?"
- options: ["Nothing specific", "Yes, let me list them", "Not sure yet"]

**4. Synthesize and confirm:**

Present synthesis:
```
Minor version $MAJOR.$NEXT_MINOR:

**Focus:** [summary]
**Key items:**
- [Item 1]
- [Item 2]
```

Use AskUserQuestion:
- header: "Ready?"
- question: "Ready to create this minor version?"
- options:
  - "Create it" - Finalize
  - "Let me add more" - I have more to share

Loop until "Create it" selected.

</step>

<step name="configure_gates">

**Configure entry and exit gates:**

Gates define when tasks in this version can start (entry) and when the version is complete (exit).

**1. Entry Gate:**

Use AskUserQuestion:
- header: "Entry Gate"
- question: "When can tasks in v$MAJOR.$NEXT_MINOR start?"
- options:
  - "Previous version complete (Recommended)" - v$MAJOR.{NEXT_MINOR-1} must be done
  - "No prerequisites" - tasks can start anytime
  - "Specific conditions" - let me define custom conditions

**If "Previous version complete":**
- Set entry gate to: `Previous minor version ($MAJOR.{NEXT_MINOR-1}) complete`
- For first minor (X.0), inherit from major version gate or "No prerequisites"

**If "Specific conditions":**

Use AskUserQuestion:
- header: "Entry Conditions"
- question: "Select entry conditions (multiple allowed):"
- multiSelect: true
- options:
  - "Previous version complete" - previous minor must be done
  - "Specific task(s) complete" - named tasks must be done first
  - "Manual approval required" - explicit sign-off before starting
  - "Custom condition" - freeform text

If "Specific task(s) complete" selected:
- Ask: "Which task(s) must complete first? (e.g., 0.5-design-review)"

If "Custom condition" selected:
- Ask: "Describe the custom entry condition:"

**2. Exit Gate:**

Use AskUserQuestion:
- header: "Exit Gate"
- question: "When is v$MAJOR.$NEXT_MINOR complete?"
- options:
  - "All tasks complete (Recommended)" - standard behavior
  - "Specific conditions" - let me define custom conditions
  - "No exit criteria" - manual decision only

**If "All tasks complete":**
- Set exit gate to: `All tasks complete`

**If "Specific conditions":**

Use AskUserQuestion:
- header: "Exit Conditions"
- question: "Select exit conditions (multiple allowed):"
- multiSelect: true
- options:
  - "All tasks complete" - every task in this version done
  - "Specific task(s) complete" - only named tasks required
  - "Tests passing" - test suite must pass
  - "Code review complete" - review sign-off required
  - "Manual sign-off" - explicit approval required
  - "Custom condition" - freeform text

If "Specific task(s) complete" selected:
- Ask: "Which task(s) are required? (leave blank if TBD)"

If "Custom condition" selected:
- Ask: "Describe the custom exit condition:"

**Store gate configuration for use in create_plan step.**

</step>

<step name="create_structure">

**Create minor version directory structure:**

```bash
MINOR_PATH="$MAJOR_PATH/v$MAJOR.$NEXT_MINOR"
mkdir -p "$MINOR_PATH/task"
```

</step>

<step name="create_state">

**Create STATE.md:**

```markdown
# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** {timestamp}
```

</step>

<step name="create_plan">

**Create PLAN.md:**

```markdown
# Plan: Minor Version {major}.{minor}

## Focus
{focus from discussion}

## Scope
{scope type from discussion}

## Gates

### Entry
{entry gate conditions from configure_gates step, one per line with "- " prefix}
{If no conditions: "- No prerequisites"}

### Exit
{exit gate conditions from configure_gates step, one per line with "- " prefix}
{If no conditions: "- All tasks complete"}

## Tasks
*No tasks defined yet. Use `/cat:add-task {major}.{minor}` to add tasks.*

## Goals
- {goal 1}
- {goal 2}
```

</step>

<step name="create_changelog">

**Create CHANGELOG.md:**

```markdown
# Changelog

## [{major}.{minor}] - Pending

*Changelog will be populated as tasks are completed.*
```

</step>

<step name="update_roadmap">

**Update ROADMAP.md:**

Read current ROADMAP.md and add the new minor version entry under the appropriate major section:

```markdown
## Major {major}: [Name]
- **{major}.0:** [existing description]
- **{major}.{minor}:** {new description from discussion}
```

</step>

<step name="update_parent">

**Update parent major STATE.md:**

Recalculate progress considering new empty minor version.

</step>

<step name="commit">

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

<step name="done">

**Present completion:**

```
Minor version created:

- Version: {major}.{minor}
- Focus: {description}
- Path: .claude/cat/v{major}/v{major}.{minor}/

---

## Next Up

**Add tasks to this minor version:**

`/cat:add-task {major}.{minor}`

---
```

</step>

</process>

<success_criteria>

- [ ] Target major version validated
- [ ] Next minor version number determined
- [ ] Discussion captured focus and scope
- [ ] Entry and exit gates configured
- [ ] Directory structure created
- [ ] STATE.md, PLAN.md (with Gates section), CHANGELOG.md created
- [ ] ROADMAP.md updated
- [ ] Parent STATE.md updated
- [ ] All committed to git

</success_criteria>
