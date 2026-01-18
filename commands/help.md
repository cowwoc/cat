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

---

## Essential Commands (Start Here)

These three commands cover 90% of daily use:

| Command | What It Does |
|---------|--------------|
| `/cat:init` | Set up a new or existing project |
| `/cat:status` | See what's happening and what to do next |
| `/cat:work` | Execute the next available task |

**Minimum viable workflow:**
```
/cat:init    →    /cat:add    →    /cat:work
   ↑                                    │
   └────────── /cat:status ─────────────┘
```

---

## Planning Commands

Use these when you need to structure your work:

| Command | What It Does |
|---------|--------------|
| `/cat:add` | Add tasks, minor versions, or major versions |
| `/cat:remove` | Remove tasks or versions (with safety checks) |
| `/cat:config` | Change workflow mode, trust level, preferences |

---

## Advanced Commands

Power user features for complex workflows:

| Command | What It Does |
|---------|--------------|
| `/cat:research` | Run stakeholder research on pending versions |
| `/cat:cleanup` | Clean up abandoned worktrees and stale locks |
| `/cat:spawn-subagent` | Launch isolated subagent for a task |
| `/cat:monitor-subagents` | Check status of running subagents |
| `/cat:collect-results` | Gather results from completed subagents |
| `/cat:merge-subagent` | Merge subagent branch into task branch |
| `/cat:token-report` | Generate token usage report |
| `/cat:decompose-task` | Split oversized task into smaller tasks |
| `/cat:parallel-execute` | Orchestrate multiple subagents concurrently |

---

## Full Reference

<details>
<summary>📁 Hierarchy Structure</summary>

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

</details>

<details>
<summary>⚙️ /cat:init Details</summary>

Initialize CAT planning structure (new or existing project).
- Creates PROJECT.md, ROADMAP.md, cat-config.json
- Asks for workflow mode (Interactive/YOLO)
- For new projects: Deep questioning to gather project context
- For existing codebases: Detects patterns and infers current state
- Offers guided first-task creation after setup

</details>

<details>
<summary>🔨 /cat:work Scope Options</summary>

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

</details>

<details>
<summary>📋 Task Naming Rules</summary>

- Lowercase letters and hyphens only
- Maximum 50 characters
- Must be unique within minor version

**Valid:** `parse-tokens`, `fix-memory-leak`, `add-user-auth`
**Invalid:** `Parse_Tokens`, `fix memory leak`, `add-very-long-task-name-that-exceeds-limit`

</details>

<details>
<summary>🌿 Branch Naming</summary>

| Type | Pattern | Example |
|------|---------|---------|
| Task | `{major}.{minor}-{task-name}` | `1.0-parse-tokens` |
| Subagent | `{major}.{minor}-{task-name}-sub-{uuid}` | `1.0-parse-tokens-sub-a1b2c3` |

</details>

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
