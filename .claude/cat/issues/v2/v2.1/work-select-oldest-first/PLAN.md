# Plan: work-select-oldest-first

## Goal
When /cat:work scans for the next available issue, it selects the issue created earliest
(oldest first), where "created" means the date of the first git commit that added the
issue's STATE.md. This ensures consistent, predictable ordering by time-of-tracking rather
than alphabetical name.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Git log call per issue adds subprocess overhead; multiple open issues = multiple
  git subprocesses.
- **Mitigation:** Typical projects have dozens of issues at most; overhead is negligible.
  If git fails (e.g., no git repo in tests), fall back to alphabetical order (Long.MAX_VALUE
  sort key = sorts last).

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` - modify
  `listIssueDirs` to sort by git creation date; add `getIssueCreationTime` helper
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java` - add
  `selectsOldestIssueFirst` test

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Add `getIssueCreationTime(Path issueDir)` private method to `IssueDiscovery.java`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
  - Runs: `git -C {projectDir} log --diff-filter=A --format=%at -- {relative path to STATE.md}`
  - Returns first line of output parsed as `long`; returns `Long.MAX_VALUE` if command fails,
    returns no output, or output is not parseable
  - Use `ProcessBuilder` following the existing pattern in `TestUtils.runGit()`
  - Catch `IOException`, `InterruptedException`, `NumberFormatException` silently
- Modify `listIssueDirs` sort from `.sorted()` to `.sorted(Comparator.comparingLong(this::getIssueCreationTime).thenComparing(Comparator.naturalOrder()))`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
  - Wraps `getIssueCreationTime` call in a lambda that handles IOException (return `Long.MAX_VALUE`)
  - `thenComparing(Comparator.naturalOrder())` breaks ties alphabetically for determinism

### Wave 2
- Add failing test `selectsOldestIssueFirst` to `IssueDiscoveryTest.java`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java`
  - Use `TestUtils.createTempGitRepo("main")` to create an isolated git repo
  - Manually create `.claude/cat/issues` directory structure in the git repo
  - Commit `issue-b` (open) first with `--date="2026-01-01T00:00:01Z"`
  - Commit `issue-a` (open) second with `--date="2026-01-01T00:00:02Z"` (newer, alphabetically first)
  - Verify `findNextIssue` returns `2.1-issue-b` (the oldest, not the alphabetically first)
  - Clean up temp repo with `TestUtils.deleteDirectoryRecursively(projectDir)`
- Run `mvn -f client/pom.xml test` to verify new test passes and no regressions
  - Files: none (validation only)

## Post-conditions
- [ ] `listIssueDirs` returns issues sorted ascending by git commit creation date (oldest first)
- [ ] Issues with no git history (e.g., in test environments without git init) sort last,
  not first, preserving deterministic behavior
- [ ] Ties in creation timestamp are broken alphabetically for determinism
- [ ] New test `selectsOldestIssueFirst` passes
- [ ] All existing `IssueDiscoveryTest` tests pass without modification
- [ ] E2E: In a project with multiple open issues committed at different times, /cat:work
  selects the issue whose STATE.md was first committed to the git repository
