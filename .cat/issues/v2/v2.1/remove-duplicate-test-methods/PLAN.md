# Plan: remove-duplicate-test-methods

## Type

refactor

## Current State
In `SetPendingAgentResultTest.java`, 4 test methods were added as duplicates of existing tests during the 2.1-optimize-collect-results-gate implementation. The duplicate methods test the same scenarios with different names.

## Target State
Remove the duplicate test methods, keeping only the correctly-named versions.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — removing duplicates only
- **Mitigation:** Remaining tests still cover all scenarios

## Files to Modify
- client/src/test/java/io/github/cowwoc/cat/hooks/test/SetPendingAgentResultTest.java - remove duplicate test methods

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Identify and remove duplicate test methods that test the same scenarios as other methods in the class
  - Files: client/src/test/java/io/github/cowwoc/cat/hooks/test/SetPendingAgentResultTest.java
- Run all tests to confirm no regressions

## Post-conditions
- [ ] No duplicate test methods exist in SetPendingAgentResultTest.java
- [ ] All remaining tests pass
- [ ] E2E: Build succeeds with `mvn -f client/pom.xml test`
