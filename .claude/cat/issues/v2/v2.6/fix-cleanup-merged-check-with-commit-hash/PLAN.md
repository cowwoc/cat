<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: Fix Cleanup --merged Check with Commit Hash

## Problem

`plugin/skills/cleanup/first-use.md:122-123` reads `cat-base` into `BASE_BRANCH` then runs
`git branch --merged "$BASE_BRANCH"`. Since issue `2.1-record-fork-commit-at-worktree-creation`,
`cat-base` stores a fork-point commit hash (40-char hex) instead of a branch name. The `--merged`
check now tests ancestry to the fork-point commit rather than the current branch tip. Branches
merged into the base branch after the fork-point incorrectly report as NOT merged, producing false
high-risk warnings during cleanup.

## Satisfies

None (infrastructure bugfix)

## Reproduction

1. Create a worktree for any issue (cat-base stores fork-point commit hash)
2. Merge additional commits into the base branch after the worktree was created
3. Run `/cat:cleanup` on the worktree
4. Observe: cleanup warns the branch is not merged even though it has been merged

## Expected vs Actual

- **Expected:** `git branch --merged <base-branch-tip>` correctly identifies merged branches
- **Actual:** `git branch --merged <fork-point-hash>` misses branches merged after the fork-point

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Adding `cat-branch` requires updating all worktree creation paths (WorkPrepare.java
  and work-prepare skill); missing the file causes fail-fast errors
- **Mitigation:** Fail-fast with clear error if `cat-branch` missing; update WorkPrepare.java and
  work-prepare skill atomically

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — write `cat-branch`
  file alongside `cat-base` at worktree creation
- `plugin/skills/work-prepare/first-use.md` — write `cat-branch` alongside `cat-base`
- `plugin/skills/cleanup/first-use.md:122-123` — read `cat-branch` instead of `cat-base` for
  `--merged` check

## Pre-conditions

- [ ] Issue `2.1-record-fork-commit-at-worktree-creation` is closed (defines cat-base format)

## Execution Steps

1. **Step 1:** In `WorkPrepare.java`, after writing `cat-base`, write `cat-branch` file
   - File: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
   - After `Files.writeString(catBaseFile, commitHash)`, add:
     `Path catBranchFile = gitCommonDir.resolve("worktrees").resolve(branch).resolve("cat-branch");`
     `Files.writeString(catBranchFile, baseBranch);`

2. **Step 2:** In `work-prepare/first-use.md`, write `cat-branch` alongside `cat-base`
   - File: `plugin/skills/work-prepare/first-use.md`
   - After the line that writes `cat-base`, add a line writing `cat-branch`:
     `echo "${BASE_BRANCH}" > "$(git rev-parse --git-common-dir)/worktrees/${BRANCH}/cat-branch"`

3. **Step 3:** In `cleanup/first-use.md`, replace `cat-base` with `cat-branch` for `--merged` check
   - File: `plugin/skills/cleanup/first-use.md:122-123`
   - Replace: `BASE_BRANCH=$(cat "$(git rev-parse --git-dir)/cat-base")`
   - With: `BASE_BRANCH=$(cat "$(git rev-parse --git-dir)/cat-branch")`
   - Add fail-fast error block if `cat-branch` file is missing

4. **Step 4:** Add regression test to `tests/worktree-isolation.bats`
   - Verify `cat-branch` contains a branch name (not a 40-char commit hash)
   - Verify `git branch --merged $(cat cat-branch)` succeeds when branch is merged

5. **Step 5:** Run full test suite
   - `mvn -f client/pom.xml test`
   - `bats tests/worktree-isolation.bats`

## Post-conditions

- [ ] `cat-branch` file written by `WorkPrepare.java` at worktree creation containing base branch name
- [ ] `cat-branch` file written by `work-prepare` skill at worktree creation
- [ ] `cleanup/first-use.md` uses `cat-branch` for `git branch --merged` check
- [ ] Cleanup correctly identifies branches merged after the fork-point as merged
- [ ] Regression test in `worktree-isolation.bats` verifies `cat-branch` contains branch name
- [ ] All tests pass with no regressions
