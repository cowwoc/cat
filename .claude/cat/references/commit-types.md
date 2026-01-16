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
| `bugfix` | Bug fix, error correction, **retrospective action items** | `bugfix: correct email validation` |
| `test` | Test-only changes | `test: add failing test for hashing` |
| `refactor` | Code cleanup, no behavior change | `refactor: extract validation helper` |
| `performance` | Performance improvement | `performance: add database index` |
| `docs` | User-facing docs (README, API docs) | `docs: add API documentation` |
| `style` | Formatting, linting fixes | `style: format auth module` |
| `config` | Config, tooling, deps, Claude-facing docs | `config: update CLAUDE.md rules` |
| `planning` | Planning system updates (ROADMAP, STATE) | `planning: add task 5 summary` |

**Claude-facing vs User-facing docs:**
- `docs:` = README, API docs, user guides - things humans read
- `config:` = CLAUDE.md, hooks, skills, style rules - things Claude reads

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

A single task typically produces **one or two commits**, but may have more:
1. Implementation commit(s) (required) - all feature/bugfix/test/refactor/docs work
2. Config commit (optional) - only if task includes **general** config changes

**Multi-Commit Tasks**: A task may be implemented across multiple commits when the work spans
multiple sessions, requires incremental progress, or addresses distinct aspects of the same task.
All commits for a task MUST include the same `Task ID` footer for traceability.

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

**Bugfixes with tests**: Tests written as part of a bugfix (TDD or otherwise) belong in the SAME commit
as the fix. The tests verify the fix works - they are part of the implementation, not separate artifacts.

```
# ❌ WRONG - separate commits for test and fix
abc1234 test: add test for comment handling
def5678 bugfix: fix comment parsing

# ✅ CORRECT - single commit with test and fix
abc1234 bugfix: fix comment parsing (includes tests)
```

## Task Resolution and Commit Footers

### Standard Task Completion

Implementation commits include a `Task ID` footer for traceability:

```
bugfix: fix multi-parameter lambda parsing

[description]

Task ID: v0.5-fix-multi-param-lambda
```

### Duplicate Task Resolution

When a task is discovered to be a duplicate of another task:

1. **No implementation commit** - the work was already done
2. **STATE.md-only commit** - marks the duplicate task complete
3. **No Task ID footer** - because there's no implementation

```
# Commit for closing a duplicate task
config: close duplicate task fix-cast-lambda-in-method-args

Duplicate of fix-multi-param-lambda (resolved in commit abc1234).
Both tasks addressed "Expected RIGHT_PARENTHESIS but found COMMA" errors.

# NOTE: No "Task ID:" footer - this is not an implementation commit
```

### Obsolete Task Resolution

When a task is no longer needed:

```
config: close obsolete task add-legacy-support

Requirements changed - legacy support is no longer in scope.

# NOTE: No "Task ID:" footer - this is not an implementation commit
```

### Finding Commits by Task

| Resolution | How to Find Commits |
|------------|---------------------|
| `implemented` | `git log --grep="Task ID: v{x}.{y}-{task-name}"` (may return multiple commits) |
| `duplicate` | Check STATE.md for `Duplicate Of`, search for that task |
| `obsolete` | No implementation commit exists |

**Note**: The grep command may return multiple commits if the task was implemented across multiple
commits. Each commit for the same task shares the identical `Task ID` footer.

See [task-resolution.md](task-resolution.md) for detailed resolution handling.
