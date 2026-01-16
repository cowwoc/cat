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

<!-- COMMON STEPS -->

<step name="mode">

AskUserQuestion: header="Mode", question="How to work?", options=["Interactive - confirm at each step", "YOLO - auto-approve"]

</step>

<step name="adventure_style">

**Choose Your Adventurer - Capture development style preferences**

Display welcome banner:
```
╔═══════════════════════════════════════════════════════════════════╗
║  🎮 WELCOME TO YOUR DEVELOPMENT ADVENTURE                          ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  A few quick questions to understand your style.                  ║
║  (These shape how CAT makes decisions throughout the project)     ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

AskUserQuestion: header="Approach", question="Development approach?", options=[
  "🛡️ Conservative - minimal changes, thorough testing, avoid risk",
  "⚖️ Balanced - pragmatic tradeoffs, reasonable coverage (Recommended)",
  "⚔️ Aggressive - comprehensive improvements, move fast, refactor freely"
]

AskUserQuestion: header="Review", question="When should CAT trigger stakeholder review?", options=[
  "Always before merging",
  "Only for high-risk or cross-module changes (Recommended)",
  "Never - I'll request when needed"
]

AskUserQuestion: header="Refactoring", question="Refactoring appetite?", options=[
  "Avoid - only fix what's broken",
  "Opportunistic - clean up adjacent code when natural (Recommended)",
  "Eager - improve code quality proactively"
]

Map responses to preference values:
- Approach: conservative | balanced | aggressive
- Stakeholder Review: always | high-risk-only | never
- Refactoring: avoid | opportunistic | eager

</step>

<step name="config">

Create `.claude/cat/cat-config.json`:
```json
{
  "mode": "[mode from step]",
  "initialized": "[date]",
  "source": "[new|existing]",
  "yoloMode": false,
  "contextLimit": 200000,
  "targetContextUsage": 40,
  "approach": "[conservative|balanced|aggressive]",
  "stakeholderReview": "[always|high-risk-only|never]",
  "refactoring": "[avoid|opportunistic|eager]"
}
```

Append to PROJECT.md (after Key Decisions):
```markdown

## User Preferences

These preferences shape how CAT makes autonomous decisions:

- **Development Approach:** [conservative|balanced|aggressive]
- **Stakeholder Review:** [always|high-risk-only|never]
- **Refactoring Appetite:** [avoid|opportunistic|eager]

Update anytime with: `/cat:update-preferences`
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
║  ✨ YOUR ADVENTURE AWAITS                                          ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Style: [approach] │ Reviews: [stakeholderReview]                 ║
║  Refactoring: [refactoring] │ Mode: [interactive|yolo]            ║
║                                                                   ║
║  These preferences will guide autonomous decisions.               ║
║  Change anytime with: /cat:update-preferences                     ║
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
| cat-config.json | ✓ | ✓ |
| Git committed | ✓ | ✓ |

</success_criteria>
