# Plan: use-isblank-for-external-json

## Type

refactor

## Current State
In `SetPendingAgentResult.java`, `isEmpty()` is used to check the `subagent_type` string extracted from external JSON input. External strings may contain whitespace-only values that `isEmpty()` would not catch.

## Target State
Replace `isEmpty()` with `isBlank()` when checking `subagent_type` from external JSON input for more robust validation.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — isBlank() is a superset of isEmpty()
- **Mitigation:** Existing tests verify behavior; edge case test for whitespace-only values

## Files to Modify
- client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java - replace isEmpty() with isBlank() for subagent_type check

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Replace isEmpty() with isBlank() for the subagent_type string check
  - Files: client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java
- Add a test case for whitespace-only subagent_type value
  - Files: client/src/test/java/io/github/cowwoc/cat/hooks/test/SetPendingAgentResultTest.java
- Run all tests to confirm no regressions

## Post-conditions
- [ ] isBlank() is used instead of isEmpty() for the subagent_type check
- [ ] A test exists for whitespace-only subagent_type value
- [ ] All existing tests pass
- [ ] E2E: Build succeeds with `mvn -f client/pom.xml test`
