<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: Record Fork Commit at Worktree Creation

## Goal

Replace the branch-name-based `cat-base` with a fixed commit hash recorded at worktree creation time. All downstream
git operations (diff, checkout, rebase, squash verification) use this immutable fork-point commit instead of
re-resolving a branch name that may have advanced.

## Satisfies

None (infrastructure improvement)

## Background

`cat-base` currently stores a branch name (e.g., `v2.1`). Every downstream skill that needs to compare against the
fork point must either:
1. Resolve the branch name to a commit — which may return a DIFFERENT commit if the branch advanced
2. Compute `git merge-base <branch> HEAD` — which can also shift if the branch advanced

This caused a real bug: `git checkout v2.1 -- <path>` imported files added to v2.1 after the worktree was created,
contaminating the squashed commit tree. The fix-git-skill-race-conditions issue (closed) added pin-once patterns within
individual skills, but the root cause persists: each skill must independently pin the branch, and documentation-based
guidance (e.g., git-squash `first-use.md`) can't prevent agent mistakes.

Recording the fork commit once at worktree creation eliminates the entire class of race conditions.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** All worktree-aware skills and Java tools read `cat-base`; changing format requires updating every reader
- **Mitigation:** Add `cat-base-commit` alongside `cat-base` (additive change); migrate readers incrementally; existing
  `cat-base` remains for branch-name lookups (e.g., merge target)

## Approach

Store BOTH the branch name and the fork commit:
- `cat-base` — branch name (unchanged, still needed for merge target identification)
- `cat-base-commit` — commit hash at fork point (NEW, used for all diff/verification operations)

## Audit: Locations Using Branch-Name References

### Java code (write)

| File | Usage | Action |
|------|-------|--------|
| `WorkPrepare.java:1157` | `Files.writeString(catBaseFile, baseBranch)` | Also write `cat-base-commit` with `git rev-parse HEAD` |

### Java code (read)

| File | Usage | Action |
|------|-------|--------|
| `GitRebaseSafe.java` | Reads `cat-base` for rebase target | Use `cat-base-commit` for rebase |
| `MergeAndCleanup.java` | Reads `cat-base` for merge target | Keep using `cat-base` (branch name needed for merge) |
| `GitMergeLinear.java` | Reads `cat-base` for merge operations | Keep `cat-base` for merge target; use `cat-base-commit` for diff |
| `EnforcePluginFileIsolation.java` | Reads `cat-base` for branch detection | Keep `cat-base` (branch name comparison) |
| `WarnBaseBranchEdit.java` | Reads `cat-base` for branch detection | Keep `cat-base` (branch name comparison) |
| `VerifyStateInCommit.java` | Reads `cat-base` | Evaluate: branch name or commit needed? |
| `WarnUnsquashedApproval.java` | Reads `cat-base` | Evaluate: branch name or commit needed? |
| `InjectSessionInstructions.java` | Reads `cat-base` | Keep `cat-base` (metadata only) |

### Skill files (high-risk — use branch name in git commands)

| File | Lines | Pattern | Action |
|------|-------|---------|--------|
| `stakeholder-review/first-use.md` | 293, 436-437, 565 | `git diff "${BASE_BRANCH}..HEAD"` | Read `cat-base-commit` instead |
| `work-merge/first-use.md` | 126, 132 | `git diff` with base branch | Read `cat-base-commit` for diff |
| `work-with-issue/first-use.md` | 284, 806, 1047, 1094 | `git diff` with base branch | Read `cat-base-commit` |
| `git-squash/first-use.md` | 200, 237, 273, 314 | `git rev-parse <base-branch>` | Read `cat-base-commit` directly |

### Skill files (safe — branch name is correct usage)

| File | Usage | Action |
|------|-------|--------|
| `cleanup/first-use.md` | `branch --merged` check | Keep `cat-base` (branch-level check is correct) |
| `git-merge-linear/first-use.md` | Merge target | Keep `cat-base` (merge needs branch name) |
| `learn/phase-prevent.md` | Metadata display | Keep `cat-base` (informational) |

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Update `WorkPrepare.java` to write `cat-base-commit`
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
   - After writing `cat-base`, also write `cat-base-commit` with `git rev-parse HEAD`
   - Add test verifying both files are created

2. **Step 2:** Update Java tools that should use fixed commit
   - Files: `GitRebaseSafe.java`, `GitMergeLinear.java` (diff operations only)
   - Read `cat-base-commit` for operations that compare against fork point
   - Keep reading `cat-base` for operations that need the branch name (merge target)
   - Add fallback: if `cat-base-commit` doesn't exist, compute `git merge-base` from `cat-base`

3. **Step 3:** Update skill files to reference `cat-base-commit`
   - Files: `stakeholder-review/first-use.md`, `work-merge/first-use.md`,
     `work-with-issue/first-use.md`, `git-squash/first-use.md`
   - Replace `$(cat cat-base)` with `$(cat cat-base-commit)` in diff/verification commands
   - Remove per-skill pin-once patterns where `cat-base-commit` makes them redundant

4. **Step 4:** Update `git-squash/first-use.md` critical rules
   - Simplify "Pin Base Branch Reference" section — pinning is now done at worktree creation
   - Update merge-base diff section to reference `cat-base-commit` directly

5. **Step 5:** Update worktree isolation tests
   - Files: `tests/worktree-isolation.bats`
   - Add test: verify `cat-base-commit` contains expected fork-point hash
   - Add test: verify `cat-base-commit` doesn't change when base branch advances

6. **Step 6:** Run full test suite
   - `mvn -f client/pom.xml test`
   - `bats tests/worktree-isolation.bats`

## Post-conditions

- [ ] `WorkPrepare` writes both `cat-base` (branch name) and `cat-base-commit` (commit hash) at worktree creation
- [ ] All diff/verification operations in Java tools use `cat-base-commit`, not branch name resolution
- [ ] All skill file git commands that compare against fork point use `cat-base-commit`
- [ ] `cat-base` is still used where branch name is genuinely needed (merge target, branch detection)
- [ ] Fallback exists for worktrees created before this change (compute merge-base from `cat-base`)
- [ ] All existing tests pass with no regressions
- [ ] Bats tests verify `cat-base-commit` correctness and immutability
