# Plan: work-prepare-discover-only

## Goal
Make `GetIssueCompleteOutput` discover the next available issue internally using `IssueDiscovery`,
eliminating the need for callers to invoke `work-prepare` (which creates worktrees and locks as side
effects) just to find what issue comes next for display purposes.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** `GetIssueCompleteOutput` currently requires callers to pass next-issue info. Changing
  it to self-discover means it needs access to `IssueDiscovery` and the project directory.
- **Mitigation:** The `JvmScope` already provides project directory access. `IssueDiscovery` is
  already used by other output classes (e.g., `GetStatusOutput`). Discovery with empty `sessionId`
  skips lock acquisition.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` — change
  `getOutput()` to accept 2 args `(issueName, targetBranch)`, discover next issue internally via
  `IssueDiscovery.findNextIssue()`, read PLAN.md goal, render appropriate box (issue-complete or
  scope-complete)
- `plugin/skills/work/SKILL.md` — update "Next Issue" section to stop calling `work-prepare` for
  discovery; just pass `issue_id` and `target_branch` to `/cat:work-complete`

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Modify `GetIssueCompleteOutput.getOutput()` to accept 2 args `(issueName, targetBranch)`:
  - Extract minor version from `issueName` (e.g., `2.1` from `2.1-fix-bug`)
  - Use `IssueDiscovery.findNextIssue()` with `Scope.MINOR`, the extracted version as target,
    empty `sessionId` (skip locking), and empty `excludePattern`
  - If `Found`: read the goal from the issue's PLAN.md, call `getIssueCompleteBox()`
  - If not `Found`: call `getScopeCompleteBox()`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java`
- Remove the 4-arg path from `getOutput()` (no longer needed — callers pass 2 args)
- Update `main()` CLI entry point to match the new 2-arg interface
- Update tests if any exist for GetIssueCompleteOutput
- Run `mvn -f client/pom.xml verify` to verify no regressions
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java`

### Wave 2
- Update `plugin/skills/work/SKILL.md` "Next Issue" section:
  - Remove the instruction to call `work-prepare` for next-issue discovery
  - Change to: after merge, invoke `/cat:work-complete ${issue_id} ${target_branch}` (2 args only)
  - Files: `plugin/skills/work/SKILL.md`

## Post-conditions
- [ ] `GetIssueCompleteOutput.getOutput()` accepts 2 args `(issueName, targetBranch)` and discovers
      the next issue internally
- [ ] No caller of `get-output work-complete` needs to invoke `work-prepare` for discovery
- [ ] The issue-complete box still displays "Next: {issue}" with goal when a next issue exists
- [ ] The scope-complete box displays when no next issue exists
- [ ] All existing tests pass (`mvn verify` exit code 0)
