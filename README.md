# <img src="docs/cat-logo.svg" height="36" alt="CAT logo"> CAT: AI Agents that land on their feet

Development should be **fun** AND **reliable**. CAT makes both happen.

```
MAJOR VERSION -> MINOR VERSION -> TASK
     v1/            v1.0/          setup-auth/
```

**CAT transforms chaotic AI coding sessions into predictable, reliable delivery.** Your team gets consistent results, trackable progress, and code that passes review the first time.

### 🎮 Fun: Your Development Adventure

Large AI projects often feel like babysitting—constant hand-holding, context loss,
unpredictable results. CAT transforms this into an engaging experience:

- **Your Adventure, Your Choices** - Express your style once, make meaningful choices
  at genuine decision points, watch your project unfold
- **Smart Recommendations** - When you face a fork in the road, CAT suggests the
  best path based on task characteristics (but you always choose)
- **Visual Progress** - See your adventure map, track decisions made, celebrate
  completed quests
- **Flow State** - Routine tasks auto-proceed; you're only interrupted when your
  input genuinely matters

### 🛡️ Reliable: Trust the Process

Fun without reliability is chaos. CAT ensures your adventure has guardrails:

- **Hierarchical Planning** - Break work into Major > Minor > Task levels
- **Token-Aware Execution** - Tasks sized to fit within context limits
- **Multi-Agent Orchestration** - Subagents execute in isolated worktrees
- **Quality Gates** - Stakeholder reviews catch issues before merge
- **Automatic State Tracking** - Never lose progress between sessions
- **Continuous Improvement** - Learns from mistakes, runs retrospectives

**The Balance:** Reliability enables trust. Trust enables flow. Flow enables fun.

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

## Development Philosophy

CAT is built on a simple insight: **development is more fun when you can trust the process**.

When you trust that:
- Context won't overflow mid-task
- Progress won't be lost between sessions
- Quality gates will catch problems
- Mistakes will be learned from

...then you can relax into the adventure. Make bold choices. Try the comprehensive
refactor. Let the system handle the bookkeeping while you focus on the creative decisions.

**Your Role:** Express your style, make meaningful choices, guide the direction.

**CAT's Role:** Handle execution, track state, ensure quality, learn and improve.

## Commands Reference

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

### User Input Handling
- Process ALL user input IMMEDIATELY, regardless of how it arrives
- User input sources: direct messages, system-reminders with "The user sent the following message:",
  or system-reminders with "MUST"/"Before proceeding"/"AGENT INSTRUCTION"
- Priority order: mandatory system-reminders → hook actions → user message content
- When input arrives mid-operation: stop, add to TodoWrite, acknowledge before continuing
- Never ignore issues or assume you'll remember—always TodoWrite immediately

### Mistake Handling
- Invoke `learn-from-mistakes` skill for ANY mistake (protocol violations, rework, failures)
- Analyzes root cause, implements prevention, records learning for retrospectives

### Skill Workflow Compliance
- When a skill is invoked, follow its documented workflow COMPLETELY
- Never invoke a skill then manually do a subset of steps
- Execute every step in sequence; if a step doesn't apply, note why and continue

### Commit Before Review
- ALWAYS commit changes BEFORE asking users to review implementation
- Users cannot see unstaged changes in their environment
- Pattern: Implement → Commit → Then ask for review

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
  "autoCleanupWorktrees": true,
  "adventureMode": {
    "enabled": true,
    "preferences": {
      "approach": "balanced",
      "stakeholderReview": "high-risk-only",
      "refactoring": "opportunistic"
    }
  }
}
```

**Check status** — Run `/cat:status` often. It shows where you are and
suggests next steps.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `yoloMode` | boolean | `false` | When `true`, skips approval gates and auto-proceeds. When `false`, requires user approval at each task completion. |
| `contextLimit` | number | `200000` | Total context window size in tokens. Set based on your Claude model's limit. |
| `targetContextUsage` | number | `40` | Target maximum context usage as a percentage (40 = 40%). Tasks are sized to stay within this limit. At 200K context, 40 means ~80K tokens per task. |

### Adventure Mode Preferences

These preferences shape how CAT makes autonomous decisions. Set during `/cat:init` or update with `/cat:update-preferences`.

| Preference | Values | Effect |
|------------|--------|--------|
| `approach` | `conservative`, `balanced`, `aggressive` | Influences approach recommendations at decision points |
| `stakeholderReview` | `always`, `high-risk-only`, `never` | Controls when multi-perspective review triggers |
| `refactoring` | `avoid`, `opportunistic`, `eager` | Determines cleanup behavior on adjacent code |
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
| `stakeholder-review` | Multi-perspective review (architect, security, quality, tester, performance) |

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
2. **Dependencies Check** - Waits for task and minor version dependencies
3. **Size Analysis** - Auto-decompose if estimated tokens exceed threshold
4. **Worktree Created** - Isolated git worktree for safe execution
5. **Execution** - Subagent execution with token tracking and metrics reporting
6. **Build Verification** - Compile, test, lint checks must pass
7. **Stakeholder Review** - Multi-perspective review (architect, security, quality, tester, performance)
8. **Approval Gate** - User reviews changes (interactive mode)
9. **Merge & Cleanup** - STATE.md updated, commits squashed by type, merged to main, worktree removed

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

CAT Source-Available Commercial License - see [LICENSE.md](LICENSE.md)

Free for personal use and small organizations (< $100K/year revenue).
Commercial use requires a paid license.
