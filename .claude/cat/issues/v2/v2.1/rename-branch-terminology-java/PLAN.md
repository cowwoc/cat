# Plan: rename-branch-terminology-java

## Goal

Rename all `base_branch`/`BASE_BRANCH`/`baseBranch`/`base-branch` and `worktree_branch`/`worktree branch` references
in Java client source and test files to use consistent `source_branch`/`target_branch` terminology.

- **`base_branch` → `target_branch`**: The branch being merged INTO (e.g., `main`, `v2.1`)
- **`worktree_branch` / issue branch → `source_branch`**: The branch being merged FROM (the issue/feature branch)

## Parent Issue

`2.1-rename-branch-terminology` (decomposed)

## Sequence

Sub-issue 2 of 2 — runs in parallel with `2.1-rename-branch-terminology-plugin`

## Satisfies

None — infrastructure terminology cleanup.

## Files to Modify

### Java Client Source

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java` — error message text
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` — class name may stay (describes
  purpose), but update Javadoc/comments
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` — argument description
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` — method comments, variable names
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — method comments, variable names
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — comments, step descriptions
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java` — Javadoc
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/ExistingWorkChecker.java` — CLI help text
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` — class Javadoc, method comments
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` — comment
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java` — class Javadoc, method comments
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java` — comment

### Java Client Tests

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ExistingWorkCheckerTest.java` — variable names, branch names
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java` — comments, variable names
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetIssueCompleteOutputTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseSafeTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java` — comments, variable names
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java` — branch name string
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforcePluginFileIsolationTest.java` — comments

## Pre-conditions

- [ ] Parent issue `2.1-rename-branch-terminology` exists

## Execution Waves

### Wave 1

1. **Rename variables/comments in Java source files** — Update variable names (`baseBranch` → `targetBranch`,
   `worktreeBranch` → `sourceBranch`), method parameters, Javadoc, and error messages in client source files.
2. **Update Java test files** — Rename variables, branch name strings, and comments in test classes to match new
   terminology.
3. **Run all tests** — `mvn -f client/pom.xml test` to verify no regressions.


## Post-conditions

- [ ] No references to `BASE_BRANCH`, `baseBranch`, or `base-branch` remain in client/ source or test files
      (except class name `WarnBaseBranchEdit` which describes purpose, and non-branch-terminology uses)
- [ ] No references to `worktreeBranch` or `worktree_branch` (as a named concept for the source) remain in client/
- [ ] All `targetBranch`/`TARGET_BRANCH` references refer to the branch being merged INTO
- [ ] All `sourceBranch`/`SOURCE_BRANCH` references refer to the branch being merged FROM
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
