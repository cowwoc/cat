# Plan: fix-oversized-decomposed-parent

## Problem
`WorkPrepare.java` returns `OVERSIZED` for decomposed parent issues whose sub-issues are all closed.
The token estimation check runs before any decomposed-parent detection, causing the large parent
PLAN.md to trigger the size limit even though the only work needed is closure (verifying acceptance
criteria and updating STATE.md).

## Parent Requirements
None

## Reproduction Code
```
// work-prepare is invoked against 2.1-rename-cat-config-files (decomposed parent, all sub-issues closed)
// STATE.md contains: "## Decomposed Into" with 3 qualified sub-issue names
// All 3 sub-issues have Status: closed
// Result: {"status":"OVERSIZED","estimated_tokens":176000,...}  ← wrong
// Expected: {"status":"READY",...}
```

## Expected vs Actual
- **Expected:** `work-prepare` returns `READY` for a decomposed parent with all sub-issues closed
- **Actual:** `work-prepare` returns `OVERSIZED` with `estimated_tokens: 176000`

## Root Cause
In `WorkPrepare.java` (`run()` method), the OVERSIZED check (lines ~285–298) executes immediately
after casting the discovery result to `Found`, before any check for decomposed-parent status.

`IssueDiscovery.findSpecificIssue()` (line 765) returns `Decomposed` only when
`isDecomposedParent(stateLines) && !allSubissuesClosed(statePath)`. When all sub-issues are
closed, the condition is false, so execution falls through to `return new DiscoveryResult.Found(...)`.
`WorkPrepare` then estimates tokens from the large parent PLAN.md and returns OVERSIZED.

The fix adds a `boolean isDecomposedComplete` flag to `Found`, set to `true` whenever a decomposed
parent has all sub-issues closed. `WorkPrepare` skips the OVERSIZED check when
`found.isDecomposedComplete()` is true, proceeding directly to `executeWithLock` with a minimal
token estimate.

## Research Findings
- `IssueDiscovery.java` line 765: `if (isDecomposedParent(stateLines) && !allSubissuesClosed(statePath))` → returns `Decomposed`; otherwise falls through to `Found`
- `IssueDiscovery.java` line 1105 (`findNextIssue`): same pattern — closed-parent falls through to `Found`
- `WorkPrepare.java` lines 285–298: token estimation and OVERSIZED check; no decomposed-parent awareness
- `WorkPrepare.java` line 437: `executeWithLock(input, projectDir, mapper, issueId, major, minor, issueName, issuePath, targetBranch, planPath, estimatedTokens, issueBranch)` — pass `5000` as `estimatedTokens` for decomposed-complete issues
- `IssueDiscovery.java` line 568 (test): one direct `new DiscoveryResult.Found(...)` constructor call needing update
- Three other open issues touch `WorkPrepare.java` (`fix-lock-worktree-path-at-acquire`, `fix-work-prepare-lock-leak`, `fix-workprepare-ioexception-handling`) in different methods — no logical conflicts, only potential merge conflicts

## Impact Notes
Three other open issues modify `WorkPrepare.java` in different methods. Merge conflicts are possible
but there are no logical conflicts. The change is additive: new field on `Found` and a new early-exit
branch before the OVERSIZED check.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** All existing `WorkPrepare` and `IssueDiscovery` tests must still pass; the `Found` record gains one field
- **Mitigation:** Regression test added; existing tests exercise the non-decomposed path unchanged

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` — add `boolean isDecomposedComplete` field to `Found` record; set it at both `Found` creation sites
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — skip OVERSIZED check when `found.isDecomposedComplete()` is true
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java` — update the one direct `Found` constructor call (line 568) to pass `false` for `isDecomposedComplete`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add regression test `testDecomposedParentAllSubIssuesClosed_returnsReady`

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Add `boolean isDecomposedComplete` field to `IssueDiscovery.DiscoveryResult.Found` record
  - Location: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
  - The `Found` record signature is at line ~230: `record Found(String issueId, String major, String minor, String patch, String issueName, String issuePath, String scope, boolean createStateMd, boolean isCorrupt)`
  - Add `boolean isDecomposedComplete` as the last parameter
  - Update the Javadoc `@param` block: add `@param isDecomposedComplete true if this is a decomposed parent with all sub-issues closed`
  - Update the compact constructor validation comment to mention the new field (no validation needed since it is a primitive boolean)
  - Update the `toJson` method to NOT include `isDecomposedComplete` in JSON output (it is an internal flag)
  - Update `findSpecificIssue` first `Found` creation site (line ~799): compute `boolean isDecomposedComplete = isDecomposedParent(stateLines)` immediately before the return statement, then pass it as the last argument
  - Update `findNextIssue` `Found` creation site (line ~1145): compute `boolean isDecomposedComplete = isDecomposedParent(stateLines)` immediately before the return statement, then pass it as the last argument
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`

- Skip OVERSIZED check in `WorkPrepare.java` for decomposed-complete issues
  - Location: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
  - After the `Found` cast (line ~258): `IssueDiscovery.DiscoveryResult.Found found = (IssueDiscovery.DiscoveryResult.Found) discoveryResult;`
  - After the corrupt check block (ends at line ~278) and before the token estimation step (line ~285), add:
    ```java
    // Decomposed parents with all sub-issues closed require only closure work — skip OVERSIZED check
    if (found.isDecomposedComplete())
    {
      String issueBranch = buildIssueBranch(major, minor, found.patch(), issueName);
      return executeWithLock(input, projectDir, mapper, issueId, major, minor, issueName,
        issuePath, targetBranch, planPath, 5000, issueBranch);
    }
    ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

- Update the direct `Found` constructor call in `IssueDiscoveryTest.java`
  - Location: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java` line ~568
  - Current: `new DiscoveryResult.Found("2.1-my-feature", "2", "1", "", "my-feature", "/path/to/issue", "all", false, false)`
  - New: `new DiscoveryResult.Found("2.1-my-feature", "2", "1", "", "my-feature", "/path/to/issue", "all", false, false, false)`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java`

### Wave 2
- Add regression test `testDecomposedParentAllSubIssuesClosed_returnsReady` to `WorkPrepareTest.java`
  - Location: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
  - Test setup (all steps inside `try (JvmScope scope = new TestJvmScope(projectDir, projectDir))`):
    1. Call `Path projectDir = createTempGitCatProject("v2.1");` to create an isolated git repo with CAT structure
    2. Call `createOversizedPlan(projectDir, "2", "1", "big-parent");` — this creates only `PLAN.md` in
       `.cat/issues/v2/v2.1/big-parent/`. It does NOT create `STATE.md`.
    3. Write the parent's `STATE.md` manually to `.cat/issues/v2/v2.1/big-parent/STATE.md` using
       `Files.writeString(...)` with this exact content (use a text block):
       ```
       # State

       - **Status:** open
       - **Progress:** 0%
       - **Dependencies:** []
       - **Blocks:** []

       ## Decomposed Into
       - 2.1-closed-sub
       ```
       (The `## Decomposed Into` entry must be a qualified issue name matching `major.minor-bare-name`.)
    4. Call `createIssue(projectDir, "2", "1", "closed-sub", "closed");` — this creates both `PLAN.md` and
       `STATE.md` for the sub-issue with `Status: closed` in `.cat/issues/v2/v2.1/closed-sub/`.
    5. Commit everything: `GitCommands.runGit(projectDir, "add", ".");` then
       `GitCommands.runGit(projectDir, "commit", "-m", "Add decomposed parent issue");`
    6. Create `WorkPrepare prepare = new WorkPrepare(scope);` and
       `PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM);`
    7. Call `String json = prepare.execute(input);`
    8. Parse JSON: `JsonNode node = scope.getJsonMapper().readTree(json);`
    9. Assert `node.path("status").asString()` equals `"READY"` (not `"OVERSIZED"`)
    10. Assert `node.path("estimated_tokens").asInt()` is less than or equal to `5000`
  - Wrap the test body in `try { ... } finally { TestUtils.deleteDirectoryRecursively(projectDir); }` outside the scope block
  - Run the full test suite to verify: `mvn -f client/pom.xml test`
  - Update STATE.md: set Status to `closed`, Progress to `100%`, Resolution to `implemented`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`

## Post-conditions
- [ ] `work-prepare` returns `READY` (not `OVERSIZED`) for a decomposed parent with all sub-issues closed
- [ ] Regression test `testDecomposedParentAllSubIssuesClosed_returnsReady` added to `WorkPrepareTest.java` and passes
- [ ] All existing tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: running `work-prepare` against `2.1-rename-cat-config-files` (the real decomposed parent with all sub-issues closed) returns `READY`
