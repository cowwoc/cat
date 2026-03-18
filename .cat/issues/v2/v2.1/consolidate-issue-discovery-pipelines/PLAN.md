# Plan: consolidate-issue-discovery-pipelines

## Current State

Two independent `IssueDiscovery` pipelines exist for different purposes:
- `GetNextIssueOutput.getNextIssueBox()` — runs `IssueDiscovery(Scope.ALL)` to find next issue across all versions
  (used for banner display)
- `GetIssueCompleteOutput.discoverAndRender()` — runs `IssueDiscovery(Scope.MINOR)` to find next issue in the current
  minor version (used for issue-complete box)

The two serve different scopes intentionally (ALL vs MINOR), but the discovery pattern is duplicated.

## Target State

Evaluate whether the two pipelines can share common infrastructure without coupling their different scope behaviors.
If shared infrastructure is possible, extract it; otherwise document the intentional separation.

## Satisfies

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Possible behavior change if discovery logic diverges during refactor
- **Mitigation:** Comprehensive tests for both discovery paths before and after refactor

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java`

## Pre-conditions

- [ ] All dependent issues are closed
- [ ] extract-read-goal-from-plan-utility is closed (shared utility extracted first)

## Sub-Agent Waves

### Wave 1

- Analyze both discovery pipelines: determine if common behavior can be extracted without losing scope flexibility
  - Files: GetNextIssueOutput.java, GetIssueCompleteOutput.java
- If consolidation is feasible: extract common discovery-and-render logic, keeping Scope as a parameter
- If not feasible: add clear documentation explaining the intentional separation (Scope.ALL vs Scope.MINOR)
- Run `mvn -f client/pom.xml verify` to confirm no regressions

## Post-conditions

- [ ] Either consolidated discovery pipeline OR documented intentional separation
- [ ] No duplicated discovery-and-render patterns remain without justification
- [ ] All existing tests pass
