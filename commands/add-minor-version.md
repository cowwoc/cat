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
[ ! -d .claude/cat ] && echo "ERROR: No planning structure. Run /cat:init first." && exit 1
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

<step name="define_requirements">

**Define requirements for this minor version:**

Requirements are the contract for what this version must deliver. Tasks will reference these
requirements via their `Satisfies` field. A minor version cannot be marked complete until all
must-have requirements are satisfied by completed tasks.

**1. Gather requirements:**

Ask inline (FREEFORM): "List the requirements for this minor version. For each, include:
- A brief description
- Priority (must-have, should-have, nice-to-have)
- How to verify it's done (acceptance criteria)"

**2. Assign requirement IDs:**

For each requirement from user input, assign sequential IDs: REQ-001, REQ-002, etc.

**3. Confirm requirements:**

Present the requirements table:
```
| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | {description} | {priority} | {criteria} |
| REQ-002 | {description} | {priority} | {criteria} |
```

Use AskUserQuestion:
- header: "Requirements"
- question: "Are these requirements correct?"
- options:
  - "Looks good" - Finalize requirements
  - "Add more" - I have additional requirements
  - "Edit" - Let me modify these
  - "Skip requirements" - Define requirements later

If "Skip requirements":
- Note: Requirements can be added later by editing PLAN.md
- Include empty Requirements section with placeholder

**Store requirements for inclusion in PLAN.md.**

</step>

<step name="research">

**Run parallel stakeholder research:**

Spawn 8 stakeholder agents in parallel using `parallel-execute` skill with `mode: research`:

```yaml
stakeholders:
  - architect: "Research stack selection and architecture patterns for {focus summary}"
  - security: "Research security risks and secure patterns for {focus summary}"
  - quality: "Research quality patterns and anti-patterns for {focus summary}"
  - tester: "Research testing strategies and edge cases for {focus summary}"
  - performance: "Research performance characteristics and pitfalls for {focus summary}"
  - ux: "Research UX patterns and usability considerations for {focus summary}"
  - sales: "Research customer value, competitive positioning, and objection handling for {focus summary}"
  - marketing: "Research positioning, messaging, and go-to-market for {focus summary}"
```

**Each agent receives:**
- Focus summary from discuss step
- Key items list from discuss step
- `mode: research` parameter
- Reference to their stakeholder definition file

**Aggregate findings:**

Collect JSON responses from all stakeholders and merge into a unified research structure:

```yaml
research:
  stack:
    # From architect
  architecture:
    # From architect
  security:
    # From security
  quality:
    # From quality
  testing:
    # From tester
  performance:
    # From performance
  ux:
    # From ux
  sources:
    # Combined from all stakeholders
  openQuestions:
    # Combined from all stakeholders
```

**Store research findings for inclusion in PLAN.md.**

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
# Plan: Version {major}.{minor}

## Overview
{focus from discussion}

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
{requirements from define_requirements step, one row per requirement}
{If skipped: | - | *No requirements defined yet* | - | - |}

## Research

*Populated by stakeholder research. If empty, run `/cat:research`.*

### Stack
{stack recommendations from research step, or placeholder}

### Architecture
{architecture recommendations from research step, or placeholder}

### Pitfalls
{pitfalls from research step, or placeholder}

## Gates

### Entry
{entry gate conditions from configure_gates step, one per line with "- " prefix}
{If no conditions: "- No prerequisites"}

### Exit
{exit gate conditions from configure_gates step, one per line with "- " prefix}
{If no conditions: "- All tasks complete"}

## Tasks Overview
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
- [ ] Requirements defined (or explicitly skipped)
- [ ] Entry and exit gates configured
- [ ] Directory structure created
- [ ] STATE.md, PLAN.md (with Requirements and Gates sections), CHANGELOG.md created
- [ ] ROADMAP.md updated
- [ ] Parent STATE.md updated
- [ ] All committed to git

</success_criteria>
