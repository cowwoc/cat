<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: Record Fork Commit at Worktree Creation

## Goal

Change `cat-base` from storing a branch name to storing the fork-point commit hash. All downstream git operations
(diff, checkout, rebase, squash verification) use this immutable commit instead of re-resolving a branch name that may
have advanced. Remove race-condition mitigations that become redundant.

## Satisfies

None (infrastructure improvement)

## Background

`cat-base` currently stores a branch name (e.g., `v2.1`). Every downstream skill that needs to compare against the fork
point must either:
1. Resolve the branch name to a commit — which may return a DIFFERENT commit if the branch advanced
2. Compute `git merge-base <branch> HEAD` — which can also shift if the branch advanced

This caused a real bug: `git checkout v2.1 -- <path>` imported files added to v2.1 after the worktree was created,
contaminating the squashed commit tree.

Recording the fork commit once at worktree creation eliminates the entire class of race conditions, making several
existing mitigations redundant.

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

## Audit: Race-Condition Mitigations (Remove or Simplify)

With `cat-base` storing an immutable fork-point commit, the following mitigations become redundant:

### Remove

| Component | Location | Current purpose | Why redundant |
|-----------|----------|-----------------|---------------|
| Patch-diff verification | `GitRebaseSafe.java:156-192` | Computes old-base via `merge-base backup base`, compares patches before/after rebase to detect false positives from base advancement | With immutable fork commit, simple `git diff --quiet backup HEAD` suffices — base can't advance |
| Pin-once guidance | `git-squash/first-use.md:306-318` | "MANDATORY: Pin base branch reference before rebase" | `cat-base` IS the pinned commit — no per-skill pinning needed |
| `git merge-base` calls in skills | Various skill files | Compute fork point at runtime | `cat-base` provides the fork point directly |
| "Never Restore Files Using checkout" rule | `git-squash/first-use.md` | Warns against `git checkout <base-branch> -- <path>` | `cat-base` is a commit hash, not a branch name — checkout with it is safe |
| "Use Merge-Base Diff" rule | `git-squash/first-use.md` | Warns against `git diff <base-branch>..HEAD` | `git diff $(cat cat-base)..HEAD` is correct because `cat-base` is the fork commit |

### Keep (orthogonal to base reference format)

| Component | Location | Why keep |
|-----------|----------|----------|
| Commit-tree approach | `GitSquash.java:115-191` | Prevents working directory contamination — unrelated to base reference |
| Reset-soft blocker | `RemindGitSquash.java:22-54` | Blocks dangerous anti-pattern — unrelated to base reference |
| Backup-verify-cleanup | `GitSquash.java`, skill docs | General safety pattern, not race-condition specific |
| Post-squash working tree check | `git-squash/first-use.md:162-176` | Verifies working tree matches HEAD — always needed |

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Change `WorkPrepare.java` to write commit hash
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
   - Replace `Files.writeString(catBaseFile, baseBranch)` with `Files.writeString(catBaseFile, commitHash)` where
     `commitHash = GitCommands.runGit(projectDir, "rev-parse", "HEAD")`
   - Update tests

2. **Step 2:** Simplify `GitRebaseSafe.java` verification
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java`
   - Remove patch-diff comparison logic (lines 156-192)
   - Replace with simple `git diff --quiet backup HEAD` tree-state comparison
   - The old patch-diff approach was needed because base could advance; with immutable fork commit this is unnecessary
   - Update tests

3. **Step 3:** Update `MergeAndCleanup` to accept base branch as CLI argument
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Add `baseBranch` parameter to `execute()` and CLI `main()`
   - Remove `getBaseBranch()` method that reads `cat-base`
   - Use the passed branch name for `git fetch origin <branch>` and merge operations
   - Still read `cat-base` for fork-point commit in diff/verification operations
   - Update tests

4. **Step 4:** Update `work-merge` skill to pass base branch to `merge-and-cleanup`
   - Files: `plugin/skills/work-merge/first-use.md`
   - Add `${BASE_BRANCH}` argument to the `merge-and-cleanup` invocation

5. **Step 5:** Simplify skill files — remove redundant race-condition mitigations
   - Files: `git-squash/first-use.md`, `stakeholder-review/first-use.md`, `work-with-issue/first-use.md`
   - Remove "Pin Base Branch Reference" critical rule section
   - Remove "Never Restore Files Using checkout" section (checkout with commit hash is safe)
   - Remove "Use Merge-Base Diff" section (diff with `cat-base` commit hash is already correct)
   - Replace pin-once patterns (`BASE=$(git rev-parse $(cat cat-base))`) with direct read (`BASE=$(cat cat-base)`)
   - Remove redundant `git merge-base` computations

6. **Step 6:** Update worktree isolation tests
   - Files: `tests/worktree-isolation.bats`
   - Add test: `cat-base` contains a commit hash, not a branch name
   - Add test: `cat-base` value doesn't change when base branch advances
   - Remove or simplify tests that documented the now-impossible branch-name race condition

7. **Step 7:** Run full test suite
   - `mvn -f client/pom.xml test`
   - `bats tests/worktree-isolation.bats`

8. **Fix Step 8:** Fix `work-prepare/first-use.md` to write commit hash instead of branch name
   - Files: `plugin/skills/work-prepare/first-use.md`
   - Criterion: "All diff/verification operations use `cat-base` directly as a commit hash"
   - At line 273 the script writes the branch name to `cat-base`; replace with the output of `git rev-parse HEAD` so
     the fork-point commit hash is written instead
   - Verify no other location in the skill file writes a branch name to `cat-base`

9. **Fix Step 9:** Remove `rev-parse` pin call from `GitRebaseSafe.java` Step 2
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java`
   - Criterion: "Per-skill pin-once patterns removed (`cat-base` IS the pinned commit)"
   - Step 2 contains a `git rev-parse` call with a "Pin target ref to prevent race conditions" comment; remove this
     call and its comment because `cat-base` already contains the immutable fork-point commit hash
   - Update tests if the removed call was exercised by a test

10. **Fix Step 10:** Remove race-condition pin comments from `git-squash/first-use.md`
    - Files: `plugin/skills/git-squash/first-use.md`
    - Criterion: "Race-condition documentation sections removed from `git-squash/first-use.md`"
    - Lines 236 and 272 contain race-condition pin comments; remove those comments and any surrounding pin-once
      guidance sections (e.g., "MANDATORY: Pin base branch reference before rebase")
    - Verify no remaining race-condition mitigation text references pinning a mutable branch name

11. **Fix Step 11:** Create `tests/worktree-isolation.bats` with `cat-base` hash verification tests
    - Files: `tests/worktree-isolation.bats`
    - Criterion: "Bats tests verify `cat-base` contains a commit hash and is immutable"
    - Create the Bats test file if it does not exist
    - Add test: `cat-base` file content matches a 40-character hex commit hash (not a branch name)
    - Add test: `cat-base` value is unchanged after the base branch advances by one commit
    - Follow test isolation rules: use temporary git repos, never operate against the real project repo

## Post-conditions

- [ ] `WorkPrepare` writes the fork-point commit hash to `cat-base`
- [ ] All diff/verification operations use `cat-base` directly as a commit hash
- [ ] `MergeAndCleanup` receives the base branch name as a CLI argument for `git fetch`
- [ ] `GitRebaseSafe` uses simple tree-state comparison instead of patch-diff
- [ ] Per-skill pin-once patterns removed (`cat-base` IS the pinned commit)
- [ ] Race-condition documentation sections removed from `git-squash/first-use.md`
- [ ] All existing tests pass with no regressions
- [ ] Bats tests verify `cat-base` contains a commit hash and is immutable
