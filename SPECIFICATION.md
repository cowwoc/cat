# Claude Code CAT Plugin Specification

> **Version:** 1.1 | **Last Updated:** 2026-01-11

CAT is a Claude Code plugin for orchestrated multi-agent task execution with comprehensive planning, quality gates, and mistake prevention.

---

## Table of Contents

1. [Overview](#overview)
2. [Source Materials](#source-materials)
3. [Hierarchy Structure](#hierarchy-structure)
4. [Document Formats](#document-formats)
5. [Task Naming & Dependencies](#task-naming--dependencies)
6. [Agent Architecture](#agent-architecture)
7. [Worktree & Branch Management](#worktree--branch-management)
8. [Configuration](#configuration)
9. [Operating Modes](#operating-modes)
10. [Error Handling](#error-handling)
11. [Persistence Model](#persistence-model)
12. [Commands](#commands)
13. [CAT-Specific Skills](#cat-specific-skills)
14. [Adapted Skills](#adapted-skills)
15. [Quality Controls](#quality-controls)
16. [Core Principles](#core-principles)

---

## Overview

CAT is a Claude Code plugin that provides:

- **Comprehensive planning** before each task
- **Multi-agent orchestration** with parallel subagent execution
- **Extensive quality controls** and approval gates after task completion
- **TDD integration** to prevent regressions
- **Preventative measures** to avoid repeating mistakes
- **Token-aware decomposition** to prevent context overflow

The plugin enables a main agent to orchestrate multiple subagents working concurrently on independent tasks, with automatic worktree management, branch merging, and conflict resolution.

---

## Source Materials

1. Base repository: https://github.com/cowwoc/cat
3. Quality gates and TDD integration
4. Prompt engineering: https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/overview

---

## Hierarchy Structure

**MAJOR → MINOR → TASK** 

### Directory Structure

```
.claude/cat/
├── PROJECT.md                              # Project overview and goals
├── ROADMAP.md                              # One-liner summaries of major/minor versions
├── cat-config.json                         # Plugin configuration
└── v<major>/
    ├── STATE.md                            # Major version state
    ├── PLAN.md                             # Major version plan (business-level)
    ├── CHANGELOG.md                        # Major version changelog (aggregates tasks)
    └── v<major>.<minor>/
        ├── STATE.md                        # Minor version state
        ├── PLAN.md                         # Minor version plan (feature-level)
        ├── CHANGELOG.md                    # Minor version changelog (aggregates tasks)
        └── <task-name>/
            ├── STATE.md                    # Task state
            └── PLAN.md                     # Task plan (technical-level)
```

> **NOTE**: Task-level CHANGELOG.md files are not created. Task changelog content is embedded
> in commit messages instead (see commit message format in execute-task command).

### Example Path

```
.claude/cat/v1/v1/parse-switch-statements/STATE.md
```

### Version Semantics

| Aspect | Description |
|--------|-------------|
| Numbering | 0-based, but users typically start with major=1, minor=0 |
| MAJOR versions | New features or capabilities |
| MINOR versions | Bugfixes and smaller feature additions |
| Version decisions | Creating MAJOR vs MINOR is purely a user decision |

---

## Document Formats

### STATE.md (Task Metadata)

**Minimal format (for new tasks):**

```markdown
# Task State: {task-name}

## Status
status: pending | in-progress | completed
progress: 0-100%
started: YYYY-MM-DD | N/A
completed: YYYY-MM-DD | N/A
duration: {duration} | N/A

## Dependencies
- {dependency-description}

## Key Files
To be determined during implementation
```

**Rich format (for completed tasks or imports):**

```markdown
# Task State: {task-name}

## Status
status: completed
progress: 100%
started: 2026-01-08
completed: 2026-01-10
duration: ~45min

## Dependencies
- task-name-1: Provides X capability
- task-name-2: Required for Y

## Provides
- Capability delivered by this task
- Another capability

## Key Files
created:
- path/to/new/file.java

modified:
- path/to/existing/file.java

## Key Decisions
- Decision made during implementation
- Another decision

## Patterns Established
- Pattern that can be reused

## Metadata
subsystem: parser
tags: [feature, api]
affects: [future-work-area]
```

**Notes:**
- Tasks are blocked implicitly if any dependencies are incomplete
- Rich format used when importing from existing planning systems
- Metadata section optional but recommended for searchability

### PLAN.md (Template-Based)

Provide templates appropriate to the work type:

#### Feature Template

```markdown
# Plan: [Task Name]

## Goal
[What this feature accomplishes]

## Approach
[High-level implementation strategy]

## Files to Modify
- path/to/file1.ext - [reason]
- path/to/file2.ext - [reason]

## Dependencies
- [task-name] - [why needed]

## Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

## Execution Steps
1. Step 1
2. Step 2
3. Step 3
```

#### Bugfix Template

```markdown
# Plan: [Task Name]

## Problem
[Description of the bug]

## Root Cause
[Analysis of why the bug occurs]

## Fix Approach
[How the fix will work]

## Files to Modify
- path/to/file.ext - [change description]

## Test Cases
- [ ] Test case 1
- [ ] Test case 2

## Execution Steps
1. Step 1
2. Step 2
```

#### Refactor Template

```markdown
# Plan: [Task Name]

## Current State
[What exists now]

## Target State
[What it should become]

## Rationale
[Why this refactor is needed]

## Files to Modify
- path/to/file.ext - [change description]

## Risk Assessment
- [Potential risk 1]
- [Potential risk 2]

## Execution Steps
1. Step 1
2. Step 2
```

### Task Commit Message Format (Replaces Task CHANGELOG.md)

Task changelog content is embedded in commit messages instead of separate CHANGELOG.md files.
The commit diff implies Files Created, Files Modified, and Test Coverage - these are not duplicated.

```
{type}: {concise description}

## Problem Solved
[WHY this task was needed]
- Problem 1
- Problem 2 if applicable

## Solution Implemented
[HOW the problem was solved]
- Key implementation detail 1
- Key implementation detail 2

## Decisions Made (optional)
- Decision: rationale

## Known Limitations (optional)
- Limitation: why accepted

## Deviations from Plan (optional)
- Deviation: reason and impact

Task ID: v{major}.{minor}-{task-name}
```

**Notes:**
- All task commits include `Task ID: v{major}.{minor}-{task-name}` footer
- Find commits for any task: `git log --oneline --grep="Task ID: v{major}.{minor}-{task-name}"`

### Minor/Major Version CHANGELOG.md

Minor and major versions have CHANGELOG.md files that aggregate completed tasks.
These are created during version completion to summarize multiple task commits.

### ROADMAP.md

Lists major/minor versions with descriptions and their constituent tasks. Minor versions group related tasks.

> **Key concept:** A minor version contains MULTIPLE tasks. Do NOT create one minor per task.
> Group logically related work (2-8 tasks) into each minor version.

```markdown
# Roadmap

## Version 1: Core Parser Implementation
- **1.0:** Basic tokenization and AST generation
  - implement-lexer
  - implement-token-types
  - build-ast-nodes
- **1.1:** Expression parsing support
  - parse-binary-expressions
  - parse-unary-expressions
  - parse-literals

## Version 2: Code Generation
- **2.0:** Basic code emission
  - emit-statements
  - emit-expressions
- **2.1:** Optimization passes
  - constant-folding
  - dead-code-elimination
```

**Grouping guidance:**
- Group tasks by feature area, component, or logical dependency
- A minor version typically has 2-8 tasks
- Tasks within a minor can have dependencies on each other
- Cross-minor dependencies are implicit (minor N depends on minor N-1 completing)

---

## Task Naming & Dependencies

### Task Names (Strict Rules)

| Rule | Constraint |
|------|------------|
| Characters | Lowercase letters and hyphens only |
| Length | Maximum 50 characters |
| Special chars | Not allowed |
| Uniqueness | Must be unique within minor version |

**Valid examples:** `parse-tokens`, `fix-memory-leak`, `add-user-auth`

**Invalid examples:** `Parse_Tokens`, `fix memory leak`, `add-user-authentication-system-with-oauth`

### Dependencies

| Aspect | Rule |
|--------|------|
| Format | Task name only (assumes same minor version) |
| Cross-minor | A minor version implicitly depends on previous minor completing |
| Blocking | Tasks blocked implicitly if any dependencies incomplete |

**Example STATE.md dependencies:**
```markdown
- **Dependencies:** [parse-tokens, build-ast]
```

---

## Agent Architecture

### Main Agent Responsibilities

| Responsibility | Description |
|----------------|-------------|
| Orchestration | Coordinate subagent execution (no direct coding) |
| Planning | Read code, make decisions, decompose tasks |
| Metadata tasks | Planning documents, git operations |
| Merging | Use per-task worktree for integrating subagent branches |
| Conflict resolution | Resolve merge conflicts automatically |
| Queue processing | Handle subagent returns via serial queue |
| State updates | Update STATE.md after each subagent returns |

**Main agent does NOT:**
- Write production code directly
- Execute implementation tasks
- Work in worktrees except for merging

### Subagent Responsibilities

| Responsibility | Description |
|----------------|-------------|
| Execution | Perform coding tasks in dedicated worktrees |
| Token tracking | Monitor context usage throughout execution |
| Compaction detection | Track conversation compaction events |
| Reporting | Return metrics to main agent on completion |
| Fail-fast | Return error immediately if plan has issues |

### Token Tracking Mechanism

Subagents read their session file to track usage:

```
/home/node/.config/claude/projects/-workspace/{SESSION_ID}.jsonl
```

**Metrics to collect:**
1. Sum `input_tokens + output_tokens` from all messages
2. Count entries with `type: "summary"` to detect compaction events
3. Return both metrics to main agent

### Task Decomposition Strategy

| Scenario | Action |
|----------|--------|
| Task too large | Split into multiple user-visible tasks |
| 40% threshold | Soft target (default 80K of 200K tokens) |
| Compaction occurs | Flag task for decomposition |
| During /cat:add-task | Encourage user to pre-decompose if doubt about fit |

**Important:** Decomposition creates new tasks, not internal chunks within a task.

---

## Worktree & Branch Management

### Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Task branch | `{major}.{minor}-{task-name}` | `1.0-parse-tokens` |
| Subagent branch | `{major}.{minor}-{task-name}-sub-{uuid}` | `1.0-parse-tokens-sub-a1b2c3` |

### Worktree Allocation

| Purpose | Allocation |
|---------|------------|
| Main agent | One worktree per task (for merging only) |
| Subagent | One worktree per execution |

### Merge Flow

```
1. Subagent works in worktree/branch
         ↓
2. Subagent completes, returns to main agent
         ↓
3. Main agent merges subagent branch → task branch
         ↓
4. Repeat for all subagents on task
         ↓
5. User approval gate (interactive mode)
         ↓
6. Squash commits by type (one commit per: feature, bugfix, refactor, etc.)
         ↓
7. Merge task branch → main
         ↓
8. Cleanup worktrees
```

### Worktree Lifecycle

- **Creation:** When task/subagent execution begins
- **Cleanup:** Immediately after successful merge to main
- **Locking:** Use locking mechanism from claude-code-cat to avoid conflicts

---

## Configuration

### cat-config.json

```json
{
  "yoloMode": false,
  "contextLimit": 200000,
  "targetContextUsage": 40,
  "autoCleanupWorktrees": true
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| yoloMode | boolean | false | Skip approval gates, auto-proceed |
| contextLimit | number | 200000 | Total context window in tokens |
| targetContextUsage | number | 40 | Target max usage as percentage (40 = 40% = 80K tokens) |
| autoCleanupWorktrees | boolean | true | Clean worktrees after merge |

---

## Operating Modes

### Interactive Mode (Default)

| Behavior | Description |
|----------|-------------|
| Approval gates | Required at task level |
| Next task | Main agent proposes when dependencies met |
| Merge | Requires user acceptance before merge to main |

### Yolo Mode

| Behavior | Description |
|----------|-------------|
| Approval gates | Skipped |
| Next task | Main agent begins immediately when dependencies met |
| Merge | Automatic on task completion |

**Enable via:** Set `"yoloMode": true` in cat-config.json

---

## Error Handling

### Subagent Failure Escalation

```
1. Subagent encounters error
         ↓
2. Subagent fails fast, returns error to main agent
         ↓
3. Main agent attempts resolution
         ↓
4. If resolved → continue
   If unresolved → escalate to user
```

### User Escalation Information (Detailed)

When escalating to user, provide:

- Error message
- Which task failed
- Subagent logs
- Suggested remediation steps

### Unplanned Issues During Development

When bugs/issues discovered that aren't planned tasks:

1. Main agent proposes adding as new task
2. User decides which minor/major version to add to
3. If accepted, creates task via normal flow

### Learn-from-Mistakes Integration

Adapted from claude-code-cat with CAT-specific additions:

| Factor | Consideration |
|--------|---------------|
| Conversation length | If mistakes occur late, suspect context degradation |
| Recommendation | Decompose work earlier when context-related |
| Token metrics | Use subagent token reports to identify patterns |

---

## Persistence Model

### Hybrid Approach

| Aspect | Persistence |
|--------|-------------|
| Progress | STATE.md files are source of truth |
| Cross-session | Can resume via STATE.md |
| Active subagent work | Session-scoped (lost if session ends mid-execution) |

**Recovery:** If session ends during subagent execution, STATE.md reflects last known state. User can resume with /cat:execute-task.

---

## Commands

### Core Commands

| Command | Description |
|---------|-------------|
| `/cat:new-project` | Initialize new project with PROJECT.md and ROADMAP.md |
| `/cat:existing-project` | Add CAT structure to existing codebase (see details below) |
| `/cat:execute-task` | Execute task (continues incomplete work) |
| `/cat:status` | Show all levels (MAJOR→MINOR→TASK) hierarchy status |

### /cat:existing-project Details

Interactive wizard that:
1. Analyzes codebase (language, stack, git history, tests)
2. Infers current state (validated requirements, in-progress work)
3. **Detects existing planning files** by content patterns (not filenames):
   - Task definitions: files containing `## Objective`, `## Tasks`, `## Technical Approach`
   - Completion records: files containing `## Accomplishments`, `completed:`, YAML frontmatter
4. Creates planning documents (PROJECT.md, ROADMAP.md, cat-config.json)
5. **Groups related tasks into minor versions** (2-8 tasks per minor, NOT 1:1 mapping)
6. **Creates task directories for ALL tasks** with FULL content import:

   > **CRITICAL:** Do NOT create placeholder files that reference the old planning system.
   > Each CAT file must be complete and standalone.

   **PLAN.md** - Import full task definition:
   - Objective, Problem Analysis, Example Code
   - Tasks (preserve checkbox state)
   - Technical Approach, Verification criteria

   **STATE.md** - Import rich metadata:
   - Status, dates, duration
   - Dependencies (requires/provides)
   - Key files (created/modified)
   - Key decisions, patterns established
   - Metadata (subsystem, tags, affects)

   > **NOTE**: Task CHANGELOG.md files are not created during import.
   > Completion details are preserved in STATE.md and in commit messages.

7. Sets task status based on discovered data:
   - Completion record exists with `completed:` date → `status: completed`, `progress: 100%`
   - Git branch exists matching task name → `status: in-progress`, `progress: 50%`
   - Neither exists → `status: pending`, `progress: 0%`
8. Suggests next action:
   - If pending/in-progress tasks exist → `/cat:execute-task {major}.{minor}/{task-name}`
   - Otherwise → `/cat:add-task`

### Add Commands

| Command | Parameters | Description |
|---------|------------|-------------|
| `/cat:add-task` | `[major.minor]` (optional) | Add task to minor version |
| `/cat:add-minor-version` | `[major]` (optional) | Add minor version to major |
| `/cat:add-major-version` | (none) | Add new major version |

**Behavior:**
- Optional parameters prompt user if omitted
- `/cat:add-task` enforces unique task names; prompts for different name on conflict
- All add commands use discussion to auto-generate comprehensive PLAN.md

### Remove Commands

| Command | Description |
|---------|-------------|
| `/cat:remove-task` | Remove a task |
| `/cat:remove-minor-version` | Remove a minor version |
| `/cat:remove-major-version` | Remove a major version |

---

## CAT-Specific Skills

New skills unique to CAT's multi-agent orchestration model:

| Skill | Description |
|-------|-------------|
| `/cat:spawn-subagent` | Launch subagent with task context, worktree setup, token tracking |
| `/cat:monitor-subagents` | Check status of running subagents, token usage, compaction events |
| `/cat:collect-results` | Gather results from completed subagents, update STATE.md |
| `/cat:merge-subagent` | Merge subagent branch into task branch, resolve conflicts |
| `/cat:token-report` | Generate token usage report for current session/task |
| `/cat:decompose-task` | Split oversized task into smaller tasks based on token analysis |
| `/cat:learn-from-mistakes` | Analyze mistakes with conversation length as potential cause |
| `/cat:parallel-execute` | Orchestrate multiple independent subagents concurrently |

---

## Adapted Skills

All skills, hooks, and scripts from `/workspace/claude-code-cat/` adapted to MAJOR→MINOR→TASK terminology.

### Key Adaptations

| Aspect | Adaptation |
|--------|------------|
| Terminology | Replace "milestone/release" with "major/minor/task" |
| File paths | Match CAT directory structure |
| Branch names | Integrate with CAT naming conventions |
| State updates | Hook into CAT's STATE.md update flow |

### Categories (from claude-code-cat)

| Category | Skills |
|----------|--------|
| Git operations | git-commit, git-squash, git-rebase, git-amend, git-merge-linear |
| Planning | plan-release → plan-task, discuss-release → discuss-task |
| Quality | verify-work, validate-git-safety |
| Workflow | progress → status, execute-change → execute-task |
| Utilities | batch-read, grep-and-read, safe-rm |

---

## Quality Controls

### From claude-code-cat

1. **Comprehensive planning** before each task
2. **Extensive quality controls** and gates after task completion
3. **TDD integration** to prevent regressions
4. **Preventative measures** to avoid repeating mistakes
5. **Hierarchical CONVENTIONS.md** for just-in-time code convention information

### Approval Gate Flow (Interactive Mode)

```
1. Task work complete
         ↓
2. Squash commits by type
         ↓
3. Present to user:
   - Overview of what was done
   - Branch name for review
   - Files changed summary
         ↓
4. User response:
   - Request changes → iterate
   - Approve → merge to main
```

---

## Core Principles

1. **Non-linear progression** - Tasks execute based on dependencies, not sequence
2. **Concurrent execution** - Multiple agents work on independent tasks simultaneously
3. **Orchestration model** - Main agent coordinates, subagents execute
4. **No arbitrary limits** - No cap on parallel subagents (main agent manages)
5. **Fail-fast** - Subagents return errors immediately on plan issues
6. **Automatic conflict resolution** - Main agent handles merge conflicts
7. **Token awareness** - Decompose work to fit context limits
8. **Persistence** - STATE.md enables cross-session resume

---

## Appendix: Quick Reference

### File Locations

| File | Location |
|------|----------|
| Project config | `.claude/cat/cat-config.json` |
| Project overview | `.claude/cat/PROJECT.md` |
| Roadmap | `.claude/cat/ROADMAP.md` |
| Major state | `.claude/cat/v{n}/STATE.md` |
| Minor state | `.claude/cat/v{n}/v{n}.{m}/STATE.md` |
| Task state | `.claude/cat/v{n}/v{n}.{m}/{name}/STATE.md` |

### Command Quick Reference

```
/cat:new-project          - Start new project
/cat:existing-project     - Add CAT to existing project (creates task directories)
/cat:status               - View all status
/cat:execute-task         - Run/continue task
/cat:add-task [m.n]       - Add task
/cat:add-minor-version [m] - Add minor
/cat:add-major-version    - Add major
/cat:remove-task          - Remove task
/cat:remove-minor-version - Remove minor
/cat:remove-major-version - Remove major
```

### Branch Patterns

```
Task:     {major}.{minor}-{task-name}
Subagent: {major}.{minor}-{task-name}-sub-{uuid}
```
