---
name: cat:init
description: Initialize CAT planning structure (new or existing project)
allowed-tools: [Read, Write, Bash, Glob, Grep, AskUserQuestion]
---

<objective>
Initialize CAT planning structure. Creates `.claude/cat/` with PROJECT.md, ROADMAP.md, cat-config.json,
`.claude/rules/` for always-loaded conventions, and `.claude/cat/conventions/` for on-demand standards.
</objective>

<execution_context>
@${CLAUDE_PLUGIN_ROOT}/templates/project.md
@${CLAUDE_PLUGIN_ROOT}/templates/roadmap.md
@${CLAUDE_PLUGIN_ROOT}/templates/cat-config.json
</execution_context>


<process>

<step name="check_precomputed_boxes">

**MANDATORY:** Check context for "PRE-COMPUTED INIT BOXES".

If found: Use those box templates exactly as provided. Replace only the {variable} placeholders with actual values.
If NOT found: **FAIL immediately**.

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/check-hooks-loaded.sh" "init boxes" "/cat:init"
if [[ $? -eq 0 ]]; then
  echo "ERROR: Pre-computed boxes not found."
  echo "The init_handler.py should have provided box templates."
  echo "Please report this issue - the handler may not be registered correctly."
fi
```

Output the error and STOP.

**Box templates available:**
- `default_gates_configured` - For version gate configuration (variable: {N})
- `research_skipped` - When research is skipped (static)
- `choose_your_partner` - Partner preference intro (static)
- `cat_initialized` - Final init confirmation (variables: {trust}, {curiosity}, {patience})
- `first_task_walkthrough` - Task walkthrough intro (static)
- `first_task_created` - Task creation confirmation (variable: {task-name})
- `all_set` - Exit with work pointer (static)
- `explore_at_your_own_pace` - Exit with exploration pointer (static)

</step>

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
mkdir -p .claude/rules .claude/cat/conventions
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
mkdir -p .claude/rules .claude/cat/conventions
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
mkdir -p ".claude/cat/issues/v{major}/v{major}.{minor}/{task-name}"
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
- **Status:** completed
- **Progress:** 100%
- **Started:** DATE
- **Completed:** DATE
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

After applying defaults, use the **default_gates_configured** box from PRE-COMPUTED INIT BOXES.
Replace `{N}` with the version count.

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
PENDING_VERSIONS=$(find .claude/cat -name "STATE.md" -exec grep -l "\*\*Status:\*\*.*pending" {} \; \
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
└─ v1.0: Researching... ✓
```

**If "Skip for now":**

Note in PROJECT.md:
```markdown
## Notes
- Research not run during init. Use `/cat:research {version}` for pending versions.
```

Use the **research_skipped** box from PRE-COMPUTED INIT BOXES.

</step>

<!-- COMMON STEPS -->

<step name="behavior_style">

**Choose Your Partner - Capture development style preferences**

Use the **choose_your_partner** box from PRE-COMPUTED INIT BOXES.
```

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

<step name="git_workflow">

**Configure Git Workflow Preferences**

This step captures the user's preferred git workflow through a conversational wizard.
The answers are used to generate RFC 2119-formatted rules in PROJECT.md.

**Step 1: Ask about branching strategy**

AskUserQuestion: header="Branching Strategy", question="How do you organize your git branches?", options=[
  "Main-only (Recommended for small projects)" - All work happens on main, no feature branches,
  "Feature branches" - Short-lived branches for each task, merge to main when done,
  "Version branches" - Long-lived branches for each version (v1.0, v2.0), tasks branch from version,
  "Let me describe" - FREEFORM input for custom workflow
]

Map response to BRANCHING_STRATEGY:
- "Main-only" -> "main-only"
- "Feature branches" -> "feature"
- "Version branches" -> "version"
- "Let me describe" -> capture FREEFORM as CUSTOM_BRANCHING

**Step 2: Ask about merge style (skip if main-only)**

**If BRANCHING_STRATEGY is NOT "main-only":**

AskUserQuestion: header="Merge Style", question="How do you prefer to integrate changes?", options=[
  "Rebase + fast-forward (Recommended)" - Linear history, no merge commits,
  "Merge commits" - Non-linear history, explicit merge points,
  "Squash merge" - Each branch becomes single commit on target branch
]

Map response to MERGE_STYLE:
- "Rebase + fast-forward" -> "fast-forward"
- "Merge commits" -> "merge-commit"
- "Squash merge" -> "squash"

**If BRANCHING_STRATEGY is "main-only":**
- Set MERGE_STYLE = "direct" (commits directly to main)

**Step 3: Ask about commit squashing preference**

AskUserQuestion: header="Commit Squashing", question="Before merging a branch, how should commits be handled?", options=[
  "Squash by type (Recommended)" - Group commits by type (feature:, bugfix:, etc.),
  "Single commit" - Squash all into one commit,
  "Keep all commits" - Preserve complete commit history,
  "Let me describe" - FREEFORM for custom squash rules
]

Map response to SQUASH_POLICY:
- "Squash by type" -> "by-type"
- "Single commit" -> "single"
- "Keep all commits" -> "keep-all"
- "Let me describe" -> capture FREEFORM as CUSTOM_SQUASH

**Step 4: Iterative clarification loop**

**Synthesize understanding based on captured preferences:**

Generate a summary of the captured workflow:

```
Based on your answers, here's my understanding of your git workflow:

**Branching:** {BRANCHING_STRATEGY description}
**Merge Style:** {MERGE_STYLE description}
**Squashing:** {SQUASH_POLICY description}
```

**Confirm understanding:**

AskUserQuestion: header="Confirm Workflow", question="Did I understand your workflow correctly?", options=[
  "Yes, that's correct" - Proceed to config step,
  "No, let me clarify" - FREEFORM to provide corrections
]

**If "No, let me clarify":**
- Capture clarification
- Update understanding
- Re-present synthesis
- Loop until user confirms "Yes, that's correct"

**Store captured values for config step:**
- GIT_BRANCHING_STRATEGY
- GIT_MERGE_STYLE
- GIT_SQUASH_POLICY
- GIT_CUSTOM_NOTES (if any FREEFORM input was provided)

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
  "trust": "[low|medium|high]",
  "verify": "changed",
  "curiosity": "[low|medium|high]",
  "patience": "[high|medium|low]",
  "gitWorkflow": {
    "branchingStrategy": "[main-only|feature|version]",
    "mergeStyle": "[direct|fast-forward|merge-commit|squash]",
    "squashPolicy": "[by-type|single|keep-all]"
  }
}
```

**Generate Git Workflow section for PROJECT.md:**

Based on the values captured in the git_workflow step, generate the Git Workflow section using
RFC 2119 terminology (MUST, SHOULD, MAY).

**Branching Strategy rules by type:**

**If GIT_BRANCHING_STRATEGY is "main-only":**
```markdown
## Git Workflow

### Branching Strategy

| Branch Type | Pattern | Purpose |
|-------------|---------|---------|
| Main | `main` | All development happens here |

**Rules:**
- All commits MUST go directly to `main`
- Feature branches SHOULD NOT be created
- Long-running branches MUST NOT exist
```

**If GIT_BRANCHING_STRATEGY is "feature":**
```markdown
## Git Workflow

### Branching Strategy

| Branch Type | Pattern | Purpose |
|-------------|---------|---------|
| Main | `main` | Production-ready code |
| Task | `{major}.{minor}-{task-name}` | Individual task work |

**Rules:**
- Task branches MUST be created from `main`
- Task branches MUST be short-lived (merge within days, not weeks)
- Task branches MUST be deleted after merge
- Direct commits to `main` SHOULD be avoided
```

**If GIT_BRANCHING_STRATEGY is "version":**
```markdown
## Git Workflow

### Branching Strategy

| Branch Type | Pattern | Purpose |
|-------------|---------|---------|
| Main | `main` | Latest stable release |
| Version | `v{major}.{minor}` | Long-lived development branches |
| Task | `{major}.{minor}-{task-name}` | Individual task work |

**Rules:**
- Version branches MUST be created from `main` when starting a new version
- Task branches MUST be created from their parent version branch
- Task branches MUST merge back to their parent version branch
- Version branches SHOULD merge to `main` only when version is complete
- Direct commits to version branches SHOULD be avoided
```

**Merge Policy rules by type:**

**If GIT_MERGE_STYLE is "direct":**
```markdown
### Merge Policy

**Pre-merge requirements:**
- Code MUST be tested before committing
- Commit messages MUST follow project conventions

**Merge method:**
- Direct commits to `main` (no branches to merge)
```

**If GIT_MERGE_STYLE is "fast-forward":**
```markdown
### Merge Policy

**Pre-merge requirements:**
- Branch MUST be rebased onto target branch
- All conflicts MUST be resolved before merge
- CI checks SHOULD pass (if configured)

**Merge method:**
- MUST use fast-forward merge (`git merge --ff-only`)
- Merge commits MUST NOT be created
- Linear history MUST be maintained
```

**If GIT_MERGE_STYLE is "merge-commit":**
```markdown
### Merge Policy

**Pre-merge requirements:**
- Branch MAY have diverged from target
- All conflicts MUST be resolved during merge

**Merge method:**
- MUST use merge commits (`git merge --no-ff`)
- Merge commits SHOULD have descriptive messages
- Branch history SHOULD be preserved
```

**If GIT_MERGE_STYLE is "squash":**
```markdown
### Merge Policy

**Pre-merge requirements:**
- Branch changes MUST be ready for single-commit summary
- Commit message MUST describe all changes

**Merge method:**
- MUST use squash merge (`git merge --squash`)
- All branch commits become one commit on target
- Original commits MAY be lost from history
```

**Squash Policy rules by type:**

**If GIT_SQUASH_POLICY is "by-type":**
```markdown
### Squash Policy

**When:** Before merging branch to target
**Strategy:** Group commits by type prefix

**Rules:**
- Implementation commits (feature:, bugfix:, refactor:) MUST be squashed together
- Infrastructure commits (config:, docs:) SHOULD be squashed separately
- Planning commits (planning:) SHOULD be included with implementation

**Example:**
```
Before:
- feature: add login form
- feature: add validation
- bugfix: fix button alignment
- config: update dependencies

After:
- feature: add login form with validation and alignment fix
- config: update dependencies
```
```

**If GIT_SQUASH_POLICY is "single":**
```markdown
### Squash Policy

**When:** Before merging branch to target
**Strategy:** Squash all commits into one

**Rules:**
- All commits MUST be squashed into a single commit
- Final commit message MUST summarize all changes
- Individual commit messages MAY be preserved in body

**Example:**
```
Before:
- feature: add login form
- feature: add validation
- bugfix: fix button alignment

After:
- feature: add complete login functionality
```
```

**If GIT_SQUASH_POLICY is "keep-all":**
```markdown
### Squash Policy

**When:** N/A - commits preserved as-is
**Strategy:** Keep all commits

**Rules:**
- Commits MUST NOT be squashed
- Each commit SHOULD be atomic and meaningful
- Commit history MUST be preserved through merge

**Note:** This policy works best with merge-commit merge style.
```

**Add Commit Format section:**

```markdown
### Commit Format

**Pattern:** `{type}: {description}`

**Valid types:** feature, bugfix, test, refactor, performance, docs, style, config, planning

**Rules:**
- Commit type prefix MUST be lowercase
- Description MUST be imperative mood ("add", not "added")
- Description MUST NOT exceed 72 characters
- Body MAY provide additional context
- Task ID footer SHOULD be included for CAT tasks
```

**If GIT_CUSTOM_NOTES exists:**

Append after commit format:
```markdown
### Custom Notes

{GIT_CUSTOM_NOTES content}
```

Append to PROJECT.md (after Key Decisions):
```markdown

## Conventions

Coding standards are split between two locations based on loading behavior:

### Always-Loaded: `.claude/rules/`

Rules loaded automatically every session (main agent and subagents). Use for:
- Critical safety rules (e.g., "never delete production data")
- Cross-cutting conventions that apply to all work (naming, formatting, patterns)
- Project-wide constraints that must never be forgotten
- `conventions.md` - Index pointing to on-demand conventions (see below)

Keep minimal - everything here costs context on every session.

### On-Demand: `.claude/cat/conventions/`

Standards loaded only when needed (similar to SKILL.md files). Use for:
- Language-specific conventions (java.md, typescript.md, etc.)
- Domain-specific guidelines (api-design.md, database.md)
- Testing standards for specific frameworks
- Detailed style guides

**Structure:**
```
.claude/rules/
└── conventions.md        # Always-loaded index pointing to on-demand conventions

.claude/cat/conventions/
├── {language}.md         # Language-specific (java.md, typescript.md, etc.)
├── testing.md            # Testing standards
└── {domain}/             # Optional subdirectories for complex domains
    └── {topic}.md
```

**conventions.md purpose:** Always-loaded index that tells agents which on-demand conventions exist
and when to load them. Each entry should have a one-line description of when to load it.

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

Use the **cat_initialized** box from PRE-COMPUTED INIT BOXES.
Replace `{trust}`, `{curiosity}`, `{patience}` with actual preference values.

**New projects:**
```
Initialized: PROJECT.md, ROADMAP.md, cat-config.json, rules/, conventions/
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

Use the **first_task_walkthrough** box from PRE-COMPUTED INIT BOXES.

1. AskUserQuestion: header="First Goal", question="What's the first thing you want to accomplish?", options=[
   "[Let user describe in their own words]" - FREEFORM
]

2. Based on the response, determine if a major/minor version exists:
   - If no major version exists: Create Major 0 with Minor 0.0
   - If major exists but no minor: Create appropriate minor version

3. Create the task directory structure:
```bash
TASK_NAME="[sanitized-task-name]"
mkdir -p ".claude/cat/issues/v0/v0.0/${TASK_NAME}"
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
- **Status:** pending
- **Progress:** 0%

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

7. Use the **first_task_created** box from PRE-COMPUTED INIT BOXES.
   Replace `{task-name}` with the actual sanitized task name.

AskUserQuestion: header="Start Work", question="Ready to start working on this task?", options=[
  "Yes, let's go! (Recommended)" - Run /cat:work immediately,
  "No, I'll start later" - Exit with /cat:work pointer
]

**If "Yes, let's go!":**
- Invoke `/cat:work` skill to begin task execution

**If "No, I'll start later":**

Use the **all_set** box from PRE-COMPUTED INIT BOXES.

**If "No, I'll explore" (from initial question):**

Use the **explore_at_your_own_pace** box from PRE-COMPUTED INIT BOXES.
│  → /cat:work         Execute tasks                                 │
│  → /cat:help         Full command reference                        │
│                                                                    │
│  Tip: Run /cat:status anytime to see suggested next steps.         │
╰────────────────────────────────────────────────────────────────────╯
```

</step>

</process>

<success_criteria>

| Criterion | New | Existing |
|-----------|-----|----------|
| Deep questioning completed | ✓ | If no planning |
| PROJECT.md captures context | ✓ | ✓ (inferred) |
| ROADMAP.md created | ✓ | ✓ (with history) |
| .claude/rules/ directory | ✓ | ✓ |
| .claude/cat/conventions/ directory | ✓ | ✓ |
| Task dirs with PLAN/STATE | - | ✓ (full content) |
| Entry/exit gates configured | - | ✓ (or skipped) |
| cat-config.json | ✓ | ✓ |
| Git committed | ✓ | ✓ |
| First task guide offered | ✓ | ✓ |

</success_criteria>
