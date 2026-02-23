# Plan: fix-work-prepare-diagnostic-scan-limit

## Problem
WorkPrepare.buildIssueIndex uses `Files.walk(issuesDir, 4).limit(1000)` which truncates the diagnostic index when
projects have more than ~333 issues (each with ~3 files). Issues beyond entry 999 appear as `not_found` in
`blocked_tasks` diagnostic output even when they exist and are closed. Additionally, circular dependencies between
issues are not detected or prevented, allowing deadlocks in issue selection.

## Satisfies
None - infrastructure bugfix

## Root Cause
1. `DIAGNOSTIC_SCAN_LIMIT=1000` in `WorkPrepare.java` (line 121) artificially limits the `Files.walk` stream in
   `buildIssueIndex` (line 577), causing incomplete diagnostic output for large projects.
2. Neither `IssueDiscovery` nor `WorkPrepare` detect circular dependency chains (e.g., A depends on B, B depends on A).

## Expected vs Actual
- **Expected:** Diagnostic output accurately reports dependency status; circular dependencies are detected and reported.
- **Actual:** Dependencies beyond file-system entry 999 shown as `not_found`; circular dependencies silently cause all
  involved issues to appear blocked forever.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Removing the scan limit could slow diagnostics on very large projects; circular dependency
  detection adds new validation logic.
- **Mitigation:** Unit tests for both fixes; performance is acceptable since diagnostics run infrequently.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - Remove DIAGNOSTIC_SCAN_LIMIT, error if
  scan exceeds a safety threshold instead of silently truncating
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` - Add circular dependency detection to
  `getBlockingDependencies` or `findNextIssue`
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/WorkPrepareTest.java` - Add test for diagnostic correctness
  with >1000 entries
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/IssueDiscoveryTest.java` - Add test for circular dependency
  detection

## Test Cases
- [ ] Diagnostic output correct when project has >1000 file-system entries (no false `not_found`)
- [ ] Error returned when scan limit exceeded instead of silent truncation
- [ ] Simple circular dependency detected (A depends on B, B depends on A)
- [ ] Complex circular dependency detected (A -> B -> C -> A)
- [ ] Non-circular dependency chains still work correctly
- [ ] Existing circular dependencies in a project are reported in diagnostic output

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Remove `DIAGNOSTIC_SCAN_LIMIT` constant from `WorkPrepare.java`. Replace
   `.limit(DIAGNOSTIC_SCAN_LIMIT)` in `buildIssueIndex` with logic that errors if the scan exceeds a safety threshold
   rather than silently truncating results.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
2. **Step 2:** Add circular dependency detection. When resolving blocking dependencies, detect cycles by tracking the
   dependency chain and reporting circular references with their cycle path (e.g., `A -> B -> C -> A`). Prevent new
   circular dependencies from being added.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
3. **Step 3:** Include circular dependencies in diagnostic output. Add a `circular_dependencies` field to the NO_TASKS
   response showing detected cycles with their paths.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
4. **Step 4:** Write regression tests for diagnostic accuracy with many issues, error on limit exceeded, and circular
   dependency detection (simple and complex cycles).
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/util/WorkPrepareTest.java`,
     `client/src/test/java/io/github/cowwoc/cat/hooks/util/IssueDiscoveryTest.java`

## Post-conditions
- [ ] `buildIssueIndex` no longer silently truncates; returns error if scan limit exceeded
- [ ] Circular dependencies are detected and reported with cycle path details
- [ ] Attempting to add a circular dependency is rejected with descriptive error message
- [ ] Existing circular dependencies in a project are shown in diagnostic output
- [ ] Regression tests pass for diagnostic correctness with >1000 file-system entries
- [ ] Regression tests pass for circular dependency detection (simple and complex cycles)
- [ ] No regressions in existing issue discovery functionality
- [ ] E2E: Run work-prepare on a project with >1000 file-system entries and verify diagnostic accuracy
- [ ] E2E: Create a circular dependency scenario and verify it is detected and reported
