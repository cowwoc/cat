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

## Parent Requirements

None (infrastructure bugfix)

## Reproduction

1. Create a worktree for any issue
2. Run `/cat:cleanup` on the worktree
3. Observe: cleanup fails trying to read the target branch name

## Expected vs Actual

- **Expected:** `git branch --merged <target-branch-name>` correctly identifies merged branches
- **Actual:** Cleanup fails with a missing-file error

## Investigation Results

Investigated all candidate mechanisms for reading the target branch from within a worktree:

| Mechanism | Stores target branch? | Pushed to remote? | Notes |
|---|---|---|---|
| STATE.md | No (currently) | Yes | Version-controlled, collaborative |
| Lock file | No (currently) | No | Local only, session-scoped |
| `work-prepare` JSON stdout | Yes | No | Not persisted to disk |
| Git upstream tracking | No | No | Not set by `git worktree add` |
| `git config branch.<name>.key` | No (currently) | No | Local `.git/config` only |
| `git merge-base` heuristic | Indirect | N/A | Requires naming convention for candidates |

Git does not natively store which branch a branch was forked from. All heuristic approaches
(`git show-branch`, `git reflog`, `git merge-base`) are fragile and unreliable. The target branch
must be stored explicitly.

**Chosen approach:** Add a `Target Branch:` field to each issue's STATE.md. STATE.md is already
version-controlled and pushed to remote, so collaborators working on the same branch get the target
branch info automatically. `WorkPrepare` writes the field at worktree creation time.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Existing STATE.md files lack the `Target Branch:` field; cleanup must handle the
  missing field gracefully (skip the `--merged` check with a warning)
- **Mitigation:** Add a migration to backfill `Target Branch:` for open issues; fail gracefully
  when the field is absent

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — write `Target Branch:`
  field to STATE.md at worktree creation
- `plugin/skills/cleanup/first-use.md:122-123` — read `Target Branch:` from issue STATE.md instead
  of the removed marker file

## Pre-conditions

- [x] Issue `2.1-remove-cat-branch-point-references` is closed

## Execution Waves

### Wave 1

1. **Step 1:** Update `WorkPrepare.java` to write `Target Branch:` to STATE.md
   - After creating the worktree, write `- **Target Branch:** <branch-name>` to the issue's
     STATE.md (the target branch is already available as a local variable)
   - Ensure the field is written idempotently (don't duplicate if already present)

2. **Step 2:** Update `cleanup/first-use.md` to read `Target Branch:` from STATE.md
   - File: `plugin/skills/cleanup/first-use.md:122-123`
   - Read `Target Branch:` from STATE.md using grep/sed
   - If `Target Branch:` is empty/absent, skip `--merged` check with a warning
   - If present, pass it to `git branch --merged "$TARGET_BRANCH"`

3. **Step 3:** Add regression test to `tests/worktree-isolation.bats`
   - Verify cleanup correctly identifies merged branches when STATE.md has `Target Branch:`
   - Verify cleanup skips the check gracefully when `Target Branch:` is absent

4. **Step 4:** Run full test suite
   - `mvn -f client/pom.xml test`
   - `bats tests/worktree-isolation.bats`


## Post-conditions

- [ ] No new marker files written to git directory
- [ ] No branch name derivation from naming conventions
- [ ] `WorkPrepare` writes `Target Branch:` to STATE.md at worktree creation
- [ ] Cleanup reads `Target Branch:` from STATE.md for `git branch --merged`
- [ ] Cleanup skips `--merged` check gracefully when `Target Branch:` is absent
- [ ] Regression test in `worktree-isolation.bats` verifies merged branch detection
- [ ] All tests pass with no regressions
