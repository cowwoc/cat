# CAT Hierarchy: MAJOR > MINOR > TASK

## Structure

```
.claude/cat/
├── PROJECT.md
├── ROADMAP.md
├── cat-config.json
└── v<n>/
    ├── STATE.md
    ├── PLAN.md
    ├── CHANGELOG.md          # Aggregates completed tasks
    └── v<n>.<m>/
        ├── STATE.md
        ├── PLAN.md
        ├── CHANGELOG.md      # Aggregates completed tasks
        └── task/<name>/
            ├── STATE.md
            └── PLAN.md
```

> **NOTE**: Task-level CHANGELOG.md is not created. Task changelog content is embedded
> in commit messages (see commit message format in work command).

## Version Semantics

| Level | Purpose | Numbering |
|-------|---------|-----------|
| MAJOR | New features or capabilities | 0-based (users typically start at 1) |
| MINOR | Bugfixes and smaller additions | 0-based |
| TASK | Atomic unit of work | Named (lowercase-hyphens) |

## Requirements Flow

Requirements are defined at the **minor version level** and traced to tasks:

```
Minor PLAN.md                    Task PLAN.md
┌─────────────────────┐         ┌─────────────────────┐
│ ## Requirements     │         │ ## Satisfies        │
│ | REQ-001 | ...     │ ◄────── │ - REQ-001           │
│ | REQ-002 | ...     │         │ - REQ-003           │
│ | REQ-003 | ...     │         └─────────────────────┘
└─────────────────────┘
```

**Key rules:**
- Requirements live in minor version PLAN.md (not task level)
- Tasks reference requirements via `## Satisfies` field (zero or more)
- A minor version cannot complete until all must-have requirements are satisfied (implicit check)
- The Requirements stakeholder verifies tasks satisfy their claimed requirements
- Exit gates are for additional user-defined conditions, not requirements

## Version Decisions

Creating MAJOR vs MINOR is a user decision:
- **MAJOR**: Significant new capabilities
- **MINOR**: Incremental improvements, fixes

## Minor Version Dependencies

Minor versions have implicit sequential dependencies:

| Scenario | Dependency |
|----------|------------|
| First minor of first major (e.g., v0.0) | None |
| Subsequent minor versions in same major (e.g., v0.1, v0.2) | Previous minor version (v0.0, v0.1) |
| First minor of new major (e.g., v1.0) | Last minor of previous major |

**Examples:**
- `v0.0` → No dependencies (first version)
- `v0.1` → Depends on `v0.0`
- `v0.5` → Depends on `v0.4`
- `v1.0` → Depends on `v0.9` (last minor of v0)
- `v1.1` → Depends on `v1.0`

**A minor version is executable when:**
1. Its dependency minor version is completed (all tasks in that minor are completed)
2. OR it has no dependency (first version)

**Task-level dependencies:**
- Tasks within a minor depend on their explicit dependency list in STATE.md
- Cross-minor task dependencies are not supported (use minor version ordering instead)

## Task Execution Order

**Non-Linear Progression:** Tasks execute based on dependency resolution, not sequence order.

When dependencies are met, tasks become eligible for execution regardless of their position in the
task list. Multiple independent tasks can execute concurrently.

| Task State | Execution Eligibility |
|------------|----------------------|
| Dependencies empty | Immediately eligible |
| All dependencies completed | Eligible |
| Any dependency pending | Blocked until resolved |

This enables parallel execution of independent tasks while maintaining correct ordering for
dependent tasks.

## Example Path

```
.claude/cat/issues/v1/v1.0/parse-switch-statements/STATE.md
```

Components:
- Major version: 1
- Minor version: 0
- Task name: parse-switch-statements
