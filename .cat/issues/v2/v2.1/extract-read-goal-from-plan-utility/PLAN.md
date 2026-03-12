# Plan: extract-read-goal-from-plan-utility

## Current State

`readGoalFromPlan()` logic is duplicated across three classes:
- `WorkPrepare.readGoalFromPlan()`
- `GetNextIssueOutput.readIssueGoal()`
- `GetIssueCompleteOutput.readGoalFromPlan()`

Each reads `## Goal` section from a PLAN.md file and returns the first paragraph (or "No goal found").

## Target State

A single shared utility method (e.g., `IssueGoalReader.readGoalFromPlan()`) in a utility class, used by all three
callers. Eliminates DRY violation and ensures consistent behavior.

## Satisfies

None

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None (internal refactor, no public API change beyond the caller classes)
- **Mitigation:** All 3 callers use the same logic; tests for the shared utility cover all edge cases

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueGoalReader.java` - create shared utility
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/WorkPrepare.java` - delegate to shared utility
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java` - delegate to shared utility
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` - delegate to shared utility

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Create `IssueGoalReader` utility class with `readGoalFromPlan(Path planPath)` static method
  - Extract logic from `GetIssueCompleteOutput.readGoalFromPlan()` (most recent, best-tested implementation)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueGoalReader.java`
- Replace `readGoalFromPlan()` in `WorkPrepare`, `GetNextIssueOutput` (readIssueGoal), and `GetIssueCompleteOutput`
  with `IssueGoalReader.readGoalFromPlan()`
  - Files: WorkPrepare.java, GetNextIssueOutput.java, GetIssueCompleteOutput.java
- Remove the now-duplicate private methods from each class
- Run `mvn -f client/pom.xml verify` to confirm no regressions

## Post-conditions

- [ ] Single `IssueGoalReader.readGoalFromPlan()` utility method used by all 3 callers
- [ ] No other copies of the PLAN.md goal-reading logic remain in the codebase
- [ ] All existing tests pass
