# Commit Types

## Core Principle

**Commit outcomes, not process.**

The git log should read like a changelog of what shipped, not a diary of planning activity.

| Event | Commit? | Why |
|-------|---------|-----|
| PROJECT.md + ROADMAP.md created | YES | Project initialization |
| PLAN.md created | NO | Intermediate - commit with first issue |
| RESEARCH.md created | NO | Intermediate artifact |
| **Issue completed** | YES | Implementation + planning metadata |
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
| `config` | Config, tooling, deps, **Claude-facing docs** | `config: update CLAUDE.md rules` |
| `planning` | Planning system updates (ROADMAP, STATE) | `planning: add issue 5 summary` |

**Commit Type by File Location (M089, M133):**

| File Location | Commit Type | Examples |
|---------------|-------------|----------|
| `.claude/cat/*.md` | `planning:` | PROJECT.md, ROADMAP.md |
| `.claude/cat/issues/v*/` | `planning:` | STATE.md, PLAN.md, CHANGELOG.md |
| `.claude/cat/retrospectives/` | `config:` | index.json, mistakes-*.json, retrospectives-*.json (Claude-facing) |
| `.claude/hooks/`, `.claude/settings.json` | `config:` | hooks, Claude Code settings |
| `CLAUDE.md`, skills, workflows | `config:` | Claude-facing behavior rules |
| `README.md`, `docs/`, API docs | `docs:` | User-facing documentation |

**Key test:** Is the file in `.claude/cat/`? → Use `planning:`. Is it Claude behavior config? → Use `config:`. Is it for end users? → Use `docs:`.

**CRITICAL: Commit type = WHAT changed, not WHERE (M134)**

File location determines the DEFAULT type, but the nature of the change takes precedence:

| Change Type | Commit Type | Example |
|-------------|-------------|---------|
| Bug fix in any file | `bugfix:` | Fix creating empty directories |
| New feature in any file | `feature:` | Add new command |
| Refactor in any file | `refactor:` | Restructure plugin directories |
| Config/maintenance | `config:` | Update settings, add dependencies |

**CAT Plugin Repo Structure (special case):**

The CAT plugin repo separates plugin source from planning:

| Location | Default Type | What It Is |
|----------|--------------|------------|
| `plugin/` | `config:` | Plugin source code that ships to users |
| `plugin/concepts/` | `config:` | Plugin's bundled reference docs |
| `plugin/skills/` | `config:` | Plugin skills |
| `.claude/cat/` (root) | `planning:` | CAT's own development planning |
| `.claude/cat/issues/v*/` (root) | `planning:` | CAT's version/issue structure |

**Key distinction:** Default type is based on location, but always override with the actual change type (bugfix, feature, refactor) when applicable. `config:` is for maintenance/settings changes, not all plugin file edits.

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

A single issue typically produces **one or two commits**, but may have more:
1. Implementation commit(s) (required) - all feature/bugfix/test/refactor/docs work
2. Config commit (optional) - only if issue includes **general** config changes

**Multi-Commit Issues**: An issue may be implemented across multiple commits when the work spans
multiple sessions, requires incremental progress, or addresses distinct aspects of the same issue.
All commits for an issue MUST include the same `Issue ID` footer for traceability.

**Issue STATE.md vs General Config:**
- **Issue's STATE.md changes** → **SAME commit** as implementation (always)
- **General config** (adding dependencies, updating tooling) → Can be **separate commit**

Any changes to an issue's STATE.md (marking complete, updating status, adding notes) are part of
implementing that issue and belong in the same commit as the code changes.

**Convention/Infrastructure Updates (M317):**

Convention updates discovered during issue work belong on the **base branch**, not the issue branch:

| Change Type | Where to Commit | Why |
|-------------|-----------------|-----|
| Issue implementation | Issue branch | Direct issue deliverable |
| Convention updates (java.md, style rules) | Base branch first | Enables multiple issues |
| Tooling config (checkstyle, pmd rules) | Base branch first | Project-wide infrastructure |
| Skill/workflow improvements | Base branch first | Benefits all future work |

**Workflow when conventions need updating:**
1. Stash or set aside issue work
2. Switch to base branch
3. Commit convention/infrastructure updates
4. Return to issue branch and rebase
5. Continue issue implementation

This keeps issue branches focused on deliverables and ensures infrastructure changes are
immediately available to all branches.

```
# ✅ CORRECT - Implementation + STATE.md update (ONE commit)
abc1234 feature: add nested annotation type support
# (includes STATE.md changes for this issue)

# ✅ CORRECT - Issue with general config changes (TWO commits)
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

## Finding Commits by Issue

Implementation commits are tracked via STATE.md file history, not commit footers.

### Finding Implementation Commits

```bash
# Find all commits that touched this issue
git log --oneline -- .claude/cat/issues/v2/v2.1/issue-name/

# Find the most recent implementation commit (usually STATE.md completion)
git log --oneline -1 -- .claude/cat/issues/v2/v2.1/issue-name/STATE.md

# See full implementation history with diffs
git log -p -- .claude/cat/issues/v2/v2.1/issue-name/STATE.md
```

**Why this works:**
- STATE.md is updated when the issue is completed
- STATE.md commits are part of the implementation commit (per M076)
- Git's file history tracking survives rebases automatically
- No manual commit hash maintenance required

### Resolution Types

| Resolution | How to Find Commits |
|------------|---------------------|
| `implemented` | `git log -- .claude/cat/issues/v*/v*.*/issue-name/STATE.md` |
| `duplicate` | Check STATE.md for `Duplicate Of`, search for that issue |
| `obsolete` | No implementation commit exists |

See [issue-resolution.md](issue-resolution.md) for detailed resolution handling.
