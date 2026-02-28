# Plan: squash-preserve-base-commit-timestamp

## Problem
When `cat:git-squash` creates the squashed commit via `git commit-tree`, no date environment variables are set,
so the squashed commit gets the current timestamp instead of retaining the base commit's original author and
committer dates. The base commit is the tip of the target branch before the squash (variable `base` in
`GitSquash.java`).

## Satisfies
None

## Reproduction
```
# Run /cat:git-squash on any issue branch.
# The resulting squashed commit on the target branch will show today's date,
# not the date of the original base commit.
git log --format="%H %aI %cI" -2   # compare issue branch and target branch timestamps
```

## Expected vs Actual
- **Expected:** Squashed commit retains the base commit's author date AND committer date.
- **Actual:** Squashed commit is timestamped with the current time.

## Root Cause
`GitSquash.java:148–149` calls `git commit-tree` via `runGitCommandSingleLineInDirectory()` without setting
`GIT_AUTHOR_DATE` or `GIT_COMMITTER_DATE` environment variables. `git commit-tree` defaults to the current
time for both dates when these variables are absent.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — date-only change; tree/content is unchanged. The existing diff verification
  step (Step 10) confirms content integrity is unaffected.
- **Mitigation:** Existing backup-and-diff verification in `GitSquash` provides a safety net.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitCommands.java` — add overloaded
  `runGitCommandSingleLineInDirectory(String directory, Map<String, String> env, String... args)` that merges
  custom env vars into the process environment before running.
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java` — before calling `git commit-tree`
  (Step 8), read `base` commit's author/committer dates and pass them via the new env-aware method.
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java` — add test cases verifying the
  squashed commit's author and committer dates match the base commit.

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In `GitCommands.java`, add a new overload:
  ```java
  public static String runGitCommandSingleLineInDirectory(
      String directory, Map<String, String> extraEnv, String... args) throws IOException
  ```
  Implementation: build the `String[] command` as usual (prepend `-C directory`), create a `ProcessBuilder`,
  call `pb.environment().putAll(extraEnv)` to layer the custom vars on top of the inherited environment, then
  run and return the single-line result. Follow the same error-handling pattern as the existing overload.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitCommands.java`

- In `GitSquash.java`, after determining `base` (Step 2) and before calling `commit-tree` (Step 8):
  1. Read `base` commit's ISO 8601 strict author date:
     `git log -1 --format=%aI <base>` → store as `baseAuthorDate`.
  2. Read `base` commit's ISO 8601 strict committer date:
     `git log -1 --format=%cI <base>` → store as `baseCommitterDate`.
  3. Build `Map<String, String> dateEnv = Map.of("GIT_AUTHOR_DATE", baseAuthorDate, "GIT_COMMITTER_DATE",
     baseCommitterDate)`.
  4. Replace the existing `runGitCommandSingleLineInDirectory(directory, "commit-tree", tree, "-p", base,
     "-m", commitMessage)` call with the new env-aware overload:
     `runGitCommandSingleLineInDirectory(directory, dateEnv, "commit-tree", tree, "-p", base, "-m",
     commitMessage)`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java`

- Add test cases to `GitSquashTest.java`:
  - Test: after squash, `git log -1 --format=%aI HEAD` on the target branch equals `git log -1 --format=%aI
    <base>` from before the squash.
  - Test: after squash, `git log -1 --format=%cI HEAD` equals `git log -1 --format=%cI <base>`.
  - Follow the existing test structure and temp-dir isolation pattern already established in `GitSquashTest.java`.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java`

### Wave 2
- Run the full test suite and verify all tests pass:
  ```bash
  mvn -f client/pom.xml test
  ```
  - Files: (verification only)

## Post-conditions
- [ ] Bug fixed: squashed commit author date matches the base commit's author date
- [ ] Bug fixed: squashed commit committer date matches the base commit's committer date
- [ ] New test cases added covering both author-date and committer-date preservation
- [ ] All existing tests still pass (`mvn -f client/pom.xml test` exits 0)
- [ ] No content regressions: diff between squashed commit and backup branch remains empty
- [ ] E2E: Run `/cat:git-squash` on a real issue branch; confirm `git log --format="%aI %cI" -1` on the
  resulting commit matches the base branch's pre-squash dates (not the current time)
