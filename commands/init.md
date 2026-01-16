---
name: cat:init
description: Initialize CAT planning structure (new or existing project)
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - AskUserQuestion
---

<objective>

Initialize CAT planning structure through a guided wizard. Handles both greenfield (new) and
brownfield (existing) projects with appropriate flows for each.

Creates `.claude/cat/` with PROJECT.md, ROADMAP.md, and cat-config.json.

</objective>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/project.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/roadmap.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/templates/cat-config.json
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/questioning.md

</execution_context>

<process>

<step name="verify">

**MANDATORY FIRST STEP - Verify preconditions:**

1. **Abort if CAT already exists:**
   ```bash
   [ -f .claude/cat/PROJECT.md ] && echo "ERROR: CAT already initialized. Use /cat:status" && exit 1
   ```

2. **Check for existing code:**
   ```bash
   CODE_COUNT=$(find . -name "*.ts" -o -name "*.js" -o -name "*.py" -o -name "*.go" \
     -o -name "*.rs" -o -name "*.java" -o -name "*.swift" 2>/dev/null \
     | grep -v node_modules | grep -v .git | wc -l)
   echo "Found $CODE_COUNT source files"
   ```

3. **Initialize git if needed:**
   ```bash
   if [ -d .git ] || [ -f .git ]; then
       echo "Git repo exists"
   else
       git init
       echo "Initialized new git repo"
   fi
   ```

   **You MUST run all bash commands above using the Bash tool before proceeding.**

</step>

<step name="project_type">

**Determine project type:**

Use AskUserQuestion:
- header: "Project Type"
- question: "What type of initialization?"
- options:
  - "New project" - Starting fresh, no existing code to import
  - "Existing codebase" - Import and track existing work

Store the selection for branching.

</step>

<!-- ============================================== -->
<!-- BRANCH: NEW PROJECT                            -->
<!-- ============================================== -->

<step name="new_setup" condition="project_type == 'New project'">

**Create planning directories:**

```bash
mkdir -p .claude/cat
```

</step>

<step name="new_question" condition="project_type == 'New project'">

**Deep questioning for new projects:**

**1. Open (FREEFORM - do NOT use AskUserQuestion):**

Ask inline: "What do you want to build?"

Wait for their freeform response. This gives you the context needed to ask intelligent follow-up questions.

**2. Follow the thread (NOW use AskUserQuestion):**

Based on their response, use AskUserQuestion with options that probe what they mentioned:
- header: "[Topic they mentioned]"
- question: "You mentioned [X] - what would that look like?"
- options: 2-3 interpretations + "Something else"

**3. Sharpen the core:**

Use AskUserQuestion:
- header: "Core"
- question: "If you could only nail one thing, what would it be?"
- options: Key aspects they've mentioned + "All equally important" + "Something else"

**4. Find boundaries:**

Use AskUserQuestion:
- header: "Scope"
- question: "What's explicitly NOT in v1?"
- options: Things that might be tempting + "Nothing specific" + "Let me list them"

**5. Ground in reality:**

Use AskUserQuestion:
- header: "Constraints"
- question: "Any hard constraints?"
- options: Relevant constraint types + "None" + "Yes, let me explain"

**6. Decision gate:**

Use AskUserQuestion:
- header: "Ready?"
- question: "Ready to create PROJECT.md, or explore more?"
- options (ALL THREE REQUIRED):
  - "Create PROJECT.md" - Finalize and continue
  - "Ask more questions" - I'll dig deeper
  - "Let me add context" - You have more to share

If "Ask more questions" -> return to step 2.
If "Let me add context" -> receive input via their response -> return to step 2.
Loop until "Create PROJECT.md" selected.

</step>

<step name="new_project" condition="project_type == 'New project'">

Synthesize all context into `.claude/cat/PROJECT.md`.

**PROJECT.md Structure:**

```markdown
# [Project Name]

## Overview
[One paragraph describing what this project is]

## Goals
- [Primary goal]
- [Secondary goals]

## Requirements

### Validated
(None yet - ship to validate)

### Active
- [ ] [Requirement 1]
- [ ] [Requirement 2]
- [ ] [Requirement 3]

### Out of Scope
- [Exclusion 1] - [why]
- [Exclusion 2] - [why]

## Constraints
- [Constraint 1]
- [Constraint 2]

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| [Choice from questioning] | [Why] | Pending |

---
*Last updated: [date] after initialization*
```

Do not compress. Capture everything gathered.

</step>

<step name="new_roadmap" condition="project_type == 'New project'">

Create initial `.claude/cat/ROADMAP.md`:

```markdown
# Roadmap

## Major 1: [First Major Version Name]
- **1.0:** [Initial minor version description]

---
*Use /cat:add-major-version to add more major versions*
*Use /cat:add-minor-version to add minor versions*
*Use /cat:add-task to add tasks to minor versions*
```

</step>

<!-- ============================================== -->
<!-- BRANCH: EXISTING CODEBASE                      -->
<!-- ============================================== -->

<step name="existing_detect" condition="project_type == 'Existing codebase'">

**Detect project characteristics:**

1. **Identify language/stack:**
   ```bash
   [ -f package.json ] && echo "Node.js project detected"
   [ -f requirements.txt ] && echo "Python project detected"
   [ -f Cargo.toml ] && echo "Rust project detected"
   [ -f go.mod ] && echo "Go project detected"
   [ -f pom.xml ] && echo "Maven/Java project detected"
   [ -f build.gradle ] && echo "Gradle/Java project detected"
   [ -f Package.swift ] && echo "Swift project detected"
   ```

2. **Check for existing documentation:**
   ```bash
   [ -f README.md ] && echo "README.md exists"
   [ -d docs ] && echo "docs/ directory exists"
   ```

3. **Analyze git history:**
   ```bash
   git log --oneline -20 2>/dev/null || echo "No git history"
   ```

4. **Check for tests:**
   ```bash
   find . -name "*test*" -o -name "*spec*" 2>/dev/null | grep -v node_modules | head -10
   ```

</step>

<step name="existing_analyze" condition="project_type == 'Existing codebase'">

**Analyze existing code and present findings:**

Use AskUserQuestion:
- header: "Codebase Analysis"
- question: "I've analyzed your codebase. Here's what I found:\n\n[Summary of detection results]\n\nDoes this look accurate?"
- options:
  - "Yes, that's correct" - Proceed with this understanding
  - "Let me clarify" - Provide corrections

If "Let me clarify" -> receive input, update understanding.

</step>

<step name="existing_check_planning" condition="project_type == 'Existing codebase'">

**Check for existing structured planning:**

> **CRITICAL**: If structured planning already exists, INFER project state from it.
> Do NOT ask the user for information that already exists in planning documents.

```bash
# Check for existing PROJECT.md in common locations
find . -maxdepth 3 -name "PROJECT.md" -type f 2>/dev/null | head -5

# Check for release/roadmap directories
find . -maxdepth 3 -type d \( -name "releases" -o -name "roadmap" \) 2>/dev/null | head -5
```

**If PROJECT.md or release structure exists:**

1. Read the PROJECT.md file to extract:
   - Project description (from Overview/What This Is section)
   - Current stage (from Requirements/Active section)
   - Validated requirements (from Requirements/Validated section)
   - Out of scope items (from Out of Scope section)
   - Constraints (from Constraints section)

2. Scan release directories for version structure.

3. **SKIP the question step entirely** - proceed directly to infer_state using extracted information.

4. Only use AskUserQuestion for:
   - Confirmation that extracted information is correct
   - Mode preference (Interactive vs YOLO)

**If NO structured planning exists:**
- Proceed to the existing_question step.

</step>

<step name="existing_parse_git_commits" condition="project_type == 'Existing codebase'">

**PRIORITY: Parse git commit messages for Task ID footers:**

> **CRITICAL**: Task ID footers in commit messages are the AUTHORITATIVE source for mapping
> commits to tasks. Parse these FIRST before falling back to other planning files.

**1. Find all commits with Task ID footers:**

```bash
# Extract all Task IDs from commit messages
# Format: "Task ID: v{major}.{minor}-{task-name}"
git log --all --format="%H %s" --grep="Task ID:" 2>/dev/null | head -100
```

**2. Build task-to-commit mapping:**

For each commit with a Task ID footer:

```bash
# Get full commit message to extract Task ID
COMMIT_MSG=$(git log -1 --format="%B" <commit-hash>)

# Extract Task ID from footer (format: v{major}.{minor}-{task-name})
TASK_ID=$(echo "$COMMIT_MSG" | grep -oP "Task ID: v\K[0-9]+\.[0-9]+-[a-z0-9-]+" | head -1)

# Parse components
MAJOR=$(echo "$TASK_ID" | cut -d. -f1)
MINOR=$(echo "$TASK_ID" | cut -d. -f2 | cut -d- -f1)
TASK_NAME=$(echo "$TASK_ID" | sed 's/^[0-9]*\.[0-9]*-//')
```

**3. For each mapped task, extract from commit:**

```bash
# Get files changed in commit
git diff-tree --no-commit-id --name-status -r <commit-hash>

# Get commit date
git log -1 --format="%ci" <commit-hash>

# Get commit message body (for problem/solution details)
git log -1 --format="%b" <commit-hash>
```

**4. Store mapping for later use:**

Build a mapping structure:
```
task-name -> {
  commits: [hash1, hash2, ...],
  files_created: [...],
  files_modified: [...],
  completion_date: YYYY-MM-DD,
  commit_messages: [...]
}
```

> **NOTE**: This mapping takes PRIORITY over planning files. If a task has commits with
> Task ID footers, use commit data for STATE.md metadata. Only fall back to planning files
> if no commits are found for a task.

</step>

<step name="existing_import_planning_data" condition="project_type == 'Existing codebase'">

**Import planning data from existing planning systems (FALLBACK):**

> **CRITICAL**: When structured planning exists, CAT must import ALL existing data to create
> self-contained task files. Do NOT create placeholder files that reference the old planning system.
> Each CAT task file (STATE.md, PLAN.md) must be complete and standalone.

> **NOTE**: Data from git commits with Task ID footers takes PRIORITY. Only use planning files
> for tasks without commit mappings, or to supplement commit data with additional context.

**1. Detect planning files by content patterns:**

Search for markdown files that contain planning-related content:

```bash
# Find consolidated changelog files (AUTHORITATIVE SOURCE for commit identification)
# These contain task entries with completion dates and file paths
find . -maxdepth 3 -name "changelog*.md" -type f 2>/dev/null | \
  grep -v node_modules | grep -v .git

# Find files containing task definitions (plans)
grep -rl "## Objective\|## Tasks\|## Technical Approach" . --include="*.md" 2>/dev/null | \
  grep -v node_modules | grep -v .git | head -30

# Find files containing completion records (summaries/changelogs)
grep -rl "## Accomplishments\|## Files Created\|## What Was Built\|completed:" . --include="*.md" 2>/dev/null | \
  grep -v node_modules | grep -v .git | head -30
```

**If changelog*.md files exist, use them as the primary source for:**
- Task completion dates
- Files created/modified per task
- Problem/solution descriptions
- Test coverage details

**Parsing changelog*.md files:**

Changelog files contain consolidated entries per task with this structure:
```markdown
### Task Title ✅

**Task**: `task-name`
**Completion Date**: YYYY-MM-DD

**Problem Solved**:
- Description of the problem

**Solution Implemented**:
- How it was solved

**Files Created**:
- `path/to/NewFile.java` - description

**Files Modified**:
- `path/to/ExistingFile.java` - what changed

**Test Coverage**:
- Test scenario 1
- Test scenario 2

**Quality**:
- N tests passing
- Zero violations
```

For each task entry in changelog*.md:
1. Extract `**Task**:` value to match with task name
2. Extract `**Completion Date**:` or `**Completed**:` for date filtering
3. Extract file paths from `**Files Created**:` (best for commit identification)
4. Extract file paths from `**Files Modified**:` (fallback)
5. Import Problem Solved, Solution Implemented, Test Coverage, Quality sections into STATE.md

**2. Categorize discovered files by content type:**

Read each discovered file and categorize based on content:

| Content Indicators | Category | Maps To |
|--------------------|----------|---------|
| `## Objective`, `## Tasks`, `## Technical Approach`, `## Verification` | Task Definition | PLAN.md |
| `## Accomplishments`, `## What Was Built`, `completed:`, YAML frontmatter with `duration:` | Completion Record | STATE.md |
| `## Changes`, `## Files Modified` | Change History | STATE.md |

**3. Build file mappings per task:**

For each task discovered in the release structure:
- Map task-name → plan source file (task definition)
- Map task-name → state source file (completion record, if exists)

**4. Import priority and transformation rules:**

When creating CAT task files, import content with these priorities:

**For PLAN.md** (task definition):
1. Import from discovered task definition file
2. Include: Objective, Problem Analysis, Example Code, Tasks, Technical Approach, Verification
3. Fallback: Create minimal plan from task name if no source exists

**For STATE.md** (task metadata):
1. Import from completion record's YAML frontmatter or structured metadata
2. Include: status, dates, dependencies, key-files, key-decisions, patterns, tags
3. Fallback: Infer status from whether completion record exists

</step>

<step name="existing_question" condition="project_type == 'Existing codebase'">

> **NOTE**: This step is SKIPPED if existing structured planning was found.

**Gather context about project goals:**

**1. Current state (FREEFORM):**

Ask inline: "What is this project, and what stage is it at?"

Wait for response.

**2. Existing work:**

Use AskUserQuestion:
- header: "Current Progress"
- question: "Which best describes the current state?"
- options:
  - "MVP/Prototype - core functionality works"
  - "Early development - some features complete"
  - "Active development - multiple features in progress"
  - "Maintenance mode - mostly bug fixes"
  - "Something else"

**3. Next steps:**

Use AskUserQuestion:
- header: "What's Next"
- question: "What do you want to accomplish next?"
- options: Based on project type + "Something else"

**4. Boundaries (future work only):**

Use AskUserQuestion:
- header: "Scope"
- question: "What FUTURE work is explicitly out of scope? (Completed work is always tracked)"
- options: Based on context + "Nothing specific" + "Let me list them"

**5. Decision gate:**

Use AskUserQuestion:
- header: "Ready?"
- question: "Ready to create CAT structure, or explore more?"
- options:
  - "Create CAT structure" - Finalize and continue
  - "Ask more questions" - Dig deeper
  - "Let me add context" - More to share

</step>

<step name="existing_infer_state" condition="project_type == 'Existing codebase'">

**Infer current state from codebase:**

Based on analysis:
1. Identify what functionality already exists (becomes "Validated" requirements)
2. Identify in-progress work from recent commits
3. Determine appropriate Major/Minor version numbers

> **MANDATORY**: Include ALL discovered versions in ROADMAP.md - completed, in-progress, AND planned.

</step>

<step name="existing_create_structure" condition="project_type == 'Existing codebase'">

**Create CAT planning structure:**

```bash
mkdir -p .claude/cat
```

**Create PROJECT.md with inferred state:**

```markdown
# [Project Name]

## Overview
[Description from README or inferred from code]

## Goals
- [Inferred from codebase]

## Requirements

### Validated
- [x] [Existing capability 1] - existing
- [x] [Existing capability 2] - existing

### Active
- [ ] [Next requirement 1]
- [ ] [Next requirement 2]

### Out of Scope
- [Exclusion 1] - [why]

## Constraints
- [Inferred constraints]

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| [Existing architectural choice] | [Inferred] | Implemented |

---
*Last updated: [date] after CAT initialization on existing codebase*
```

**Create ROADMAP.md reflecting current state:**

> **IMPORTANT**: Minor versions group related tasks. Each minor version contains MULTIPLE tasks.

```markdown
# Roadmap

## Version 1: [Current Version Name]
- **1.0:** [Description] (COMPLETED)
  - task-a
  - task-b
- **1.1:** [Description]
  - task-c
  - task-d

---
*Initialized from existing codebase*
```

</step>

<step name="existing_create_task_directories" condition="project_type == 'Existing codebase'">

**Create task directory structure for ALL discovered tasks:**

For each major version in ROADMAP.md:

```bash
mkdir -p ".claude/cat/v{major}/v{major}.{minor}/{task-name}"
```

**Create STATE.md and PLAN.md for each task using imported content.**

> **NOTE**: Task CHANGELOG.md files are no longer created. Changelog content is embedded
> in commit messages (for future tasks) or preserved in original planning files (for imports).

---

### PLAN.md Template (Task Definition)

> **REQUIREMENT**: PLAN.md must be comprehensive enough that someone could understand
> and re-implement the task without access to the original planning files.

Import content from source task definition file. Transform to this structure:

```markdown
# Task Plan: {task-name}

## Objective
[REQUIREMENT: Clear, specific statement of what this task accomplishes]
[Import from source: ## Objective section]
[If not found: Derive from task name - but make it specific]

## Problem Analysis
[REQUIREMENT: Explain the problem being solved with specific details]
[Import from source: ## Problem Analysis or ## Background section]
[Include: Error messages, error counts, affected components, root cause]
- **Error**: "{exact error message}"
- **Occurrences**: {N} in {codebase/project}
- **Root Cause**: {technical explanation}

## Example Code
[REQUIREMENT: Include actual code examples that demonstrate the problem]
[Import from source: ## Example, ## Example Failing Code, or code blocks in problem analysis]
```{language}
// Code that triggers the problem
```

## Tasks
[REQUIREMENT: Specific, checkable tasks - not vague descriptions]
[Import from source: ## Tasks section - preserve checkbox state exactly as found]
[If not found: Create actionable tasks from objective]
- [x] {Specific action with file/method names}
- [x] {Specific action with file/method names}

## Technical Approach
[REQUIREMENT: Explain HOW the solution works, not just what it does]
[Import from source: ## Technical Approach, ## Approach, or ## Implementation section]
[Include: Algorithm, data structures, patterns used, integration points]

## Files Involved
[PRIORITY: Extract from git diff-tree for task commits]
[FALLBACK: Import from planning files]
created:
- `{path}` - {purpose}

modified:
- `{path}` - {what changed}

## Verification
[REQUIREMENT: Specific, testable criteria]
[Import from source: ## Verification, ## Acceptance Criteria, or ## Definition of Done]
- [x] {Specific test case or scenario}
- [x] {Specific test case or scenario}

---
*Imported from: {source-file-path}*
*Import date: {current-date}*
```

---

### STATE.md Template (Task Metadata)

> **REQUIREMENT**: STATE.md must contain enough metadata to understand task relationships,
> find related code, and track what capabilities were delivered.

**For COMPLETED tasks** (completion record exists):

```markdown
# Task State: {task-name}

## Status
status: completed
progress: 100%
started: {from git first commit date or planning file}
completed: {from git last commit date or planning file}
duration: {calculated or from source}

## Commits
[PRIORITY: Extract from git log with Task ID footer]
- `{commit-hash}` ({date}) - {subject}

## Dependencies
[Import from source: requires, dependencies, or ## Dependencies section]
[REQUIREMENT: Be specific about what was needed]
- {Specific capability or task that was required}

## Provides
[REQUIREMENT: List specific capabilities delivered, not vague descriptions]
[Import from source: provides, delivers, or ## Deliverables section]
- {Specific capability, e.g., "Multi-parameter lambda expression parsing"}
- {Specific capability, e.g., "Lambda vs cast disambiguation"}

## Key Files
[PRIORITY: Extract from git diff-tree for task commits]
[REQUIREMENT: List actual file paths, not placeholders]
created:
- `{actual/path/to/File.java}` - {brief purpose}

modified:
- `{actual/path/to/File.java}` - {what was changed}

## Key Decisions
[REQUIREMENT: Include rationale, not just the decision]
[Import from source: key-decisions, ## Decisions, or ## Decisions Made section]
- **{Decision}**: {Rationale for why this approach was chosen}

## Patterns Established
[Import from source: patterns-established, ## Patterns, or notable approaches]
[REQUIREMENT: Describe patterns that should be reused]
- {Pattern name}: {How it works and when to use it}

## Metadata
subsystem: {from source or inferred from file path}
tags: [{tag1}, {tag2}, {tag3}]
affects: [{affected-component-1}, {affected-component-2}]

---
*Source: git commits {commit-hash-list}*
*Supplemented from: {source-file-path if used}*
```

**For PENDING tasks** (no completion record):

```markdown
# Task State: {task-name}

## Status
status: pending
progress: 0%
started: N/A
completed: N/A

## Dependencies
[Import from plan source if mentioned, otherwise:]
None identified - review PLAN.md for implicit dependencies

## Key Files
To be determined during implementation

---
*Pending task - see PLAN.md for task definition*
```

**For IN-PROGRESS tasks:**

```markdown
# Task State: {task-name}

## Status
status: in-progress
progress: {estimate based on completed subtasks, or 50%}
started: {from git branch creation or first commit}
completed: N/A

## Dependencies
[Same as completed task format]

## Key Files
[Partial list from work done so far]

---
*In progress - see PLAN.md for remaining work*
```

---

**Task status determination:**
- Completion record exists with `completed:` date → `status: completed`, `progress: 100%`
- Git branch exists matching task name → `status: in-progress`, `progress: 50%`
- Neither exists → `status: pending`, `progress: 0%`

</step>

<!-- ============================================== -->
<!-- COMMON: MODE, CONFIG, COMMIT, DONE             -->
<!-- ============================================== -->

<step name="mode">

**Ask workflow mode preference:**

Use AskUserQuestion:
- header: "Mode"
- question: "How do you want to work?"
- options:
  - "Interactive" - Confirm at each step, approval gates enabled
  - "YOLO" - Auto-approve, just execute

</step>

<step name="config">

Create `.claude/cat/cat-config.json`:

```json
{
  "mode": "[interactive|yolo]",
  "initialized": "[date]",
  "source": "[new|existing]"
}
```

</step>

<step name="commit">

```bash
git add .claude/cat/
git commit -m "$(cat <<'EOF'
docs: initialize CAT planning structure

Creates PROJECT.md, ROADMAP.md, and cat-config.json.
EOF
)"
```

</step>

<step name="done">

**Present completion with appropriate next steps:**

**For new projects:**
```
Project initialized:

- Project: .claude/cat/PROJECT.md
- Roadmap: .claude/cat/ROADMAP.md
- Config: .claude/cat/cat-config.json (mode: [mode])

---

## Next Up

**[Project Name]** - add first major version structure

<sub>`/clear` first -> fresh context window</sub>

`/cat:add-major-version`

---
```

**For existing codebases:**
```
CAT structure added to existing codebase:

- Project: .claude/cat/PROJECT.md
- Roadmap: .claude/cat/ROADMAP.md
- Config: .claude/cat/cat-config.json (mode: [mode])

Task directories created:
- [N] major versions
- [N] minor versions
- [N] tasks ([N] completed, [N] in-progress, [N] pending)

---

## Next Up

<sub>`/clear` first -> fresh context window</sub>

[IF pending/in-progress tasks exist:]
**Execute next task** - {task-name}

`/cat:execute-task {major}.{minor}/{task-name}`

[ELSE:]
**Add tasks** - define work to be done

`/cat:add-task`

---
```

</step>

</process>

<success_criteria>

**For new projects:**
- [ ] Deep questioning completed (not rushed)
- [ ] PROJECT.md captures full context
- [ ] Requirements initialized as hypotheses
- [ ] ROADMAP.md created with placeholder
- [ ] cat-config.json has workflow mode
- [ ] All committed to git

**For existing codebases:**
- [ ] Existing code detected and analyzed
- [ ] Current capabilities inferred as Validated requirements
- [ ] PROJECT.md reflects actual project state
- [ ] ROADMAP.md shows existing work as complete
- [ ] Task directories created for all tasks
- [ ] **PLAN.md imported with full content** (objective, tasks, approach, verification)
- [ ] **STATE.md imported with rich metadata** (status, dependencies, key-files, decisions)
- [ ] No placeholder files that reference old planning system
- [ ] cat-config.json created with mode
- [ ] All committed to git
- [ ] Next action suggested based on active tasks

</success_criteria>
