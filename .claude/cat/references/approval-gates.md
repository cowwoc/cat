# Approval Gates

## Operating Modes

### Interactive Mode (Default)
Stakeholder review and user approval required before merge to main.

### Yolo Mode
All approval gates skipped. Tasks auto-proceed and auto-merge.

Enable via `cat-config.json`:
```json
{
  "yoloMode": true
}
```

## Approval Flow (Interactive Mode)

```
Task work complete
        |
        v
  STAKEHOLDER REVIEW GATE
  (5 parallel reviews)
        |
        v
  Aggregate concerns
        |
    [REJECTED?]----> Fix concerns
        |                  |
        v                  v
    [APPROVED]       Loop back
        |
        v
Squash commits by type
        |
        v
  USER APPROVAL GATE
  Present to user:
  - Overview of changes
  - Stakeholder review summary
  - Branch name for review
  - Files changed summary
        |
        v
User decision:
  - Request changes -> iterate
  - Approve -> merge to main
```

## Stakeholder Review Gate

Before user approval, implementation is reviewed by 5 stakeholder perspectives:

| Stakeholder | Focus Area |
|-------------|------------|
| architect | System design, module boundaries, APIs |
| security | Vulnerabilities, input validation |
| quality | Code quality, complexity, duplication |
| tester | Test coverage, edge cases |
| performance | Efficiency, resource usage |

**Aggregation Rules:**

| Condition | Result |
|-----------|--------|
| Any CRITICAL concern | REJECTED - must fix |
| Any stakeholder REJECTED | REJECTED - must fix |
| 3+ HIGH concerns total | REJECTED - must fix |
| Only MEDIUM concerns | CONCERNS - proceed with notes |
| No concerns | APPROVED - proceed |

**Configuration:**

Stakeholder review frequency is controlled via `stakeholderReview` in cat-config.json:

| Value | Behavior |
|-------|----------|
| `always` | Run on every task |
| `high-risk-only` | Only tasks with risk indicators (default) |
| `never` | Skip entirely |

See [stakeholders/index.md](stakeholders/index.md) for detailed stakeholder definitions.

## Information Presented

At approval gate, user sees:

1. **Summary**: What was accomplished
2. **Branch**: `{major}.{minor}-{task-name}` for review
3. **Files Changed**: Count and list of modified files
4. **Commits**: Squashed commits by type
5. **Test Results**: Pass/fail status

## User Options

| Action | Result |
|--------|--------|
| Approve | Merge to main, cleanup worktrees |
| Request changes | Return to task execution |
| Reject | Mark task blocked, escalate |

## Commit Squashing

Before approval, commits are squashed by type:
- One commit per: `feature`, `bugfix`, `refactor`, `docs`, `test`, `config`

Example result:
```
feature: add token tracking to subagent execution
bugfix: resolve merge conflict in parser module
refactor: simplify worktree cleanup logic
```
