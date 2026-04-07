<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Initialize CAT

Initialize CAT planning structure for new or existing projects.

<objective>
Initialize CAT planning structure. Creates `.cat/` with project.md, roadmap.md, config.json,
`.claude/rules/` for universal rules, and `.cat/rules/` for audience-filtered standards.

**Reference files** — read on demand as needed:
- See `${CLAUDE_PLUGIN_ROOT}/templates/project.md` for the project.md template.
- See `${CLAUDE_PLUGIN_ROOT}/templates/roadmap.md` for the roadmap.md template.
- See `${CLAUDE_PLUGIN_ROOT}/templates/config.json` for the config.json template.
</objective>



<process>

<step name="verify">

```bash
[ -f .cat/project.md ] && echo "ERROR: CAT already initialized" && exit 1
CODE_COUNT=$(find . -name "*.ts" -o -name "*.js" -o -name "*.py" -o -name "*.go" \
  -o -name "*.rs" -o -name "*.java" -o -name "*.swift" 2>/dev/null \
  | grep -v node_modules | grep -v .git | wc -l)
echo "Found $CODE_COUNT source files"
[ -d .git ] || git init
```

</step>

<step name="configure_worktree_push">

**Configure git for worktree-based workflow**

CAT uses git worktrees for issue isolation. By default, git refuses to push to a checked-out branch.
This step configures git to allow it (with automatic working tree update).

```bash
CURRENT_SETTING=$(git config --get receive.denyCurrentBranch 2>/dev/null || echo "not set")
```

**If setting is already "updateInstead":**
Skip configuration, proceed to next step.

**If setting is NOT "updateInstead":**

Explain and ask permission:

```
CAT uses git worktrees for issue isolation. When merging completed issues back to the main
branch, git needs permission to update a checked-out branch.

The setting `receive.denyCurrentBranch=updateInstead` allows this safely by automatically
updating the working tree when the branch is updated.

Current setting: {CURRENT_SETTING}
```

AskUserQuestion: header="Git Config", question="Enable worktree push support?", options=[
  "Yes, enable (Recommended)" - Set receive.denyCurrentBranch=updateInstead,
  "No, I'll merge manually" - Skip; user will need to merge from main worktree
]

**If "Yes, enable":**
```bash
git config receive.denyCurrentBranch updateInstead
echo "Configured: receive.denyCurrentBranch=updateInstead"
```

**If "No, I'll merge manually":**
Note in project.md:
```markdown
## Notes
- Worktree push disabled. Merge issue branches manually from main worktree.
```

</step>

<step name="project_type">

AskUserQuestion: header="Project Type", question="What type?", options=["New project", "Existing codebase"]

</step>

<!-- NEW PROJECT BRANCH -->

<step name="new_setup" condition="New project">

```bash
mkdir -p .claude/rules .cat/rules
if [[ ! -f .cat/.gitignore ]]; then
  if [[ -f "${CLAUDE_PLUGIN_ROOT}/templates/gitignore" ]]; then
    cp "${CLAUDE_PLUGIN_ROOT}/templates/gitignore" .cat/.gitignore
  else
    echo "WARNING: .gitignore template not found at ${CLAUDE_PLUGIN_ROOT}/templates/gitignore"
  fi
fi
```

**Deep questioning flow:**
1. Open (FREEFORM): "What do you want to build?" - wait for response
2. Follow-up (AskUserQuestion): Probe what they mentioned with 2-3 interpretations
3. Core: "If you could only nail one thing?"
4. Scope: "What's explicitly NOT in v1?"
5. Constraints: "Any hard constraints?"
6. Gate: "Ready to create project.md?" / "Ask more" / "Add context" - loop until ready

</step>

<step name="new_project" condition="New project">

Create `.cat/project.md`:
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

Create `.cat/roadmap.md`:
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
find . -maxdepth 3 -name "project.md" -type f 2>/dev/null | head -5
find . -maxdepth 3 -type d \( -name "releases" -o -name "roadmap" \) 2>/dev/null | head -5
```

**If structured planning exists**: Read project.md, extract description/requirements/constraints. SKIP questioning,
proceed to infer_state.

</step>

<step name="existing_parse_git" condition="Existing codebase">

**Parse index.json file history (AUTHORITATIVE source):**
```bash
# Find all issue directories with index.json files
find .cat/issues -name "index.json" -type f 2>/dev/null | head -100
```

For each index.json found:
- Extract: major, minor, issue-name from path `.cat/issues/v{major}/v{major}.{minor}/{issue-name}/index.json`
- Get commits: `git log --oneline -- ".cat/issues/v{major}/v{major}.{minor}/{issue-name}/"`
- Get files: `git diff-tree --no-commit-id --name-status -r <hash>` for each commit
- Get date: `git log -1 --format="%ci" -- <index.json path>`

Build mapping: issue-name → {commits, files_created, files_modified, date}

</step>

<step name="existing_import" condition="Existing codebase">

**Import planning data (FALLBACK when no index.json files exist):**

```bash
find . -maxdepth 3 -name "changelog*.md" -type f 2>/dev/null | grep -v node_modules
grep -rl "## Objective\|## Issues" . --include="*.md" 2>/dev/null | head -30
```

| Content Pattern | Category | Maps To |
|-----------------|----------|---------|
| `## Objective`, `## Issues` | Issue Definition | plan.md |
| `## Accomplishments`, `completed:` | Completion Record | index.json |

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
mkdir -p .claude/rules .cat/rules
if [[ ! -f .cat/.gitignore ]]; then
  if [[ -f "${CLAUDE_PLUGIN_ROOT}/templates/gitignore" ]]; then
    cp "${CLAUDE_PLUGIN_ROOT}/templates/gitignore" .cat/.gitignore
  else
    echo "WARNING: .gitignore template not found at ${CLAUDE_PLUGIN_ROOT}/templates/gitignore"
  fi
fi
```

Create project.md with inferred state (existing capabilities → Validated requirements).

Create roadmap.md:
```markdown
# Roadmap
## Version 1: [Name]
- **1.0:** [Description] (COMPLETED)
  - issue-a, issue-b
- **1.1:** [Description]
  - issue-c, issue-d
```

Create issue directories:
```bash
mkdir -p ".cat/issues/v{major}/v{major}.{minor}/{issue-name}"
```

**plan.md** (from issue definition source):
```markdown
# Issue Plan: {issue-name}
## Objective
[Clear statement - import or derive from name]
## Problem Analysis
- **Error**: "{message}" | **Occurrences**: N | **Root Cause**: {explanation}
## Example Code
[Code that triggers problem]
## Issues
- [x] {Specific action}
## Technical Approach
[HOW solution works]
## Verification
- [x] {Test case}
---
*Imported from: {source}*
```

**index.json** (closed issues):
```markdown
# State

- **Status:** closed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** []
- **Blocks:** []
```

**index.json** (open): status: open, progress: 0%

</step>

<step name="configure_gates" condition="Existing codebase">

**Configure entry/exit gates for imported versions:**

After importing version structure, configure gates for each version.

Use AskUserQuestion:
- header: "Version Gates"
- question: "How would you like to configure entry/exit gates for imported versions?"
- options:
  - "Use defaults (Recommended)" - sequential dependencies, all-issues-complete exit
  - "Configure per version" - set gates for each major/minor version
  - "Skip for now" - add gates later via /cat:config

**If "Use defaults":**

For each major version plan.md, add:
```markdown
## Gates

### Entry
- Previous major version complete

### Exit
- All minor versions complete
```

For each minor version plan.md, add:
```markdown
## Gates

### Entry
- Previous minor version complete

### Exit
- All issues complete
```

After applying defaults:

INVOKE: Skill("cat:get-output-agent", args="init.default-gates-configured {N}")

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
- options: ["All issues complete", "Specific conditions", "No criteria"]

**If "Skip for now":**
- Note in project.md: "Gates not configured. Use `/cat:config` to set up version gates."

</step>

<step name="existing_research" condition="Existing codebase">

**Trigger stakeholder research for pending versions:**

After importing the project structure, research is needed for pending versions.

**Find pending versions:**

```bash
# Find all pending minor versions
PENDING_VERSIONS=$(find .cat -name "index.json" -exec grep -l "\*\*Status:\*\*.*pending" {} \; \
  | sed 's|.cat/||; s|/index.json||' \
  | grep -E "v[0-9]+/v[0-9]+\.[0-9]+" \
  | sed 's|v\([0-9]*\)/v\([0-9]*\.[0-9]*\)|\2|' \
  | sort -V)
```

**For each pending version, run stakeholder research:**

Use AskUserQuestion:
- header: "Research"
- question: "Run stakeholder research for pending versions?"
- options:
  - "Yes, research all pending (Recommended)" - ask Claude to research each pending version
  - "Skip for now" - research later by asking Claude (e.g., "research v1.0")

**If "Yes, research all pending":**

For each pending version in PENDING_VERSIONS:
- Invoke `/cat:research-agent {version}`
- This spawns 8 stakeholder agents in parallel
- Results are stored in the version's plan.md Research section

Display progress:
```
Running stakeholder research for pending versions...
├─ v1.2: Researching... ✓
├─ v1.3: Researching... ✓
└─ v1.0: Researching... ✓
```

**If "Skip for now":**

Note in project.md:
```markdown
## Notes
- Research not run during init. Ask Claude to research pending versions (e.g., "research v1.0").
```

INVOKE: Skill("cat:get-output-agent", args="init.research-skipped {PENDING_VERSION}")

Replace `{PENDING_VERSION}` with an example pending version for the help text.

</step>

<!-- COMMON STEPS -->

<step name="behavior_style">

**Choose Your Partner — Personality Questionnaire**

Present 5 situational questions without revealing which config option each derives. Collect all 5 answers
before displaying the results.

See `plugin/templates/questionnaire.md` for question content, answer mappings, and explanation text.

**Question 1:**

AskUserQuestion:
- header: "How do you lead? (1/5)"
- question: |
    It's Wednesday evening and you're on vacation. A junior developer messages you:
    "I'm close to finishing the new feature — what should I do when I have it?"
    You tell them:
- options:
  - "Push it when you're ready"
  - "Send me a quick summary to review before pushing anything out"
  - "Sit tight until Monday — we'll go through everything together before it ships"

Map answer to TRUST:
- "Push it when you're ready" → TRUST=high
- "Send me a quick summary..." → TRUST=medium
- "Sit tight until Monday..." → TRUST=low

**Question 2:**

AskUserQuestion:
- header: "Friday deploy (2/5)"
- question: |
    It's 4:55pm on a Friday and production is down. You've found the fix. Before you push and head out,
    you run:
- options:
  - "Nothing — you live dangerously"
  - "The tests for what you changed — close enough"
  - "The full test suite — the pub can wait"

Map answer to CAUTION:
- "Nothing — you live dangerously" → CAUTION=low
- "The tests for what you changed — close enough" → CAUTION=medium
- "The full test suite — the pub can wait" → CAUTION=high

**Question 3:**

AskUserQuestion:
- header: "The old module (3/5)"
- question: |
    You're handed a bug report in a module nobody has touched in two years. Do you:
- options:
  - "Fix the line, close the ticket, move on"
  - "Poke around enough to understand what you're changing"
  - "Read the whole thing — you don't touch code you don't understand"

Map answer to CURIOSITY:
- "Fix the line, close the ticket, move on" → CURIOSITY=low
- "Poke around enough to understand..." → CURIOSITY=medium
- "Read the whole thing..." → CURIOSITY=high

**Question 4:**

AskUserQuestion:
- header: "Someone else's mess (4/5)"
- question: |
    While fixing a bug you stumble across an obvious hack someone left in the code. Do you:
- options:
  - "Leave it — it's a problem for another day"
  - "Clean it up if it'll take less than ten minutes"
  - "Fix it — you're not leaving that in the codebase"

Map answer to PERFECTION:
- "Leave it..." → PERFECTION=low
- "Clean it up if it'll take less than ten minutes" → PERFECTION=medium
- "Fix it — you're not leaving that in the codebase" → PERFECTION=high

**Question 5:**

AskUserQuestion:
- header: "The code review (5/5)"
- question: |
    You're reviewing a PR with a tricky bug. You'd prefer CAT to:
- options:
  - "Give you the short answer"
  - "Walk you through the reasoning"
  - "Explain everything, including what it ruled out"

Map answer to VERBOSITY:
- "Give you the short answer" → VERBOSITY=low
- "Walk you through the reasoning" → VERBOSITY=medium
- "Explain everything, including what it ruled out" → VERBOSITY=high

**After collecting all 5 answers, display results as plain text (not as an AskUserQuestion):**

Select explanation text from the table in `plugin/templates/questionnaire.md` and output:

```
Your working style:

  trust: {TRUST}         {trust_explanation}
  caution: {CAUTION}     {caution_explanation}
  curiosity: {CURIOSITY} {curiosity_explanation}
  perfection: {PERFECTION} {perfection_explanation}
  verbosity: {VERBOSITY} {verbosity_explanation}

You can update any of these later with /cat:config.
```

**Manual Testing:**
To test the questionnaire behavior manually:
1. Create a new CAT project with `/cat:init`
2. At the "How do you lead?" step, verify all 5 questions appear in sequence
3. Provide one answer to each question
4. Verify the result display shows all 5 values with correct explanations (verify against template)
5. Confirm all config values (trust, caution, curiosity, perfection, verbosity) are correctly derived in `.cat/config.json`

Store derived values for use in the config step:
- TRUST (low|medium|high)
- CAUTION (low|medium|high)
- CURIOSITY (low|medium|high)
- PERFECTION (low|medium|high)
- VERBOSITY (low|medium|high)

</step>

<step name="git_workflow">

**Configure Git Workflow Preferences**

This step captures the user's preferred git workflow through a conversational wizard.
The answers are used to generate RFC 2119-formatted rules in project.md.

**Step 1: Ask about branching strategy**

AskUserQuestion: header="Branching Strategy", question="How do you organize your git branches?", options=[
  "Main-only (Recommended for small projects)" - All work happens on main, no feature branches,
  "Feature branches" - Short-lived branches for each issue, merge to main when done,
  "Version branches" - Long-lived branches for each version (v1.0, v2.0), issues branch from version,
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

AskUserQuestion: header="Commit Squashing", question="Before merging a branch, how should commits be handled?",
options=[
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
CAT_VERSION=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' "${CLAUDE_PLUGIN_ROOT}/.claude-plugin/plugin.json" | head -1 | sed 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
```

Create `.cat/config.json`:
```json
{
  "last_migrated_version": "[CAT_VERSION from above]",
  "trust": "[low|medium|high]",
  "caution": "[low|medium|high]",
  "curiosity": "[low|medium|high]",
  "perfection": "[high|medium|low]",
  "verbosity": "[low|medium|high]",
  "gitWorkflow": {
    "branchingStrategy": "[main-only|feature|version]",
    "mergeStyle": "[direct|fast-forward|merge-commit|squash]",
    "squashPolicy": "[by-type|single|keep-all]"
  }
}
```

**Generate Git Workflow section for project.md:**

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
| Issue | `{major}.{minor}-{issue-name}` | Individual issue work |

**Rules:**
- Issue branches MUST be created from `main`
- Issue branches MUST be short-lived (merge within days, not weeks)
- Issue branches MUST be deleted after merge
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
| Issue | `{major}.{minor}-{issue-name}` | Individual issue work |

**Rules:**
- Version branches MUST be created from `main` when starting a new version
- Issue branches MUST be created from their parent version branch
- Issue branches MUST merge back to their parent version branch
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
- Commits are tracked via index.json file history, not commit footers
```

**If GIT_CUSTOM_NOTES exists:**

Append after commit format:
```markdown
### Custom Notes

{GIT_CUSTOM_NOTES content}
```

Append to project.md (after Key Decisions):
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

### Audience-Filtered: `.cat/rules/`

Rules injected by CAT hooks with audience filtering. Use for:
- Language-specific conventions (java.md, typescript.md, etc.) with `paths:` frontmatter
- Orchestration rules for the main agent only (`subAgents: []`)
- Domain-specific guidelines with specific subagent targeting

**Frontmatter properties (all optional, shown with non-default values):**
```yaml
---
mainAgent: false       # default: true (omit to inject into main agent)
subAgents: []          # default: all (omit to inject into all subagents)
paths: ["*.java"]      # default: always (omit to always inject)
---
```

**Structure:**
```
.cat/rules/
├── index.md              # Summary of all rules with audience information
├── common.md             # Common conventions (main + all subagents)
├── {language}.md         # Language-specific with paths: frontmatter
└── {topic}.md            # Topic-specific rules
```

**Content guidelines:**
- Optimized for AI consumption (concise, unambiguous, examples over prose)
- Human-facing docs belong elsewhere (`docs/`, `CONTRIBUTING.md`)

## User Preferences

These preferences shape how CAT makes autonomous decisions:

- **Trust Level:** [low|medium|high] - review frequency
- **Caution:** [low|medium|high] - validation thoroughness
- **Curiosity:** [low|medium|high] - exploration beyond immediate issue
- **Perfection:** [high|medium|low] - how immediately to act on improvements

Update anytime with: `/cat:config`
```

</step>

<step name="commit">

```bash
git add .cat/
git commit -m "docs: initialize CAT planning structure"
```

</step>

<step name="done">

INVOKE: Skill("cat:get-output-agent", args="init.cat-initialized {trust} {caution} {curiosity} {perfection} {verbosity}")

Replace `{trust}`, `{caution}`, `{curiosity}`, `{perfection}`, `{verbosity}` with actual preference values.

**New projects:**
```
Initialized: project.md, roadmap.md, config.json, rules/, conventions/
Next: /clear -> ask Claude to add a major version
```

**Existing codebases:**
```
Initialized with [N] major versions, [N] minor versions, [N] issues
Next: /clear -> /cat:work {issue} OR ask Claude to add an issue
```

</step>

<step name="first_issue_guide">

**Offer guided first-issue creation**

After initialization completes, offer to walk user through creating their first issue:

AskUserQuestion: header="First Issue", question="Would you like me to walk you through creating your first issue?",
options=[
  "Yes, guide me (Recommended)" - Interactive walkthrough of first issue,
  "No, I'll explore" - Exit with pointers to /cat:help and /cat:status
]

**If "Yes, guide me":**

INVOKE: Skill("cat:get-output-agent", args="init.first-issue-walkthrough")

1. AskUserQuestion: header="First Goal", question="What's the first thing you want to accomplish?", options=[
   "[Let user describe in their own words]" - FREEFORM
]

2. Based on the response, determine if a major/minor version exists:
   - If no major version exists: Create Major 0 with Minor 0.0
   - If major exists but no minor: Create appropriate minor version

3. Create the issue directory structure:
```bash
ISSUE_NAME="[sanitized-issue-name]"
mkdir -p ".cat/issues/v0/v0.0/${ISSUE_NAME}"
```

4. Create initial plan.md for the issue:
```markdown
# Issue Plan: {issue-name}

## Objective
[From user's description]

## Issues
- [ ] [Broken down from objective]

## Technical Approach
[To be determined during implementation]

## Verification
- [ ] [Success criteria]
```

5. Create initial index.json:
```markdown
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []
```

6. Commit the new issue:
```bash
git add ".cat/"
git commit -m "docs: add first issue - ${ISSUE_NAME}"
```

7. INVOKE: Skill("cat:get-output-agent", args="init.first-issue-created {ISSUE_NAME}")

   Replace `{ISSUE_NAME}` with the actual sanitized issue name.

AskUserQuestion: header="Start Work", question="Ready to start working on this issue?", options=[
  "Yes, let's go! (Recommended)" - Run /cat:work immediately,
  "No, I'll start later" - Exit with /cat:work pointer
]

**If "Yes, let's go!":**
- Invoke `/cat:work-agent` skill to begin issue execution

**If "No, I'll start later":**

INVOKE: Skill("cat:get-output-agent", args="init.all-set")

**If "No, I'll explore" (from initial question):**

INVOKE: Skill("cat:get-output-agent", args="init.explore-at-your-own-pace")

</step>

</process>

<success_criteria>

| Criterion | New | Existing |
|-----------|-----|----------|
| Deep questioning completed | ✓ | If no planning |
| project.md captures context | ✓ | ✓ (inferred) |
| roadmap.md created | ✓ | ✓ (with history) |
| .claude/rules/ directory | ✓ | ✓ |
| .cat/rules/ directory | ✓ | ✓ |
| Issue dirs with PLAN/STATE | - | ✓ (full content) |
| Entry/exit gates configured | - | ✓ (or skipped) |
| config.json | ✓ | ✓ |
| Git committed | ✓ | ✓ |
| First issue guide offered | ✓ | ✓ |

</success_criteria>
