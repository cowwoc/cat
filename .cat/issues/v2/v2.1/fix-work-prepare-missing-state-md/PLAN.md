# Plan: fix-work-prepare-missing-state-md

## Problem
`IssueDiscovery.java` skips any issue directory that lacks a `STATE.md` file, treating absence of
the file as non-existence of the issue. This causes issues that were created with only `PLAN.md`
(no `STATE.md`) to be invisible to `work-prepare`, making them unselectable. Additionally,
`getIssueStatus()` throws `IOException` when the status field is missing or invalid, causing
those issues to be silently skipped instead of defaulted to `open`.

## Satisfies
None ��� internal tooling bugfix

## Root Cause
Four locations in `IssueDiscovery.java` guard on `Files.isRegularFile(statePath)` and skip
directories where `STATE.md` is absent:
- `findIssueInDir()` line ~1022: skip → treat as open
- `hasOpenIssues()` lines ~916, ~936: skip → treat as open
- `findMatchingBareName()` lines ~601, ~608, ~615: exclude from matches → include
- Specific issue lookup line ~698–703: return `NotExecutable` when unreadable → create STATE.md

Additionally, `getIssueStatus(List<String>, Path)` throws `IOException` when no valid status
line is found, silently skipping those issues instead of defaulting to `open`.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Changing skip-to-open defaults could expose issues that should not be worked on
- **Mitigation:** Scope is limited to missing STATE.md / missing status field; all other
  filtering (dependencies, decomposed parents, locks) still applies

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` ��� core fix
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` ��� create STATE.md
  in worktree after it is set up for an issue that had no STATE.md
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` ��� regression tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java` ��� unit tests

## Pre-conditions
- All dependent issues are closed

## Sub-Agent Waves

### Wave 1
1. **Step 1:** In `IssueDiscovery.getIssueStatus(List<String>, Path)`, change the IOException
   thrown when no status line is found to return `"open"` instead. Log a warning containing the
   file path and the fact that status is missing/invalid (reusing the error-message pattern from
   `improve-parsestatus-error-with-file-location`).
   - Files: `IssueDiscovery.java`
2. **Step 2:** In `IssueDiscovery.findIssueInDir()`, replace the early `continue` for missing
   STATE.md with: use an empty `List<String>` for `stateLines` and skip the status read (treat
   status as `"open"`). All other guards (dependencies, decomposed, locks) still apply.
   - Files: `IssueDiscovery.java`
3. **Step 3:** In `IssueDiscovery.hasOpenIssues()` (two loop bodies, for minor-level and
   patch-level issues), replace the early `continue` for missing STATE.md with `return true`
   (missing STATE.md = open issue exists).
   - Files: `IssueDiscovery.java`
4. **Step 4:** In `IssueDiscovery.findMatchingBareName()`, remove the
   `&& Files.isRegularFile(issueDir.resolve("STATE.md"))` guard from the three matching checks
   (lines ~601, ~608, ~615) so issue dirs with only `PLAN.md` are included as candidates.
   - Files: `IssueDiscovery.java`
5. **Step 5:** In the specific issue lookup path (around line 698), when `STATE.md` is missing
   (catches `IOException` from `readFileLines`), do not return `NotExecutable`. Instead, treat
   `stateLines` as empty and status as `"open"`, and set a flag `missingStateMd = true`.
   - Files: `IssueDiscovery.java`
6. **Step 6:** In `WorkPrepare`, after the worktree is created and the branch is checked out,
   check whether the issue had `missingStateMd = true` (or pass this flag from `IssueDiscovery`).
   If so, write a minimal `STATE.md` into the worktree copy of the issue directory:
   ```
   # State

   - **Status:** open
   - **Progress:** 0%
   - **Dependencies:** []
   - **Blocks:** []
   ```
   Do NOT write STATE.md to the main workspace ��� only to the worktree copy.
   - Files: `WorkPrepare.java`
7. **Step 7:** Write regression tests in `WorkPrepareTest.java`:
   - Test: issue dir with only `PLAN.md` is selected by work-prepare and returned with status READY
   - Test: work-prepare creates STATE.md in the worktree for a PLAN.md-only issue
   - Test: STATE.md with missing status field is treated as open
   Write unit tests in `IssueDiscoveryTest.java`:
   - Test: `findIssueInDir` includes PLAN.md-only dirs
   - Test: `hasOpenIssues` returns true for PLAN.md-only dirs
   - Test: `getIssueStatus` returns `open` when status field is missing
   - Files: `WorkPrepareTest.java`, `IssueDiscoveryTest.java`
8. **Step 8:** Run tests: `mvn -f client/pom.xml test`
   Update STATE.md: status: closed, progress: 100%

## Post-conditions
- [ ] Bug fixed: `work-prepare` treats issues without STATE.md as open and selects them for work
- [ ] Bug fixed: `work-prepare` treats STATE.md with missing or invalid status field as open
- [ ] Missing STATE.md is created in the issue worktree (not main workspace) with minimal open state
- [ ] Created STATE.md follows standard format: Status open, Progress 0%, empty Dependencies and Blocks
- [ ] Invalid/missing status values are logged with file path and context before defaulting to open
- [ ] Regression test: PLAN.md-only issue is selected by work-prepare (returns READY)
- [ ] Regression test: work-prepare creates STATE.md in worktree for PLAN.md-only issue
- [ ] Regression test: STATE.md with missing status field defaults to open
- [ ] No regressions in existing issue selection logic (all existing tests pass)
- [ ] E2E: run `work-prepare` against a test repo containing issue with only PLAN.md, confirm READY