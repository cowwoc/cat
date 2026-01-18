---
name: cat:init
description: Initialize CAT planning structure (new or existing project)
allowed-tools: [Read, Write, Bash, Glob, Grep, AskUserQuestion]
---

<objective>
Initialize CAT planning structure. Creates `.claude/cat/` with PROJECT.md, ROADMAP.md, cat-config.json,
and `conventions/` directory for Claude-facing coding standards.
</objective>

<execution_context>
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/project.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/roadmap.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/cat-config.json
</execution_context>

<banner_output_instructions>

**Use centralized box rendering scripts for all banners.**

LLMs cannot reliably calculate character-level padding for Unicode text (M142).
All banners MUST be rendered using the scripts in `${CLAUDE_PLUGIN_ROOT}/scripts/`.

**Available banner scripts:**

```bash
# init-banner.sh - Renders all /cat:init banners
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" BANNER_TYPE [ARGS...]

# Banner types:
#   choose-partner          - "Choose Your Partner" welcome banner
#   gates-configured N      - Default gates configured for N versions
#   research-skipped        - Research skipped informational banner
#   initialized T C P       - CAT initialized (trust, curiosity, patience)
#   first-task-walkthrough  - First task walkthrough intro
#   first-task-created N P  - First task created (name, path)
#   all-set                 - All set, explore later
#   explore-pace            - Explore at your own pace
```

**Workflow:**
1. Run the appropriate banner script
2. Capture output to temp file: `> /tmp/banner-output.txt`
3. Use Read tool to read the file
4. Output the contents VERBATIM

</banner_output_instructions>

<process>

<step name="verify">

```bash
[ -f .claude/cat/PROJECT.md ] && echo "ERROR: CAT already initialized" && exit 1
CODE_COUNT=$(find . -name "*.ts" -o -name "*.js" -o -name "*.py" -o -name "*.go" \
  -o -name "*.rs" -o -name "*.java" -o -name "*.swift" 2>/dev/null \
  | grep -v node_modules | grep -v .git | wc -l)
echo "Found $CODE_COUNT source files"
[ -d .git ] || git init
```

</step>

<step name="project_type">

AskUserQuestion: header="Project Type", question="What type?", options=["New project", "Existing codebase"]

</step>

<!-- NEW PROJECT BRANCH -->

<step name="new_setup" condition="New project">

```bash
mkdir -p .claude/cat/conventions
```

**Deep questioning flow:**
1. Open (FREEFORM): "What do you want to build?" - wait for response
2. Follow-up (AskUserQuestion): Probe what they mentioned with 2-3 interpretations
3. Core: "If you could only nail one thing?"
4. Scope: "What's explicitly NOT in v1?"
5. Constraints: "Any hard constraints?"
6. Gate: "Ready to create PROJECT.md?" / "Ask more" / "Add context" - loop until ready

</step>

<step name="new_project" condition="New project">

Create `.claude/cat/PROJECT.md`:
```markdown
# [Project Name]

## Overview
[One paragraph]

## Goals
- [Primary/Secondary goals]

## Requirements
### Validated
(None - ship to validate)
### Active
- [ ] [Requirements from questioning]
### Out of Scope
- [Exclusions with reasons]

## Constraints
- [From questioning]

## Key Decisions
| Decision | Rationale | Outcome |
|----------|-----------|---------|
| [Choice] | [Why] | Pending |
```

Create `.claude/cat/ROADMAP.md`:
```markdown
# Roadmap
## Major 1: [Name]
- **1.0:** [Description]
```

</step>

<!-- EXISTING CODEBASE BRANCH -->

<step name="existing_detect" condition="Existing codebase">

```bash
[ -f package.json ] && echo "Node.js"
[ -f pom.xml ] && echo "Maven/Java"
[ -f Cargo.toml ] && echo "Rust"
[ -f go.mod ] && echo "Go"
[ -f README.md ] && echo "Has README"
git log --oneline -20 2>/dev/null || echo "No history"
```

</step>

<step name="existing_check_planning" condition="Existing codebase">

```bash
find . -maxdepth 3 -name "PROJECT.md" -type f 2>/dev/null | head -5
find . -maxdepth 3 -type d \( -name "releases" -o -name "roadmap" \) 2>/dev/null | head -5
```

**If structured planning exists**: Read PROJECT.md, extract description/requirements/constraints. SKIP questioning, proceed to infer_state.

</step>

<step name="existing_parse_git" condition="Existing codebase">

**Parse Task ID footers (AUTHORITATIVE source):**
```bash
git log --all --format="%H %s" --grep="Task ID:" 2>/dev/null | head -100
```

For each commit with Task ID:
- Extract: major, minor, task-name from `Task ID: v{major}.{minor}-{task-name}`
- Get files: `git diff-tree --no-commit-id --name-status -r <hash>`
- Get date: `git log -1 --format="%ci" <hash>`

Build mapping: task-name → {commits, files_created, files_modified, date}

</step>

<step name="existing_import" condition="Existing codebase">

**Import planning data (FALLBACK when no Task ID commits):**

```bash
find . -maxdepth 3 -name "changelog*.md" -type f 2>/dev/null | grep -v node_modules
grep -rl "## Objective\|## Tasks" . --include="*.md" 2>/dev/null | head -30
```

| Content Pattern | Category | Maps To |
|-----------------|----------|---------|
| `## Objective`, `## Tasks` | Task Definition | PLAN.md |
| `## Accomplishments`, `completed:` | Completion Record | STATE.md |

</step>

<step name="existing_question" condition="Existing codebase AND no structured planning">

1. FREEFORM: "What is this project, and what stage is it at?"
2. AskUserQuestion: Current state (MVP/Early/Active/Maintenance)
3. AskUserQuestion: What's next?
4. AskUserQuestion: Future out-of-scope items
5. Gate: Ready / Ask more / Add context

</step>

<step name="existing_create" condition="Existing codebase">

```bash
mkdir -p .claude/cat/conventions
```

Create PROJECT.md with inferred state (existing capabilities → Validated requirements).

Create ROADMAP.md:
```markdown
# Roadmap
## Version 1: [Name]
- **1.0:** [Description] (COMPLETED)
  - task-a, task-b
- **1.1:** [Description]
  - task-c, task-d
```

Create task directories:
```bash
mkdir -p ".claude/cat/v{major}/v{major}.{minor}/{task-name}"
```

**PLAN.md** (from task definition source):
```markdown
# Task Plan: {task-name}
## Objective
[Clear statement - import or derive from name]
## Problem Analysis
- **Error**: "{message}" | **Occurrences**: N | **Root Cause**: {explanation}
## Example Code
[Code that triggers problem]
## Tasks
- [x] {Specific action}
## Technical Approach
[HOW solution works]
## Verification
- [x] {Test case}
---
*Imported from: {source}*
```

**STATE.md** (completed tasks):
```markdown
# Task State: {task-name}
## Status
status: completed | progress: 100% | started: DATE | completed: DATE
## Commits
- `{hash}` ({date}) - {subject}
## Dependencies
- {Required capability}
## Provides
- {Delivered capability}
## Key Files
created: `{path}` | modified: `{path}`
## Key Decisions
- **{Decision}**: {Rationale}
---
*Source: git commits {hashes}*
```

**STATE.md** (pending): status: pending, progress: 0%

</step>

<step name="configure_gates" condition="Existing codebase">

**Configure entry/exit gates for imported versions:**

After importing version structure, configure gates for each version.

Use AskUserQuestion:
- header: "Version Gates"
- question: "How would you like to configure entry/exit gates for imported versions?"
- options:
  - "Use defaults (Recommended)" - sequential dependencies, all-tasks-complete exit
  - "Configure per version" - set gates for each major/minor version
  - "Skip for now" - add gates later via /cat:config

**If "Use defaults":**

For each major version PLAN.md, add:
```markdown
## Gates

### Entry
- Previous major version complete

### Exit
- All minor versions complete
```

For each minor version PLAN.md, add:
```markdown
## Gates

### Entry
- Previous minor version complete

### Exit
- All tasks complete
```

After applying defaults, render banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" gates-configured {N} > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

**If "Configure per version":**

For each major version found, use AskUserQuestion:
- header: "Major {N} Entry"
- question: "Entry gate for Major {N}?"
- options: ["Previous major complete", "No prerequisites", "Custom"]

Then:
- header: "Major {N} Exit"
- question: "Exit gate for Major {N}?"
- options: ["All minor versions complete", "Specific conditions", "No criteria"]

For each minor version, use AskUserQuestion:
- header: "v{X}.{Y} Entry"
- question: "Entry gate for v{X}.{Y}?"
- options: ["Previous minor complete", "No prerequisites", "Custom"]

Then:
- header: "v{X}.{Y} Exit"
- question: "Exit gate for v{X}.{Y}?"
- options: ["All tasks complete", "Specific conditions", "No criteria"]

**If "Skip for now":**
- Note in PROJECT.md: "Gates not configured. Use `/cat:config` to set up version gates."

</step>

<step name="existing_research" condition="Existing codebase">

**Trigger stakeholder research for pending versions:**

After importing the project structure, research is needed for pending versions.

**Find pending versions:**

```bash
# Find all pending minor versions
PENDING_VERSIONS=$(find .claude/cat -name "STATE.md" -exec grep -l "status: pending" {} \; \
  | sed 's|.claude/cat/||; s|/STATE.md||' \
  | grep -E "v[0-9]+/v[0-9]+\.[0-9]+" \
  | sed 's|v\([0-9]*\)/v\([0-9]*\.[0-9]*\)|\2|' \
  | sort -V)
```

**For each pending version, run stakeholder research:**

Use AskUserQuestion:
- header: "Research"
- question: "Run stakeholder research for pending versions?"
- options:
  - "Yes, research all pending (Recommended)" - run /cat:research for each
  - "Skip for now" - research later with /cat:research

**If "Yes, research all pending":**

For each pending version in PENDING_VERSIONS:
- Invoke `/cat:research {version}`
- This spawns 8 stakeholder agents in parallel
- Results are stored in the version's PLAN.md Research section

Display progress:
```
Running stakeholder research for pending versions...
├─ v1.2: Researching... ✓
├─ v1.3: Researching... ✓
└─ v2.0: Researching... ✓
```

**If "Skip for now":**

Note in PROJECT.md:
```markdown
## Notes
- Research not run during init. Use `/cat:research {version}` for pending versions.
```

Render banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" research-skipped > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

</step>

<!-- COMMON STEPS -->

<step name="behavior_style">

**Choose Your Partner - Capture development style preferences**

Render welcome banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" choose-partner > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

AskUserQuestion: header="Trust", question="How do you prefer to work together?", options=[
  "🛡️ Hands-On - check in often, verify each move",
  "⚔️ Balanced - trust routine calls, review key decisions (Recommended)",
  "🏹 Autonomous - let the partner lead, step in when critical"
]

AskUserQuestion: header="Curiosity", question="How should your partner handle discoveries?", options=[
  "🎯 Focused - stay on the task, ignore tangents",
  "🗺️ Observant - note interesting finds, but stay on mission (Recommended)",
  "🔮 Thorough - explore every corner, document all discoveries"
]

AskUserQuestion: header="Patience", question="When your partner spots an opportunity...", options=[
  "📜 Log it - add to backlog, maintain focus",
  "⚖️ Quick wins - take easy improvements, note the rest (Recommended)",
  "💎 Act now - if it's valuable, address it immediately"
]

Map responses to preference values:
- Trust: low | medium | high
- Curiosity: low | medium | high
- Patience: high | medium | low

</step>

<step name="config">

Get plugin version for config:
```bash
CAT_VERSION=$(jq -r '.version' "${CLAUDE_PLUGIN_ROOT}/.claude-plugin/plugin.json")
```

Create `.claude/cat/cat-config.json`:
```json
{
  "version": "[CAT_VERSION from above]",
  "contextLimit": 200000,
  "targetContextUsage": 40,
  "trust": "[low|medium|high]",
  "verify": "changed",
  "curiosity": "[low|medium|high]",
  "patience": "[high|medium|low]"
}
```

Append to PROJECT.md (after Key Decisions):
```markdown

## Conventions

Claude-facing coding standards live in `.claude/cat/conventions/`. Place files here that define:
- Code style rules (naming, formatting, patterns)
- Testing standards and requirements
- Architecture guidelines
- Language-specific conventions

**Structure:**
```
.claude/cat/conventions/
├── INDEX.md              # Summary with links to load sub-conventions on demand
├── common.md             # Cross-cutting conventions
├── {language}.md         # Language-specific (java.md, typescript.md, etc.)
├── testing.md            # Testing standards
└── {domain}/             # Optional subdirectories for complex domains
    └── {topic}.md
```

**INDEX.md purpose:** Provides a table of contents so agents can load only the conventions they need,
minimizing context usage. Each entry should have a one-line description of when to load it.

**Content guidelines:**
- Optimized for AI consumption (concise, unambiguous, examples over prose)
- Human-facing docs belong elsewhere (`docs/`, `CONTRIBUTING.md`)

## User Preferences

These preferences shape how CAT makes autonomous decisions:

- **Trust Level:** [low|medium|high] - review frequency
- **Curiosity:** [low|medium|high] - exploration beyond immediate task
- **Patience:** [high|medium|low] - tolerance for opportunistic improvements

Update anytime with: `/cat:config`
```

</step>

<step name="commit">

```bash
git add .claude/cat/
git commit -m "docs: initialize CAT planning structure"
```

</step>

<step name="done">

Render completion banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" initialized [trust] [curiosity] [patience] > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

**New projects:**
```
Initialized: PROJECT.md, ROADMAP.md, cat-config.json, conventions/
Next: /clear -> /cat:add-major-version
```

**Existing codebases:**
```
Initialized with [N] major versions, [N] minor versions, [N] tasks
Next: /clear -> /cat:work {task} OR /cat:add
```

</step>

<step name="first_task_guide">

**Offer guided first-task creation**

After initialization completes, offer to walk user through creating their first task:

AskUserQuestion: header="First Task", question="Would you like me to walk you through creating your first task?", options=[
  "Yes, guide me (Recommended)" - Interactive walkthrough of first task,
  "No, I'll explore" - Exit with pointers to /cat:help and /cat:status
]

**If "Yes, guide me":**

Render guidance banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" first-task-walkthrough > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

1. AskUserQuestion: header="First Goal", question="What's the first thing you want to accomplish?", options=[
   "[Let user describe in their own words]" - FREEFORM
]

2. Based on the response, determine if a major/minor version exists:
   - If no major version exists: Create Major 0 with Minor 0.0
   - If major exists but no minor: Create appropriate minor version

3. Create the task directory structure:
```bash
TASK_NAME="[sanitized-task-name]"
mkdir -p ".claude/cat/v0/v0.0/${TASK_NAME}"
```

4. Create initial PLAN.md for the task:
```markdown
# Task Plan: {task-name}

## Objective
[From user's description]

## Tasks
- [ ] [Broken down from objective]

## Technical Approach
[To be determined during implementation]

## Verification
- [ ] [Success criteria]
```

5. Create initial STATE.md:
```markdown
# Task State: {task-name}

## Status
status: pending
progress: 0%

## Dependencies
- None

## Provides
- [What this task delivers]
```

6. Commit the new task:
```bash
git add ".claude/cat/"
git commit -m "docs: add first task - ${TASK_NAME}"
```

7. Render completion banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" first-task-created "{task-name}" ".claude/cat/v0/v0.0/{task-name}/" > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

AskUserQuestion: header="Start Work", question="Ready to start working on this task?", options=[
  "Yes, let's go! (Recommended)" - Run /cat:work immediately,
  "No, I'll start later" - Exit with /cat:work pointer
]

**If "Yes, let's go!":**
- Invoke `/cat:work` skill to begin task execution

**If "No, I'll start later":**

Render exit banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" all-set > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

**If "No, I'll explore" (from initial question):**

Render explore banner using script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/init-banner.sh" explore-pace > /tmp/banner.txt
```

Then use Read tool on `/tmp/banner.txt` and output contents VERBATIM.

</step>

</process>

<success_criteria>

| Criterion | New | Existing |
|-----------|-----|----------|
| Deep questioning completed | ✓ | If no planning |
| PROJECT.md captures context | ✓ | ✓ (inferred) |
| ROADMAP.md created | ✓ | ✓ (with history) |
| conventions/ directory | ✓ | ✓ |
| Task dirs with PLAN/STATE | - | ✓ (full content) |
| Entry/exit gates configured | - | ✓ (or skipped) |
| cat-config.json | ✓ | ✓ |
| Git committed | ✓ | ✓ |
| First task guide offered | ✓ | ✓ |

</success_criteria>
