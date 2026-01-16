---
name: cat:add-major-version
description: Add new major version
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
---

<objective>

Add a new major version to the project. Major versions represent new features or significant
capabilities. Uses collaborative thinking to help the user crystallize their vision.

</objective>

<philosophy>

**You are a thinking partner, not an interviewer.**

The user often has a fuzzy idea. Your job is to help them sharpen it. Ask questions that make them
think "oh, I hadn't considered that" or "yes, that's exactly what I mean."

**Features first — everything else derives from what they want to build.**

Don't ask abstract questions about "scope" or "timeline." Ask about what they want to create.
The scope emerges from the features, not the other way around.

**User = visionary. Claude = builder.**

The user knows:
- How they imagine it working
- What it should look/feel like
- What's essential vs nice-to-have

You figure out:
- Technical approach (read the code)
- Implementation constraints
- Risk assessment

</philosophy>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/major-state.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/major-plan.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/changelog.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/questioning.md

</execution_context>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No planning structure. Run /cat:new-project first." && exit 1
[ ! -f .claude/cat/ROADMAP.md ] && echo "ERROR: No ROADMAP.md. Run /cat:new-project first." && exit 1
```

</step>

<step name="find_next_major">

**Determine next major version number:**

```bash
# Find highest existing major version
NEXT_MAJOR=$(ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -V | tail -1)
if [ -z "$NEXT_MAJOR" ]; then
    NEXT_MAJOR=1
else
    NEXT_MAJOR=$((NEXT_MAJOR + 1))
fi
echo "Next major version: $NEXT_MAJOR"
```

</step>

<step name="discuss">

**Gather major version context through collaborative thinking:**

**CRITICAL: ALL questions use AskUserQuestion. Never ask inline text questions.**

**1. Open - Features First (FREEFORM):**

Use AskUserQuestion:
- header: "Vision"
- question: "What do you want to build, add, or fix in Major Version $NEXT_MAJOR?"
- options: [Deferred issues from STATE.md if any] + ["New features", "Improvements to existing",
  "Bug fixes", "Let me describe"]

Wait for response.

**2. Explore features:**

Based on their response, use AskUserQuestion:

If they named specific features:
- header: "Feature Details"
- question: "Tell me more about [feature] - what should it do?"
- options: [Contextual options based on feature type + "Let me describe it"]

If they described a general direction:
- header: "Breaking It Down"
- question: "That could involve [A], [B], [C] - which matter most?"
- options: [Specific sub-features + "All of them" + "Something else"]

If they're not sure:
- header: "Starting Points"
- question: "What's been frustrating or missing?"
- options: [Pain point categories + "Let me think about it"]

**3. Sharpen the core:**

Use AskUserQuestion:
- header: "Essential"
- question: "What's the most important part of this major version?"
- options: Key aspects they've mentioned + "All equally important" + "Something else"

**4. Find boundaries:**

Use AskUserQuestion:
- header: "Boundaries"
- question: "What's explicitly NOT in this major version?"
- options: Things that might be tempting + "Nothing specific" + "Let me list them"

**5. Dependencies:**

Use AskUserQuestion:
- header: "Dependencies"
- question: "Does this major version depend on completing previous work?"
- options:
  - "No dependencies" - Can start immediately
  - "Requires previous major" - Must complete Major {N-1} first
  - "Partial dependencies" - Some work must complete first

**6. Synthesize and confirm:**

Present synthesis:
```
Based on what you described:

**Features:**
- [Feature 1]: [brief description]
- [Feature 2]: [brief description]

**Core focus:** [what matters most]
**Out of scope:** [boundaries mentioned]
```

Use AskUserQuestion:
- header: "Ready?"
- question: "Ready to create Major Version $NEXT_MAJOR, or explore more?"
- options:
  - "Create it" - Finalize and continue
  - "Ask more questions" - Help me think through this more
  - "Let me add context" - I have more to share

If "Ask more questions" → return to step 2 with new probes.
If "Let me add context" → receive input → return to step 2.
Loop until "Create it" selected.

</step>

<step name="configure_gates">

**Configure entry and exit gates for Major Version:**

Gates define when work on this major version can start (entry) and when it's complete (exit).
**Major gates are inherited by all minor versions within this major.**

**1. Entry Gate:**

Use AskUserQuestion:
- header: "Entry Gate"
- question: "When can work on Major $NEXT_MAJOR begin?"
- options:
  - "Previous major complete (Recommended)" - Major {NEXT_MAJOR-1} must be done
  - "No prerequisites" - work can start anytime
  - "Specific conditions" - let me define custom conditions

**If "Previous major complete":**
- Set entry gate to: `Previous major version ({NEXT_MAJOR-1}) complete`
- For first major (v0 or v1), default to "No prerequisites"

**If "Specific conditions":**

Use AskUserQuestion:
- header: "Entry Conditions"
- question: "Select entry conditions (multiple allowed):"
- multiSelect: true
- options:
  - "Previous major complete" - previous major must be done
  - "Specific version(s) complete" - named versions must be done first
  - "Specific task(s) complete" - named tasks must be done first
  - "Manual approval required" - explicit sign-off before starting
  - "Custom condition" - freeform text

If "Specific version(s) complete" selected:
- Ask: "Which version(s) must complete first? (e.g., 0.5)"

If "Specific task(s) complete" selected:
- Ask: "Which task(s) must complete first? (e.g., 0.5-design-review)"

If "Custom condition" selected:
- Ask: "Describe the custom entry condition:"

**2. Exit Gate:**

Use AskUserQuestion:
- header: "Exit Gate"
- question: "When is Major $NEXT_MAJOR complete?"
- options:
  - "All minor versions complete (Recommended)" - standard behavior
  - "Specific conditions" - let me define custom conditions
  - "No exit criteria" - manual decision only

**If "All minor versions complete":**
- Set exit gate to: `All minor versions complete`

**If "Specific conditions":**

Use AskUserQuestion:
- header: "Exit Conditions"
- question: "Select exit conditions (multiple allowed):"
- multiSelect: true
- options:
  - "All minor versions complete" - every minor version in this major done
  - "Specific version(s) complete" - only named minors required
  - "Tests passing" - full test suite must pass
  - "Code review complete" - review sign-off required
  - "Manual sign-off" - explicit approval required
  - "Custom condition" - freeform text

If "Specific version(s) complete" selected:
- Ask: "Which minor version(s) are required? (leave blank if TBD)"

If "Custom condition" selected:
- Ask: "Describe the custom exit condition:"

**Store gate configuration for use in create_major_plan step.**

</step>

<step name="create_structure">

**Create major version directory structure:**

```bash
MAJOR_PATH=".claude/cat/v$NEXT_MAJOR"
mkdir -p "$MAJOR_PATH/v$NEXT_MAJOR.0/task"
```

</step>

<step name="create_major_state">

**Create Major STATE.md:**

```markdown
# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** [{previous major if applicable}]
- **Last Updated:** {timestamp}
```

</step>

<step name="create_major_plan">

**Create Major PLAN.md (business-level):**

```markdown
# Plan: Version {major}

## Vision
{vision from discussion}

## Core Value
{core value from discussion}

## Scope
{scope assessment}

## Gates

### Entry
{entry gate conditions from configure_gates step, one per line with "- " prefix}
{If no conditions: "- No prerequisites"}

### Exit
{exit gate conditions from configure_gates step, one per line with "- " prefix}
{If no conditions: "- All minor versions complete"}

**Note:** These gates are inherited by all minor versions in this major.

## Goals
- {goal 1}
- {goal 2}
- {goal 3}

## Out of Scope
- {exclusion 1}
- {exclusion 2}

## Minor Versions
- **{major}.0:** Initial implementation
  *Use `/cat:add-minor-version {major}` to add more*

## Success Criteria
- [ ] {criterion 1}
- [ ] {criterion 2}
```

</step>

<step name="create_major_changelog">

**Create Major CHANGELOG.md:**

```markdown
# Changelog

## [Major {major}] - Pending

*Changelog will be populated as minor versions are completed.*
```

</step>

<step name="create_initial_minor">

**Create initial minor version (X.0):**

Create `.claude/cat/v{major}/v{major}.0/` with:

**STATE.md:**
```markdown
# State

- **Status:** pending
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** {timestamp}
```

**PLAN.md:**
```markdown
# Plan: Version {major}.0

## Focus
Initial implementation for Major {major}

## Gates

### Entry
- Inherits from Major {major} gates

### Exit
- All tasks complete

## Tasks
*No tasks defined yet. Use `/cat:add-task {major}.0` to add tasks.*
```

**CHANGELOG.md:**
```markdown
# Changelog

## [{major}.0] - Pending

*Changelog will be populated as tasks are completed.*
```

</step>

<step name="update_roadmap">

**Update ROADMAP.md:**

Append new major version section:

```markdown
## Major {major}: {Name from vision}
- **{major}.0:** Initial implementation

---
*Use /cat:add-minor-version to add minor versions*
*Use /cat:add-task to add tasks*
```

</step>

<step name="commit">

**Commit major version creation:**

```bash
git add ".claude/cat/v$NEXT_MAJOR/"
git add ".claude/cat/ROADMAP.md"
git commit -m "$(cat <<'EOF'
docs: add major version {major}

{One-line description of major version vision}

Creates Major {major} with initial minor version {major}.0.
EOF
)"
```

</step>

<step name="done">

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

`/cat:add-task {major}.0`

**Or add more minor versions:**

`/cat:add-minor-version {major}`

---
```

</step>

</process>

<success_criteria>

- [ ] Next major version number determined
- [ ] Deep discussion captured vision and scope
- [ ] Entry and exit gates configured
- [ ] Major directory structure created
- [ ] Major STATE.md, PLAN.md (with Gates section), CHANGELOG.md created
- [ ] Initial minor version (X.0) created with inherited gates
- [ ] ROADMAP.md updated with new major section
- [ ] All committed to git

</success_criteria>
