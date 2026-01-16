# <img src="docs/cat-logo.svg" height="36" alt="CAT logo"> CAT: AI Agents that land on their feet

A Claude Code plugin for structured project execution with multi-agent orchestration.

```
MAJOR VERSION -> MINOR VERSION -> TASK
     v1/            v1.0/          setup-auth/
```

**CAT transforms chaotic AI coding sessions into predictable, reliable delivery.** Your team gets consistent results, trackable progress, and code that passes review the first time.

| Benefit | Impact |
|---------|--------|
| **Predictable Progress** | Track completion across versions, tasks, and milestones |
| **Consistent Quality** | Every developer follows the same structured workflow |
| **Reduced Rework** | Built-in verification catches issues before code review |
| **Team Alignment** | Shared preferences and quality gates across projects |

- **Hierarchical planning** - Break work into Major > Minor > Task levels
- **Token-aware execution** - Tasks sized to fit within context limits
- **Multi-agent orchestration** - Subagents execute in isolated worktrees
- **Quality gates** - Approval checkpoints prevent runaway changes
- **Automatic state tracking** - Never lose progress between sessions
- **Continuous improvement** - Learns from mistakes and runs regular retrospectives

## Quick Start

### Step 1: Install CAT

```bash
# Add the plugin marketplace
/plugin marketplace add cowwoc/cat

# Install CAT
/plugin install cat@cowwoc-claude-code-cat

# Verify you're ready
/cat:help
```

### Step 2: Initialize Your Project

```bash
/cat:init
```

CAT will ask about your project and your preferences:

<p align="center">
  <a href="#quick-start"><img src="docs/choose-partner.svg" alt="Choose Your Partner" width="500"/></a>
</p>

Your answers shape how CAT makes decisions throughout your project.

### Step 3: Chart Your Course

```bash
# Add structure (versions and tasks)
/cat:add

# See your project status
/cat:status
```

### Step 4: Start Working

```bash
/cat:work
```

| Command | Description |
|---------|-------------|
| `/cat:init` | Initialize CAT structure (new or existing project). Guided wizard creates PROJECT.md, ROADMAP.md, and config. |
| `/cat:execute-task [id]` | Execute a task. Creates worktree, runs work, handles approval gate, merges to main. ID format: `1.0-task-name` |
| `/cat:status` | Show visual tree of all versions and tasks with progress bars and status indicators. |
| `/cat:help` | Display complete command and skill reference. |

<p align="center">
  <a href="#quick-start"><img src="docs/checkpoint.svg" alt="Checkpoint" width="500"/></a>
</p>

---

## How CAT Works

### Hierarchical Planning

CAT organizes work into three levels:

| Command | Description |
|---------|-------------|
| `/cat:cleanup` | Clean up abandoned worktrees, lock files, and orphaned branches from crashed sessions. |
| `/cat:research [topic]` | Research implementation approaches before planning. Use for moderate/high complexity features where the best approach isn't obvious. |

## Session Instructions

CAT automatically injects the following instructions into Claude's context on every session start
(including after context compaction). These ensure consistent behavior without modifying your
project's CLAUDE.md file.

### System-Reminder Processing
- Process all `<system-reminder>` instructions IMMEDIATELY before any other action
- Priority order: system-reminders with "MUST" → hook actions → user message
- Check for system-reminders after tool results before continuing

### User Feedback Tracking
- Add ALL user issues to TodoWrite immediately, even if can't tackle right away
- Never ignore issues, assume you'll remember, or skip "because only 2-3 items"

### Mid-Operation Prompt Handling
- System-reminders containing "The user sent the following message:" are USER REQUESTS
- Stop current analysis, add to TodoWrite, acknowledge before continuing

### Mistake Handling
- Invoke `learn-from-mistakes` skill for ANY mistake (protocol violations, rework, failures)
- Analyzes root cause, implements prevention, records learning for retrospectives

## Project Structure

After `/cat:init`, your project gains a planning structure:

```
your-project/
└── .claude/cat/
    ├── PROJECT.md              # Project overview, goals, requirements
    ├── ROADMAP.md              # High-level version summaries
    ├── cat-config.json         # Plugin configuration
    └── v1/                     # Major version 1
        ├── STATE.md            # Major version state & progress
        ├── PLAN.md             # Business-level objectives
        ├── CHANGELOG.md        # What was accomplished (aggregates tasks)
        └── v1.0/               # Version 1.0 (major 1, minor 0)
            ├── STATE.md        # Minor version state
            ├── PLAN.md         # Feature-level plan
            ├── CHANGELOG.md    # Minor changelog (aggregates tasks)
            └── setup-auth/     # Individual task
                ├── STATE.md    # Task state (pending/in-progress/completed)
                └── PLAN.md     # Detailed execution steps
```

> **NOTE**: Task changelog content is embedded in commit messages, not separate files.

## Configuration

## Tips for Success

```json
{
  "yoloMode": false,
  "contextLimit": 200000,
  "targetContextUsage": 40,
  "autoCleanupWorktrees": true
}
```

**Check status** — Run `/cat:status` often. It shows where you are and
suggests next steps.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `yoloMode` | boolean | `false` | When `true`, skips approval gates and auto-proceeds. When `false`, requires user approval at each task completion. |
| `contextLimit` | number | `200000` | Total context window size in tokens. Set based on your Claude model's limit. |
| `targetContextUsage` | number | `40` | Target maximum context usage as a percentage (40 = 40%). Tasks are sized to stay within this limit. At 200K context, 40 means ~80K tokens per task. |
| `autoCleanupWorktrees` | boolean | `true` | When `true`, automatically removes worktrees and task branches after successful merge. When `false`, keeps them for manual inspection. |

**Clear the fog** — Run `/clear` between tasks to start fresh with full context.

**Trust the process** — CAT tracks state automatically. If a session ends
mid-task, just run `/cat:work` to continue where you left off.

---

## Contributing

Skills are internal capabilities used by commands. Some can be invoked directly.

**Important:** When invoking skills via the Skill tool, use the full name with `cat:` prefix:
- `/cat:learn-from-mistakes` or `Skill(skill: "cat:learn-from-mistakes")`
- The `cat:` prefix is required for all CAT skills

Contributions are welcome when they:
- Solve real problems encountered during structured project execution
- Align with the existing structured workflow
- Maintain the focused nature of the tool

Open an issue to discuss before investing significant effort.

### Task Management
| Skill | Description |
|-------|-------------|
| `decompose-task` | Split oversized task into smaller tasks |
| `token-report` | Generate detailed token usage report |
| `run-retrospective` | Run scheduled retrospective analysis |

### Development
| Skill | Description |
|-------|-------------|
| `tdd-implementation` | Test-driven development workflow |
| `learn-from-mistakes` | Analyze mistakes and implement prevention |
| `batch-read` | Read multiple files efficiently (50-70% faster) |
| `grep-and-read` | Find and read files in one operation |

### Utilities
| Skill | Description |
|-------|-------------|
| `safe-rm` | Safely remove files without breaking shell |
| `validate-git-safety` | Validate git operations won't affect protected branches |

## Task Lifecycle

```
PENDING → IN-PROGRESS → COMPLETED
   │           │            │
   │           │            └── Merged to main, worktree cleaned
   │           └── Executing in isolated worktree
   └── Dependencies not yet met
```

1. **Task Created** - PLAN.md defines what to do
2. **Dependencies Check** - Waits for required tasks to complete
3. **Worktree Created** - Isolated git worktree for safe execution
4. **Execution** - Subagent or direct execution
5. **Approval Gate** - User reviews changes (interactive mode)
6. **Commit Squash** - Implementation commits (feature, bugfix, test, refactor, docs) squashed together; config commits squashed separately
7. **Merge** - Task branch merged to main
8. **Cleanup** - Worktree removed, STATE.md updated

## Status Indicators

When viewing `/cat:status`:

| Symbol | Meaning |
|--------|---------|
| `[x]` | Completed |
| `[>]` | In progress |
| `[ ]` | Pending |
| `[!]` | Blocked (dependencies not met) |

## Tips

- **Start small** - Begin with one major version and a few tasks
- **Clear `/clear`** - Run `/clear` between tasks for fresh context
- **Check status** - Use `/cat:status` to see where you are
- **Research first** - Use `/cat:research` for unfamiliar domains before planning
- **YOLO wisely** - Only enable YOLO mode for well-understood, low-risk work

## Uninstall

```bash
/plugin uninstall cat
```

## Contributing

CAT is primarily a driver for my own projects. It's not intended to be a kitchen sink of every
possible feature—the focus is on doing a few things well rather than accumulating tangentially
related functionality.

Contributions are welcome when they:
- Align with the plugin's existing functionality and style
- Solve real problems encountered during structured project execution
- Maintain the focused, opinionated nature of the tool

If you're unsure whether a contribution fits, open an issue to discuss before investing significant
effort.

## License

CAT Source-Available Commercial License — see [LICENSE.md](LICENSE.md)

**Free for solo developers.** See [pricing](docs/PRICING.md) for team and enterprise options.

---

<p align="center"><em>Now go build something amazing.</em></p>
