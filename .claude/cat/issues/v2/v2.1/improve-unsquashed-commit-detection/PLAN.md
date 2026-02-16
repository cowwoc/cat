# Plan: improve-unsquashed-commit-detection

## Problem

WarnUnsquashedApproval uses a hard-coded `commitCount > 2` threshold to detect unsquashed commits.
This breaks when `/cat:git-squash` legitimately produces 3+ commits via squash-by-topic. The tests
are also environment-dependent because they run the hook against real git state.

## Satisfies

None (infrastructure improvement)

## Root Cause

The commit-count heuristic cannot distinguish "forgot to squash" from "intentionally multi-commit
after squash-by-topic." However, the orchestrator (work-with-issue skill Step 6) already enforces
squashing before the approval gate, making the worktree check redundant.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Tests must not depend on real git state
- **Mitigation:** Mock git state in tests

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java` — Remove
  worktree commit-count check; orchestrator handles squash verification
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java` — Fix
  environment-dependent tests to mock git state

## Acceptance Criteria

- [ ] WarnUnsquashedApproval returns Result.allow() for worktree contexts (orchestrator handles squash)
- [ ] Approval gate only checks main workspace commits (config/planning + implementation pattern)
- [ ] Tests pass regardless of the worktree's actual commit count
- [ ] Uses "issue" terminology consistently (not "task")

## Execution Steps

1. **Simplify WarnUnsquashedApproval worktree check:** When `cat-base` exists (worktree context),
   return `Result.allow()` immediately. Remove `checkWorktreeCommits` method entirely.
   Orchestrator (work-with-issue Step 6) enforces squashing before approval gate.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java`

2. **Fix environment-dependent tests:** Mock git state or use test-specific git repos instead
   of relying on real worktree state
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java`

3. **Run tests and verify all pass**
