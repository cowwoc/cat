<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: Fix Cleanup --merged Check with Target Branch Name

## Problem

`plugin/skills/cleanup/first-use.md:122-123` needs the target branch name to run
`git branch --merged "$TARGET_BRANCH"`. The file it previously read was removed in v2.1, leaving cleanup
without a source for the target branch name. This causes cleanup to fail with a missing-file error
when trying to determine whether an issue branch has been merged.

## Satisfies

None (infrastructure bugfix)

## Reproduction

1. Create a worktree for any issue
2. Run `/cat:cleanup` on the worktree
3. Observe: cleanup fails trying to read the target branch name

## Expected vs Actual

- **Expected:** `git branch --merged <target-branch-name>` correctly identifies merged branches
- **Actual:** Cleanup fails with a missing-file error

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The approach for determining the target branch must be reliable
- **Mitigation:** Fail-fast with clear error if target branch cannot be determined

## Files to Modify

- `plugin/skills/cleanup/first-use.md:122-123` — determine target branch for `--merged` check

## Pre-conditions

- [x] Issue `2.1-remove-cat-branch-point-references` is closed

## Execution Waves

### Wave 1

1. **Step 1:** Investigate how to determine the target branch for each worktree
   - Each issue has a STATE.md that records which version it belongs to
   - `work-prepare` outputs `target_branch` in its JSON result
   - Evaluate whether git-native tools (e.g., `git merge-base`) can determine merge status without
     knowing the target branch explicitly

2. **Step 2:** Update `cleanup/first-use.md` to use the chosen approach
   - File: `plugin/skills/cleanup/first-use.md:122-123`
   - Fail-fast with clear error if target branch cannot be determined
   - Pass target branch to `git branch --merged`

3. **Step 3:** Add regression test to `tests/worktree-isolation.bats`
   - Verify cleanup correctly identifies merged branches

4. **Step 4:** Run full test suite
   - `mvn -f client/pom.xml test`
   - `bats tests/worktree-isolation.bats`


## Post-conditions

- [ ] No new marker files written to git directory
- [ ] No branch name derivation from naming conventions
- [ ] Cleanup correctly identifies merged branches
- [ ] Regression test in `worktree-isolation.bats` verifies merged branch detection
- [ ] All tests pass with no regressions
