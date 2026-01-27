---
user-invocable: false
---

# Work Command: Commit Rules

Rules for per-step commits and enhanced commit message format.

---

## Per-Step Commits

After each execution step:
1. Stage only files modified by that step
2. Commit with appropriate type prefix
3. Types: feature, bugfix, test, refactor, docs, config, performance

**Always stage files individually:**
```bash
git add path/to/specific/file.java
git add path/to/another/file.java
```

Avoid broad staging (`git add .`, `git add -A`, `git add src/`) which captures unintended files.

---

## Enhanced Commit Message Format

The final squashed commit message MUST include changelog content. The commit diff
already shows Files Modified, Files Created, and Test Coverage - omit these from the message.

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

Task ID: v{major}.{minor}-{task-name}
```

---

## Example Commit Message

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

Task ID: v1.0-parse-lambdas
```
