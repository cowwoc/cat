# Project: CAT

## Overview
CAT is a Claude Code plugin for multi-agent task orchestration with comprehensive planning and quality gates. It
organizes work into a MAJOR → MINOR → TASK hierarchy, executes tasks in isolated git worktrees, and provides token-aware
decomposition to prevent context overflow.

## Goals
- Enable reliable, structured AI-assisted software development
- Provide multi-agent orchestration with parallel execution
- Ensure code quality through stakeholder reviews and verification gates
- Make AI work predictable and recoverable

## Requirements

### Validated
- Multi-agent orchestration with isolated worktrees
- Token-aware task decomposition (40% context threshold)
- Stakeholder review system (architect, security, quality, tester, performance)
- Learn-from-mistakes workflow with retrospectives
- Git safety validation and linear history enforcement
- User preference system (trust, effort, patience)
- Interactive and YOLO execution modes

### Active
- Commercialization (pricing tiers, license key system)
- Stability and polish improvements

### Out of Scope
- IDE integrations (VSCode, JetBrains)
- Cloud features (remote execution, sync)
- GUI/Dashboard interfaces

## Tech Stack
- Node.js (plugin runtime)
- Bash (hooks, scripts)
- Bats (test framework, 66+ tests)
- JSON (configuration)
- Markdown (documentation, planning artifacts)

## Repository Structure
```
claude-code-cat/
├── .claude/cat/           # CAT planning structure (this directory)
│   ├── PROJECT.md         # Project overview
│   ├── ROADMAP.md         # Version roadmap
│   ├── cat-config.json    # User preferences
│   ├── rules/             # Coding standards and audience-filtered rules
│   ├── references/        # Reference documentation
│   ├── templates/         # Document templates
│   └── workflows/         # Workflow definitions
├── commands/              # Slash command definitions
├── skills/                # Skill implementations
├── hooks/                 # Session and event hooks
├── scripts/               # Utility scripts
├── tests/                 # Bats test suites
└── docs/                  # User documentation
```

## Key Decisions
| Decision | Rationale | Outcome |
|----------|-----------|---------|
| MAJOR → MINOR → TASK hierarchy | Clear decomposition levels for token management | Adopted |
| Isolated worktrees | Prevent main branch corruption during task execution | Adopted |
| Stakeholder reviews | Multi-perspective quality gates catch diverse issues | Adopted |
| Linear git history | Easier debugging and rollback | Adopted |
| Source-available license | Balance open development with commercial sustainability | Adopted |

## Rules and Conventions

Claude-facing coding standards live in `.claude/cat/rules/`. This directory uses a two-tier system with
audience-aware injection via CAT hooks.

**Structure:**
```
.claude/rules/
└── common.md             # Truly universal content (auto-loaded by Claude Code for ALL agents)

.claude/cat/rules/
├── INDEX.md              # Summary of all rules with audience information
├── common.md             # Common CAT conventions (main + all subagents)
├── hooks.md              # Hook registration rules (main agent only)
├── {language}.md         # Language-specific (java.md, etc.) with paths: frontmatter
└── state-schema.md       # STATE.md schema reference
```

**Frontmatter properties:**
```yaml
---
paths: ["*.java"]      # Only inject when editing matching files (default: always)
---
```

All properties have defaults and can be omitted: `mainAgent` defaults to `true`, `subAgents` defaults to
all, `paths` defaults to always inject.
See `plugin/concepts/rules-audience.md` for full documentation.

**Content guidelines:**
- Optimized for AI consumption (concise, unambiguous, examples over prose)
- Human-facing docs belong elsewhere (`docs/`, `CONTRIBUTING.md`)

## User Preferences

These preferences shape how CAT makes autonomous decisions:

- **Trust Level:** medium - trust routine calls, review key decisions
- **Effort:** medium - explore alternatives and note trade-offs
- **Patience:** medium - grab low-hanging fruit, note the rest

Update anytime with: `/cat:config`

## Related Documentation
- [ROADMAP.md](ROADMAP.md) - Version roadmap
- [cat-config.json](cat-config.json) - CAT configuration
