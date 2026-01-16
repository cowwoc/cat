---
name: cat:init
description: Initialize CAT planning structure (new or existing project)
allowed-tools: [Read, Write, Bash, Glob, Grep, AskUserQuestion]
---

<objective>
Initialize CAT planning structure. Creates `.claude/cat/` with PROJECT.md, ROADMAP.md, cat-config.json.
</objective>

<execution_context>
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/project.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/roadmap.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/cat-config.json
</execution_context>

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
mkdir -p .claude/cat
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
mkdir -p .claude/cat
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

After applying defaults, display:
```
┌──────────────────────────────────────────────────────────────┐
│  📊 Default gates configured for {N} versions:               │
│                                                              │
│  Entry gates: Work proceeds sequentially                     │
│  • Each minor waits for previous minor to complete           │
│  • Each major waits for previous major to complete           │
│                                                              │
│  Exit gates: Standard completion criteria                    │
│  • Minor versions: all tasks must complete                   │
│  • Major versions: all minor versions must complete          │
│                                                              │
│  To customize gates for any version:                         │
│  → /cat:config → 📊 Version Gates                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**If "Configure per version":**

For each major version found, use AskUserQuestion:
- header: "Major {N} Entry"
- question: "Entry gate for Major {N}?"
- options: ["Previous major complete", "No prerequisites", "Custom"]

Then:
- header: "Major {N} Exit"
- question: "Exit gate for Major {N}?"
- options: ["All minors complete", "Specific conditions", "No criteria"]

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

<!-- COMMON STEPS -->

<step name="mode">

AskUserQuestion: header="Mode", question="How to work?", options=["Interactive - confirm at each step", "YOLO - auto-approve"]

</step>

<step name="adventure_style">

**Choose Your Adventurer - Capture development style preferences**

Display welcome banner:
```
╔═══════════════════════════════════════════════════════════════════╗
║  🎮 WELCOME TO YOUR DEVELOPMENT ADVENTURE                         ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  A few quick questions to understand your style.                  ║
║  (These shape how CAT makes decisions throughout the project)     ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

AskUserQuestion: header="Trust", question="How much autonomy should CAT have?", options=[
  "🔍 Short leash - review frequently, confirm often",
  "⚖️ Medium leash - review on significant changes (Recommended)",
  "🚀 Long leash - trust CAT to make decisions, review less"
]

AskUserQuestion: header="Curiosity", question="How much should CAT explore beyond the immediate task?", options=[
  "📦 Low - stay focused, minimal exploration",
  "⚖️ Medium - explore related concerns when relevant (Recommended)",
  "🔭 High - investigate root causes and broader patterns"
]

AskUserQuestion: header="Patience", question="How tolerant of opportunistic improvements?", options=[
  "🎯 High patience - only change what's required",
  "⚖️ Medium patience - clean up related code when natural (Recommended)",
  "⚡ Low patience - actively improve code quality in touched files"
]

Map responses to preference values:
- Trust: short | medium | long
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
  "yoloMode": false,
  "contextLimit": 200000,
  "targetContextUsage": 40,
  "trust": "[short|medium|long]",
  "verify": "changed",
  "curiosity": "[low|medium|high]",
  "patience": "[high|medium|low]"
}
```

Append to PROJECT.md (after Key Decisions):
```markdown

## User Preferences

These preferences shape how CAT makes autonomous decisions:

- **Trust Level:** [short|medium|long] - review frequency
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

Display completion banner:
```
╔═══════════════════════════════════════════════════════════════════╗
║  ✨ YOUR ADVENTURE AWAITS                                         ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Trust: [trust] │ Curiosity: [curiosity] │ Patience: [patience]   ║
║  Mode: [interactive|yolo]                                         ║
║                                                                   ║
║  These preferences will guide autonomous decisions.               ║
║  Change anytime with: /cat:config                                 ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

**New projects:**
```
Initialized: PROJECT.md, ROADMAP.md, cat-config.json
Next: /clear -> /cat:add-major-version
```

**Existing codebases:**
```
Initialized with [N] majors, [N] minors, [N] tasks
Next: /clear -> /cat:execute-task {task} OR /cat:add-task
```

</step>

</process>

<success_criteria>

| Criterion | New | Existing |
|-----------|-----|----------|
| Deep questioning completed | ✓ | If no planning |
| PROJECT.md captures context | ✓ | ✓ (inferred) |
| ROADMAP.md created | ✓ | ✓ (with history) |
| Task dirs with PLAN/STATE | - | ✓ (full content) |
| Entry/exit gates configured | - | ✓ (or skipped) |
| cat-config.json | ✓ | ✓ |
| Git committed | ✓ | ✓ |

</success_criteria>
