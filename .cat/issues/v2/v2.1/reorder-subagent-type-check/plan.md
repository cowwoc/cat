# Plan: reorder-subagent-type-check

## Type

refactor

## Current State
In `SetPendingAgentResult.java`, the `subagent_type` check runs after `WorktreeContext.forSession()`, meaning unnecessary worktree context processing occurs for non-work-execute agents.

## Target State
Move the `isWorkExecute` boolean guard to run before `WorktreeContext.forSession()` so that non-work-execute agents return early without performing unnecessary processing.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — only reorders existing logic
- **Mitigation:** Existing tests verify behavior is unchanged

## Files to Modify
- client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java - reorder the subagent_type check to before WorktreeContext.forSession()

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Move the `isWorkExecute` check block (lines ~87-103) to execute before the `WorktreeContext.forSession()` call
  - Files: client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java
- Run all tests to confirm no regressions
  - Files: client/src/test/java/io/github/cowwoc/cat/hooks/test/SetPendingAgentResultTest.java

## Post-conditions
- [ ] The subagent_type check executes before WorktreeContext.forSession() in the method flow
- [ ] All existing tests pass without modification
- [ ] E2E: Build succeeds with `mvn -f client/pom.xml test`
