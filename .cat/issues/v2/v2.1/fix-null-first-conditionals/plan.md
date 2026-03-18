# Plan: fix-null-first-conditionals

## Type

refactor

## Current State
In `SetPendingAgentResult.java`, the `agentIdNode` extraction uses `isTextual()` before checking `isNull()`, which violates the null-first conditionals convention.

## Target State
Reorder the conditional to check `isNull()` (or null) first before `isTextual()`, following the project's coding convention for null-safety.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — only reorders conditional branches
- **Mitigation:** Existing tests verify behavior is unchanged

## Files to Modify
- client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java - reorder agentIdNode conditionals to check null first

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Reorder the agentIdNode extraction conditional to test isNull()/null before isTextual()
  - Files: client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java
- Run all tests to confirm no regressions
  - Files: client/src/test/java/io/github/cowwoc/cat/hooks/test/SetPendingAgentResultTest.java

## Post-conditions
- [ ] The null check precedes the isTextual() check in agentIdNode extraction
- [ ] All existing tests pass without modification
- [ ] E2E: Build succeeds with `mvn -f client/pom.xml test`
