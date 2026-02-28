<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: Record Fork Commit at Worktree Creation

## Goal

Change `cat-base` from storing a branch name to storing the fork-point commit hash. All downstream git operations
(diff, checkout, rebase, squash verification) use this immutable commit instead of re-resolving a branch name that may
have advanced.

## Satisfies

None (infrastructure improvement)

## Background

`cat-base` currently stores a branch name (e.g., `v2.1`). Every downstream skill that needs to compare against the fork
point must either:
1. Resolve the branch name to a commit — which may return a DIFFERENT commit if the branch advanced
2. Compute `git merge-base <branch> HEAD` — which can also shift if the branch advanced

This caused a real bug: `git checkout v2.1 -- <path>` imported files added to v2.1 after the worktree was created,
contaminating the squashed commit tree.

Recording the fork commit once at worktree creation eliminates the entire class of race conditions.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** All worktree-aware code reads `cat-base`; changing its format requires updating every reader
- **Mitigation:** All git operations (`rev-parse`, `rebase --onto`, `merge-base`, `diff`) accept commit hashes
  interchangeably with branch names. The only exception is `git fetch origin <branch>` which needs a branch name —
  this is addressed by passing the branch name as a CLI argument.

## Audit: `cat-base` Consumers

### Existence-only checks (no change needed — work with any file content)

| File | Usage |
|------|-------|
| `EnforcePluginFileIsolation.java` | `Files.exists(catBase)` — detects worktree |
| `WarnBaseBranchEdit.java` | `Files.exists(catBaseFile)` — detects worktree |
| `VerifyStateInCommit.java` | `Files.exists(gitCatBase)` — detects worktree |
| `WarnUnsquashedApproval.java` | `Files.exists(catBaseFile)` — detects worktree |

### Content readers (need updating)

| File | Current usage | Action |
|------|---------------|--------|
| `WorkPrepare.java:1157` | Writes branch name | Write commit hash (`git rev-parse HEAD`) |
| `GitRebaseSafe.java:106` | Reads, then `rev-parse` to pin | Already pins — works with commit hash as-is |
| `MergeAndCleanup.java:175` | Reads for `git fetch origin <branch>` and rebase | Accept base branch as CLI arg instead |
| `GitMergeLinear.java:129` | Reads, already receives branch as CLI arg | Remove `detectBaseBranch()` (redundant with CLI arg) |
| `InjectSessionInstructions.java` | Reads for metadata display | Display commit hash (or derive branch name from convention) |

### Skill files

| File | Pattern | Action |
|------|---------|--------|
| `stakeholder-review/first-use.md` | `git diff "${BASE_BRANCH}..HEAD"` | Read `cat-base` as commit hash directly |
| `work-merge/first-use.md` | `git diff` with base branch | Read `cat-base` as commit hash; pass branch name for merge |
| `work-with-issue/first-use.md` | `git diff` with base branch | Read `cat-base` as commit hash |
| `git-squash/first-use.md` | `git rev-parse <base-branch>` | Read `cat-base` directly (already a commit hash) |
| `cleanup/first-use.md` | `branch --merged` check | Read `cat-base` as commit hash (works for `--merged`) |
| `learn/phase-prevent.md` | Metadata display | Display commit hash |

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Change `WorkPrepare.java` to write commit hash
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
   - Replace `Files.writeString(catBaseFile, baseBranch)` with `Files.writeString(catBaseFile, commitHash)` where
     `commitHash = GitCommands.runGit(projectDir, "rev-parse", "HEAD")`
   - Update tests

2. **Step 2:** Update `MergeAndCleanup` to accept base branch as CLI argument
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Add `baseBranch` parameter to `execute()` and CLI `main()`
   - Remove `getBaseBranch()` method that reads `cat-base`
   - Use the passed branch name for `git fetch origin <branch>` and merge operations
   - Still read `cat-base` for fork-point commit in diff/verification operations
   - Update tests

3. **Step 3:** Update `work-merge` skill to pass base branch to `merge-and-cleanup`
   - Files: `plugin/skills/work-merge/first-use.md`
   - Add `${BASE_BRANCH}` argument to the `merge-and-cleanup` invocation

4. **Step 4:** Update skill files to treat `cat-base` as commit hash
   - Files: `stakeholder-review/first-use.md`, `work-with-issue/first-use.md`, `git-squash/first-use.md`
   - Replace pin-once patterns (`BASE=$(git rev-parse $(cat cat-base))`) with direct read (`BASE=$(cat cat-base)`)
   - Remove redundant `git merge-base` computations where `cat-base` now provides the fork commit directly

5. **Step 5:** Update worktree isolation tests
   - Files: `tests/worktree-isolation.bats`
   - Add test: `cat-base` contains a commit hash, not a branch name
   - Add test: `cat-base` value doesn't change when base branch advances

6. **Step 6:** Run full test suite
   - `mvn -f client/pom.xml test`
   - `bats tests/worktree-isolation.bats`

## Post-conditions

- [ ] `WorkPrepare` writes the fork-point commit hash to `cat-base`
- [ ] All diff/verification operations use `cat-base` directly as a commit hash
- [ ] `MergeAndCleanup` receives the base branch name as a CLI argument for `git fetch`
- [ ] Per-skill pin-once patterns simplified (no `git rev-parse` needed — `cat-base` IS the pinned commit)
- [ ] All existing tests pass with no regressions
- [ ] Bats tests verify `cat-base` contains a commit hash and is immutable
