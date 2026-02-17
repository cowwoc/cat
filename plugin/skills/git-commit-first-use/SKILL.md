---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Commit Message Skill

**Purpose**: Provide guidance for writing clear, descriptive commit messages that explain WHAT the code does and WHY.

## PROJECT.md Commit Format (If Configured)

**Before writing a commit message, check if PROJECT.md specifies commit format rules.**

```bash
# Check for configured commit format in PROJECT.md
COMMIT_FORMAT=$(grep -A10 "### Commit Format" .claude/cat/PROJECT.md 2>/dev/null)

if [[ -n "$COMMIT_FORMAT" ]]; then
  echo "Using commit format from PROJECT.md:"
  echo "$COMMIT_FORMAT"
  echo ""
  echo "Apply any MUST rules from PROJECT.md to the commit message."
fi
```

**When PROJECT.md has commit format rules:**
- MUST rules are mandatory - follow them exactly
- SHOULD rules are recommended - follow unless you have a good reason not to
- MAY rules are optional - use your judgment

**If no PROJECT.md configuration exists:** Use the default rules below.

## Core Principles

### 1. Describe WHAT the Code Does, Not the Process

```
# WRONG - Describes the process (NEVER use these)
❌ "Squashed commits"
❌ "Combined multiple commits"
❌ "Merged feature branch"

# CORRECT - Describes what the code does
Add user authentication with JWT tokens
Fix memory leak in connection pool
Refactor parser to use visitor pattern
```

### 2. Use Imperative Mood (Command Form)

```
# WRONG
Added authentication
Authentication was added

# CORRECT
Add user authentication
Fix authentication timeout bug
```

### 3. Subject Line Formula

```
<Verb> <what> [<where/context>]

Examples:
Add   rate limiting      to API endpoints
Fix   memory leak        in connection pool
Refactor  parser         to use visitor pattern
```

**Rules**:
- Max 72 characters (50 ideal)
- Imperative mood (Add, Fix, Update, Remove, Refactor)
- No period at end
- Capitalize first word

### 4. Describe Changes Conceptually

The commit diff already shows which files were changed. Describe WHAT changed conceptually, not WHERE.

```
# WRONG - Subject line lists files
Update Parser.java and Lexer.java for comment handling

# WRONG - Body has "Files updated" section
config: update display standards

Files updated:
- commands/status.md
- skills/collect-results/SKILL.md
- concepts/display-standards.md

# CORRECT - Describes what changed
Fix comment handling in member declarations

# CORRECT - Body describes changes, not files
config: update display standards

Standardize fork display format and checkpoint messaging.
```

## Structure for Complex Changes

```
Subject line: Brief summary (50-72 chars, imperative mood)

Body paragraph: Explain the overall change and why it's needed.

Changes:
- First major change
- Second major change
- Third major change
```

## Finding Commits for CAT Issues

**Commits are tracked via STATE.md file history, not commit footers.**

When an issue is completed, STATE.md is updated in the same commit as the implementation
(per M076). This creates a permanent link between the commit and the issue.

```
feature: add yield statement parsing support

Add YIELD_STATEMENT node type and parseYieldStatement() method
for JDK 14+ switch expressions.

- Added YIELD_STATEMENT to NodeType enum
- Created parseYieldStatement() following parseThrowStatement() pattern
- Updated ContextDetector exhaustive switch
```

**Finding implementation commits:**

```bash
# Find all commits for an issue
git log --oneline -- .claude/cat/issues/v3/v3.0/add-yield-statement-support/

# Find the completion commit
git log --oneline -1 -- .claude/cat/issues/v3/v3.0/add-yield-statement-support/STATE.md
```

**Why no footer needed:** Git's file history tracking survives rebases automatically and
requires no manual maintenance

## For Squashed Commits

**Review commits being squashed**:
```bash
git log --oneline base..HEAD
```

**Synthesize into unified message:**

```
# WRONG - Concatenated messages
feature(auth): add login form
feature(auth): add validation
feature(auth): add error handling
bugfix(auth): fix typo

# CORRECT - Unified message describing what the code does
feature: add login form with validation and error handling

- Email/password form with client-side validation
- Server-side validation with descriptive error messages
- Loading states and error display
```

### Enhanced Format for Task Commits

The final squashed commit message MUST include changelog content. The commit diff already shows Files Modified, Files
Created, and Test Coverage - omit these from the message.

```
{type}: {concise description}

## Problem Solved
[WHY this task was needed - what wasn't working or was missing]
- {Problem 1}
- {Problem 2 if applicable}

## Solution Implemented
[HOW the problem was solved - the approach taken]
- {Key implementation detail 1}
- {Key implementation detail 2}

## Decisions Made (optional)
- {Decision}: {rationale}

## Known Limitations (optional)
- {Limitation}: {why accepted or deferred}

## Deviations from Plan (optional)
- {Deviation}: {reason and impact}
```

**Example:**

```
feature: add lambda expression parsing

## Problem Solved
- Parser failed on multi-parameter lambdas: `(a, b) -> a + b`
- 318 parsing errors in Spring Framework codebase

## Solution Implemented
- Added lookahead in parsePostfix() to detect lambda arrow
- Reused existing parameter parsing for lambda parameters
- Handles both inferred and explicit type parameters

## Decisions Made
- Reuse parameter parsing: Maintains consistency with method parameters
```

## Commit Types (MANDATORY)

**CRITICAL:** When working in a CAT-managed project, use ONLY these types:

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
| `planning` | Planning system updates | `planning: add issue 5 summary` |

**NOT VALID:** `feat`, `fix`, `chore`, `build`, `ci`, `perf` - use full names instead

**Format:** `{type}: {description}`

### Commit Type Separation (MANDATORY)

**Keep one commit type per commit.** Each commit should have ONE type.

```
# WRONG - Mixed types in one commit
bugfix: fix parser bug and update documentation

Changes:
- Fix comment parsing in member declarations
- Update requirements-api.md with correct method names

# CORRECT - Separate commits by type
bugfix: fix parser bug for comments in member declarations

---

config: correct method names in requirements-api.md

Updated isReferenceEqualTo documentation.
```

**Why**: Git history becomes searchable by type. `git log --grep="^config:"` finds all config
changes. Mixed commits break this traceability.

**Rule**: If changes span multiple types, create multiple commits.

## Good Verbs for Description

| Verb | Use For |
|------|---------|
| **add** | New feature, file, function |
| **fix** | Bug fix or correction |
| **update** | Modify existing feature (non-breaking) |
| **remove** | Delete feature, file, or code |
| **refactor** | Restructure without changing behavior |
| **improve** | Enhance existing feature |

## Anti-Patterns to Avoid

```
# Meaningless
WIP
Fix stuff
Updates
.

# Overly Generic
Update code
Fix bugs
Refactor

# Just the Process (NEVER use these)
❌ "Squashed commits"
❌ "Merged feature branch"
❌ "Combined work"

# Too Technical
Change variable name from x to userCount
Move function from line 45 to line 67

# Listing Modified Files (the diff already shows this)
Update Parser.java, Lexer.java, and TokenType.java

Files updated:
- commands/status.md
- skills/collect-results/SKILL.md
```

## Pre-Commit Efficiency

When committing changes you just made in the current session, skip redundant checks (`git status`, `git diff`, `git
log`). You already know:
- What files you edited (from Edit/Write tools)
- What the changes are (you wrote them)
- The commit message style (from earlier commits)

Proceed directly to `git add <files> && git commit`.

**Use the three-command pattern only when:**
- Resuming work from a previous session
- Changes span files you didn't directly edit
- Uncertain about repository state

## Checklist Before Committing

- [ ] **In correct worktree**: `pwd` shows issue worktree, NOT `/workspace`
- [ ] Subject line is imperative mood ("Add", not "Added")
- [ ] Subject line is specific (not "Update files")
- [ ] Subject line is under 72 characters
- [ ] Body explains WHAT and WHY, not HOW
- [ ] No file names listed (the diff already shows which files changed)
- [ ] For squashed commits: synthesized meaningful summary
- [ ] Message would make sense in git history 6 months from now

## Worktree Verification

**Before committing in a CAT issue, verify you're in the issue worktree:**

```bash
# Quick verification
pwd  # Should show /workspace/.claude/cat/worktrees/<issue-name>, NOT /workspace
git branch --show-current  # Should show issue branch, NOT main
```

**If in wrong worktree:** Stop and navigate to the correct one before committing.

## Quick Test

Ask yourself: "If I read this in git log in 6 months, would I understand what this commit does and why?"

If no, revise the message.
