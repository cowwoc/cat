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
> in commit messages (see commit message format in execute-task command).

## Version Semantics

| Level | Purpose | Numbering |
|-------|---------|-----------|
| MAJOR | New features or capabilities | 0-based (users typically start at 1) |
| MINOR | Bugfixes and smaller additions | 0-based |
| TASK | Atomic unit of work | Named (lowercase-hyphens) |

## Version Decisions

Creating MAJOR vs MINOR is a user decision:
- **MAJOR**: Significant new capabilities
- **MINOR**: Incremental improvements, fixes

## Implicit Dependencies

- A minor version implicitly depends on the previous minor completing
- Tasks within a minor depend on their explicit dependency list
- Cross-minor dependencies are not supported (use minor version ordering)

## Example Path

```
.claude/cat/v1/v1/parse-switch-statements/STATE.md
```

Components:
- Major version: 1
- Minor version: 0
- Task name: parse-switch-statements
