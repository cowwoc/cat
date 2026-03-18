# Plan: validate-worktree-branch-on-commit

## Goal
Prevent subagents from accidentally committing to the wrong branch by adding a PreToolUse:Bash hook
that detects `git commit` commands and validates the current branch matches the expected issue branch
from the worktree context.

## Satisfies
None — prevention for mistake M452 (subagent committed directly to v2.1 instead of issue worktree).

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Hook must not block legitimate main-agent commits
- **Mitigation:** Only validate when inside a known CAT worktree directory; skip validation in main workspace

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWrongBranchCommit.java` — new hook class
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockWrongBranchCommitTest.java` — new test class
- `plugin/hooks/hooks.json` — register the hook

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Implement and register the hook
- Create `BlockWrongBranchCommit.java` that:
  1. Triggers on `PreToolUse:Bash` when command matches `git commit`
  2. Reads current branch via `git branch --show-current`
  3. Checks if CWD is inside a CAT worktree (looks for `cat-branch-point` file in `.git/`)
  4. If inside a worktree, reads expected branch from `cat-branch-point`
  5. If current branch != expected branch, blocks commit with error message including recovery steps
- Create unit tests covering: inside worktree on correct branch (allow), inside worktree on wrong branch
  (block with message), outside worktree (allow)
- Register hook in `plugin/hooks/hooks.json` for PreToolUse:Bash
- Run `mvn -f client/pom.xml test` to verify all tests pass

## Post-conditions
- [ ] `git commit` in a CAT worktree on wrong branch is blocked with a clear error message
- [ ] `git commit` in a CAT worktree on correct branch is allowed
- [ ] `git commit` outside a CAT worktree (main workspace) is allowed unchanged
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
