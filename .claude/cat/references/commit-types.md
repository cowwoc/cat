# Commit Types

## Core Principle

**Commit outcomes, not process.**

The git log should read like a changelog of what shipped, not a diary of planning activity.

| Event | Commit? | Why |
|-------|---------|-----|
| PROJECT.md + ROADMAP.md created | YES | Project initialization |
| PLAN.md created | NO | Intermediate - commit with first task |
| RESEARCH.md created | NO | Intermediate artifact |
| **Task completed** | YES | Implementation + planning metadata |
| Handoff created | YES | WIP state preserved |

**Key principle:** Commit working code and shipped outcomes, not planning process.

## Standard Types (MANDATORY)

Use ONLY these types when committing in a CAT-managed project:

| Type | When to Use | Example |
|------|-------------|---------|
| `feature` | New functionality, endpoint, component | `feature: add user registration` |
| `bugfix` | Bug fix, error correction | `bugfix: correct email validation` |
| `test` | Test-only changes | `test: add failing test for hashing` |
| `refactor` | Code cleanup, no behavior change | `refactor: extract validation helper` |
| `performance` | Performance improvement | `performance: add database index` |
| `docs` | User-facing docs (README, API docs) | `docs: add API documentation` |
| `style` | Formatting, linting fixes | `style: format auth module` |
| `config` | Config, tooling, deps, Claude-facing docs | `config: add bcrypt dependency` |
| `planning` | Planning system updates (ROADMAP, STATE) | `planning: add task 5 summary` |

## Format

```
{type}: {description}
```

## Invalid Types

**NOT VALID:** `feat`, `fix`, `chore`, `build`, `ci`, `perf`

These abbreviated forms are NOT in the standard types. Use the full names above.

## Squash Categories

Before merge, commits are grouped into TWO categories:

**Implementation** (ONE squashed commit):
- `feature:`, `bugfix:`, `test:`, `refactor:`, `docs:`

**Infrastructure** (ONE squashed commit, optional):
- `config:` - configuration/tooling changes

A single task produces **one or two commits** total:
1. Implementation commit (required) - all feature/bugfix/test/refactor/docs work
2. Config commit (optional) - only if task includes **general** config changes

**Task STATE.md vs General Config:**
- **Task's STATE.md changes** → **SAME commit** as implementation (always)
- **General config** (adding dependencies, updating tooling) → Can be **separate commit**

Any changes to a task's STATE.md (marking complete, updating status, adding notes) are part of
implementing that task and belong in the same commit as the code changes.

```
# ✅ CORRECT - Implementation + STATE.md update (ONE commit)
abc1234 feature: add nested annotation type support
# (includes STATE.md changes for this task)

# ✅ CORRECT - Task with general config changes (TWO commits)
abc1234 feature: add nested annotation type support
def5678 config: add new dependency for annotation support

# ❌ WRONG - STATE.md changes in separate commit
abc1234 feature: add nested annotation type support
def5678 config: mark add-nested-annotation-type-support complete
# (STATE.md changes belong with the feature commit)
```

Use `test:` type ONLY for standalone test changes (no production code).
