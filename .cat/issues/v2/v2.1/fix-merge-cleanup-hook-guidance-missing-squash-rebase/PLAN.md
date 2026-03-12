# Plan: fix-merge-cleanup-hook-guidance-missing-squash-rebase

## Goal
Fix the BlockUnauthorizedMergeCleanup hook's error message to list all mandatory recovery steps
in the correct order: squash (Step 7), rebase (Step 8), approval gate (Step 9), then merge.

## Background
The hook that blocks direct merge binary invocations provides recovery guidance. The current
message lists "Complete Step 8 (Approval Gate) - present AskUserQuestion to the user" as step 1,
omitting mandatory Steps 7 (squash via cat:git-squash-agent) and 8 (rebase via cat:git-rebase-agent).
When work-merge-agent fails to load, agents follow this incomplete guidance and skip squash/rebase,
going directly to the approval gate. This is a priming failure from misleading hook documentation.

## Current State
BlockUnauthorizedMergeCleanup.java lists only the approval gate as the first recovery step.

## Target State
BlockUnauthorizedMergeCleanup.java lists four recovery steps in order:
1. Step 7: Squash Commits by Topic (cat:git-squash-agent)
2. Step 8: Rebase onto Target Branch (cat:git-rebase-agent)
3. Step 9: Approval Gate (AskUserQuestion)
4. Invoke merge via Task/Skill tool

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — error message text change only
- **Mitigation:** Existing tests verify message content; tests must be updated to match new text

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`
  — update Result.block() error message to list squash, rebase, and approval gate steps
- `client/src/test/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanupTest.java`
  — update test assertions to match new error message text

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update the error message in `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`:
  - Find the `Result.block()` call that generates the recovery guidance message
  - Replace the current guidance that starts with the approval gate step
  - New guidance must list:
    1. "Complete Step 7 (Squash Commits by Topic): invoke cat:git-squash-agent"
    2. "Complete Step 8 (Rebase onto Target Branch): invoke cat:git-rebase-agent"
    3. "Complete Step 9 (Approval Gate): present AskUserQuestion to the user"
    4. "Then invoke the merge via Task or Skill tool"
- Update `client/src/test/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanupTest.java`:
  - Update any test assertions that check the exact error message text
  - Add assertions that verify the message contains "squash" and "rebase" step references
- Run tests: `mvn -f client/pom.xml test`

## Post-conditions
- [ ] BlockUnauthorizedMergeCleanup.java error message lists Step 7 (Squash Commits by Topic) as the first recovery step
- [ ] Error message lists Step 8 (Rebase onto Target Branch) as the second recovery step
- [ ] Error message lists Step 9 (Approval Gate - AskUserQuestion) as the third recovery step
- [ ] Error message lists the Task/Skill tool merge invocation as the fourth step
- [ ] All existing tests in BlockUnauthorizedMergeCleanupTest pass
- [ ] Tests verify the updated message contains squash and rebase step references
