# Plan: status-oldest-first-ordering

## Goal
Update `/cat:status` to list issues in the same oldest-first order used by `/cat:work` (by git
creation time of STATE.md), so users see issues in the same sequence they will be worked on.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — reuses existing `listIssueDirsByAge()` method added by `2.1-work-select-oldest-first`.
- **Mitigation:** No new git subprocess logic needed; just swap call sites.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` — change the two
  `listIssueDirs()` call sites in the status display path (lines ~908, ~928, ~1411) to call
  `listIssueDirsByAge()` instead

## Pre-conditions
- [ ] `2.1-work-select-oldest-first` is closed (provides `listIssueDirsByAge()`)

## Sub-Agent Waves

### Wave 1
- In `IssueDiscovery.java`, replace the `listIssueDirs(minorDir)` calls used for status display
  with `listIssueDirsByAge(minorDir)` (and similarly for patchDir)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
  - Identify all call sites by searching for `listIssueDirs(` in the status-related methods
    (look for methods involved in building status output)
  - Change each `listIssueDirs(` to `listIssueDirsByAge(` at those sites
- Run `mvn -f client/pom.xml test` to verify no regressions

## Post-conditions
- [ ] `/cat:status` lists issues within each version sorted ascending by git creation date (oldest
  first), matching the order `/cat:work` selects them
- [ ] Alphabetical tiebreaker applies when two issues share the same creation timestamp
- [ ] All existing tests pass without modification
