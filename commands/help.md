---
name: cat:help
description: Quick reference for all CAT commands and skills
model: haiku
context: fork
---

<objective>

Display the complete CAT command and skill reference.

Output ONLY the reference content below. Do NOT add:
- Project-specific analysis
- Git status or file context
- Next-step suggestions
- Any commentary beyond the reference

</objective>

<reference>

# CAT Command Reference

**CAT** enables hierarchical project planning with multi-agent task execution.

## Quick Start

1. `/cat:init` - Initialize project structure (new or existing codebase)
2. `/cat:add` - Add versions and tasks (interactive)
3. `/cat:work` - Execute the next available task

## Hierarchy Structure

```
MAJOR -> MINOR -> TASK

.claude/cat/
├── PROJECT.md                    # Project overview
├── ROADMAP.md                    # Version summaries
├── cat-config.json               # Configuration
└── v{n}/
    ├── STATE.md                  # Major version state
    ├── PLAN.md                   # Business-level plan
    ├── CHANGELOG.md              # Major changelog (aggregates tasks)
    └── v{n}.{m}/
        ├── STATE.md              # Minor version state
        ├── PLAN.md               # Feature-level plan
        ├── CHANGELOG.md          # Minor changelog (aggregates tasks)
        └── {name}/
            ├── STATE.md          # Task state
            └── PLAN.md           # Technical-level plan
```

> Task changelog content is embedded in commit messages (see work commit format).

## Core Commands

### Project Initialization

**`/cat:init`**
Initialize CAT planning structure (new or existing project).
- Creates PROJECT.md, ROADMAP.md, cat-config.json
- Asks for workflow mode (Interactive/YOLO)
- For new projects: Deep questioning to gather project context
- For existing codebases: Detects patterns and infers current state

### Task Execution

**`/cat:work [scope]`**
Work on tasks with automatic progression.

| Scope Format | Example | Behavior |
|--------------|---------|----------|
| (none) | `/cat:work` | Work through all incomplete tasks |
| major | `/cat:work 0` | Work through all tasks in v0.x |
| minor | `/cat:work 0.5` | Work through all tasks in v0.5 |
| task | `/cat:work 0.5-parse` | Work on single specific task |

**Features:**
- Auto-continues to next task when trust >= medium
- Creates worktree and task branch per task
- Spawns subagent for isolated execution
- Monitors token usage
- Runs approval gate (when trust < high)
- Squashes commits by type
- Merges to main and cleans up

### Status

**`/cat:status`**
Show complete hierarchy status.
- Visual tree of all versions and tasks
- Progress bar and percentages
- Current position highlighted
- Blocked tasks explained
- Next action suggested

### Adding Structure

**`/cat:add`**
Unified command for adding tasks or versions.
- Asks what to add: Task, Minor version, or Major version
- For tasks: Discussion workflow generates PLAN.md
- For versions: Handles number conflicts with insert/renumber option
- Validates names and checks uniqueness

### Removing Structure

**`/cat:remove`**
Unified command for removing tasks or versions.
- Asks what to remove: Task, Minor version, or Major version
- Validates no work in progress
- Checks for dependencies
- Confirms with user before removal

## CAT-Specific Skills

| Skill | Description |
|-------|-------------|
| `/cat:spawn-subagent` | Launch subagent with task context and token tracking |
| `/cat:monitor-subagents` | Check status of running subagents |
| `/cat:collect-results` | Gather results from completed subagents |
| `/cat:merge-subagent` | Merge subagent branch into task branch |
| `/cat:token-report` | Generate token usage report |
| `/cat:decompose-task` | Split oversized task into smaller tasks |
| `/cat:learn-from-mistakes` | Analyze mistakes with conversation length as factor |
| `/cat:parallel-execute` | Orchestrate multiple subagents concurrently |

## Workflow Modes

Set during `/cat:init` in cat-config.json:

**Interactive Mode** (default)
- Approval gates at task completion
- User confirms before merge to main
- Pauses for review opportunities

**YOLO Mode**
- Skips approval gates
- Auto-merges on task completion
- Continuous execution

Change anytime by editing `.claude/cat/cat-config.json`

## Task Naming Rules

- Lowercase letters and hyphens only
- Maximum 50 characters
- Must be unique within minor version

**Valid:** `parse-tokens`, `fix-memory-leak`, `add-user-auth`
**Invalid:** `Parse_Tokens`, `fix memory leak`, `add-very-long-task-name-that-exceeds-limit`

## Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Task | `{major}.{minor}-{task-name}` | `1.0-parse-tokens` |
| Subagent | `{major}.{minor}-{task-name}-sub-{uuid}` | `1.0-parse-tokens-sub-a1b2c3` |

## Common Workflows

**Starting a new project:**
```
/cat:init
/cat:add          # Select "Major version", then "Task"
/cat:work
```

**Checking progress:**
```
/cat:status
```

**Adding more work:**
```
/cat:add          # Interactive: choose Task, Minor, or Major
```

**Removing planned work:**
```
/cat:remove       # Interactive: choose Task, Minor, or Major
```

## Configuration Options

cat-config.json:
```json
{
  "contextLimit": 200000,       // Total context window
  "targetContextUsage": 40,     // Soft limit (40%)
  "trust": "medium",            // low | medium | high (autonomy level)
  "verify": "changed",          // changed | all (verification scope)
  "curiosity": "medium",        // low | medium | high (exploration level)
  "patience": "medium"          // low | medium | high (refactoring tolerance)
}
```

## Getting Help

- Read `.claude/cat/PROJECT.md` for project vision
- Check `.claude/cat/ROADMAP.md` for version overview
- Use `/cat:status` to see current state
- Review individual STATE.md files for detailed progress

</reference>
