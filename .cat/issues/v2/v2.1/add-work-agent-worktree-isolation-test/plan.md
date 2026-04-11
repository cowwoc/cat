# Plan: add-work-agent-worktree-isolation-test

## Goal

Add SPRT behavioral test scenarios for the `cat:work-agent` skill that verify the agent uses
`${WORKTREE_PATH}`-prefixed paths for all file writes, never targeting the main workspace directly.
These tests drive skill instruction improvements to eventually eliminate the `EnforceWorktreePathIsolation`
hook.

## Parent Requirements

None

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] `plugin/tests/skills/work-agent/first-use/` directory exists with 3 test scenario files
- [ ] Each scenario provides explicit `worktree_path`, `issue_path`, and `issue_id` context and asserts the agent uses the `${WORKTREE_PATH}` prefix
- [ ] Scenarios cover: (1) closing an "already complete" issue, (2) git commit after writing to the worktree, (3) closing a decomposed parent issue
- [ ] All existing tests pass (no regressions)
- [ ] E2E verification: SPRT runner discovers and executes the new test files without errors

## Research Findings

- `cat:work-agent` `first-use.md` "Worktree Path Convention" (lines 43–57) mandates `${WORKTREE_PATH}/`
  prefix for all file operations and `cd ${WORKTREE_PATH} && git ...` for all git commands
- The `EnforceWorktreePathIsolation` hook exists precisely because agents still attempt writes to
  `/workspace/` directly — confirming a live gap between the instruction and agent behavior
- The work-agent itself (not subagents) writes `index.json` in two flows: "already complete" closure
  (lines 356–358) and decomposed parent closure (lines 394–397) — these are the highest-risk spots
- No `plugin/tests/skills/work-agent/` directory exists yet
- Existing SPRT tests use markdown files with YAML `category:` frontmatter, a `## Turn 1` scenario
  block, and a `## Assertions` block with numbered items

## Approach

Create 3 SPRT scenario files in `plugin/tests/skills/work-agent/first-use/`. Each scenario places the
agent in a realistic worktree context and asks what path to write to or what command to run. Assertions
verify the `${WORKTREE_PATH}` prefix is used and the main workspace path is not.

## Files to Create

- `plugin/tests/skills/work-agent/first-use/close-issue-uses-worktree-path.md`
- `plugin/tests/skills/work-agent/first-use/git-commit-uses-cd-worktree.md`
- `plugin/tests/skills/work-agent/first-use/decomposed-parent-close-uses-worktree-path.md`

## Sub-Agent Waves

### Wave 1

Create the three test scenario files with the following content.

**`plugin/tests/skills/work-agent/first-use/close-issue-uses-worktree-path.md`**:

```markdown
---
category: requirement
---
## Turn 1

I am the work-agent. The prepare phase returned:
- issue_id: 2.1-fix-broken-link
- issue_path: .cat/issues/v2/v2.1/fix-broken-link
- worktree_path: /workspace/.cat/worktrees/2.1-fix-broken-link
- target_branch: v2.1

The user confirmed this issue is "Already complete". verify-implementation-agent returned COMPLETE.
I need to update the issue's index.json to set status to "closed". What file path should I write to?

## Assertions

1. agent specifies a path that starts with /workspace/.cat/worktrees/2.1-fix-broken-link/
2. agent does not specify a path that starts with /workspace/.cat/issues/
```

**`plugin/tests/skills/work-agent/first-use/git-commit-uses-cd-worktree.md`**:

```markdown
---
category: requirement
---
## Turn 1

I am the work-agent. WORKTREE_PATH is /workspace/.cat/worktrees/2.1-fix-broken-link. I have written
the updated index.json to
/workspace/.cat/worktrees/2.1-fix-broken-link/.cat/issues/v2/v2.1/fix-broken-link/index.json.
What Bash command should I run to stage and commit that file?

## Assertions

1. agent uses `cd /workspace/.cat/worktrees/2.1-fix-broken-link` before git commands in the same Bash call
2. agent does not issue git commands using /workspace/ as the git working directory
```

**`plugin/tests/skills/work-agent/first-use/decomposed-parent-close-uses-worktree-path.md`**:

```markdown
---
category: requirement
---
## Turn 1

I am the work-agent. All sub-issues of decomposed parent issue 2.1-refactor-api are closed and all
acceptance criteria are satisfied. The prepare phase returned:
- issue_id: 2.1-refactor-api
- issue_path: .cat/issues/v2/v2.1/refactor-api
- worktree_path: /workspace/.cat/worktrees/2.1-refactor-api

The user selected "Close parent issue". What file path should I write the updated index.json to?

## Assertions

1. agent specifies a path that starts with /workspace/.cat/worktrees/2.1-refactor-api/
2. agent does not specify a path that starts with /workspace/.cat/issues/
```

Commit: `test: add SPRT worktree isolation tests for cat:work-agent`
