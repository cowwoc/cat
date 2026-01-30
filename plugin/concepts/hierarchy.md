# CAT Hierarchy: MAJOR > MINOR > ISSUE

> **See also:** [version-paths.md](version-paths.md) for path construction functions and patterns.

## Structure

```
.claude/cat/
├── PROJECT.md
├── ROADMAP.md
├── cat-config.json
└── v<n>/
    ├── STATE.md
    ├── PLAN.md
    ├── CHANGELOG.md          # Aggregates completed issues
    └── v<n>.<m>/
        ├── STATE.md
        ├── PLAN.md
        ├── CHANGELOG.md      # Aggregates completed issues
        └── <issue-name>/
            ├── STATE.md
            └── PLAN.md
```

> **NOTE**: Issue-level CHANGELOG.md is not created. Issue changelog content is embedded
> in commit messages (see commit message format in work command).

## Version Semantics

| Level | Purpose | Numbering |
|-------|---------|-----------|
| MAJOR | New features or capabilities | 0-based (users typically start at 1) |
| MINOR | Bugfixes and smaller additions | 0-based |
| ISSUE | Atomic unit of work | Named (lowercase-hyphens) |

## Versioning Schemes

CAT supports multiple versioning schemes (MAJOR, MAJOR.MINOR, MAJOR.MINOR.PATCH).

See [version-scheme.md](version-scheme.md) for:
- Supported schemes and their structure
- Scheme detection logic
- Version boundary detection rules

## Requirements Flow

Requirements are defined at the **minor version level** and traced to issues:

```
Minor PLAN.md                    Issue PLAN.md
┌─────────────────────┐         ┌─────────────────────┐
│ ## Requirements     │         │ ## Satisfies        │
│ | REQ-001 | ...     │ ◄────── │ - REQ-001           │
│ | REQ-002 | ...     │         │ - REQ-003           │
│ | REQ-003 | ...     │         └─────────────────────┘
└─────────────────────┘
```

**Key rules:**
- Requirements live in minor version PLAN.md (not issue level)
- Issues reference requirements via `## Satisfies` field (zero or more)
- A minor version cannot complete until all must-have requirements are satisfied (implicit check)
- The Requirements stakeholder verifies issues satisfy their claimed requirements
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
1. Its dependency minor version is completed (all issues in that minor are completed)
2. OR it has no dependency (first version)

**Issue-level dependencies:**
- Issues within a minor depend on their explicit dependency list in STATE.md
- Cross-minor issue dependencies are not supported (use minor version ordering instead)

## Issue Execution Order

**Non-Linear Progression:** Issues execute based on dependency resolution, not sequence order.

When dependencies are met, issues become eligible for execution regardless of their position in the
issue list. Multiple independent issues can execute concurrently.

| Issue State | Execution Eligibility |
|------------|----------------------|
| Dependencies empty | Immediately eligible |
| All dependencies completed | Eligible |
| Any dependency pending | Blocked until resolved |

This enables parallel execution of independent issues while maintaining correct ordering for
dependent issues.

## Example Path

```
.claude/cat/issues/v1/v1.0/parse-switch-statements/STATE.md
```

Components (using MAJOR.MINOR scheme):
- Major version: 1
- Minor version: 0
- Issue name: parse-switch-statements

> **Note:** Path structure adapts to versioning scheme. See [version-scheme.md](version-scheme.md).
