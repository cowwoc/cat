# Plan

## Goal

Fix the pre-rebase path consistency check (content-references category) to only flag files that were
modified by the issue's commits, not all files differing from the target branch that happen to contain
old path references. The current implementation produces false positives when the target branch has
advanced after the worktree was forked: files that v2.1 updated (e.g., `.cat/issues/` plan.md files
with old skill paths) appear different from the target and contain old paths, but the issue's commits
never touched them. During rebase, git would naturally take v2.1's version of these untouched files,
resolving the old-path references automatically.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: the pre-rebase check no longer blocks rebase when the flagged files were not modified
  by the issue's commits
- [ ] Regression test added: a worktree whose issue commits don't touch any files containing old paths
  rebases successfully, even when the worktree has stale files (from before v2.1 advanced) that contain
  old path references
- [ ] All existing pre-rebase check tests pass (true positives — files the issue DID modify with old
  paths — are still correctly blocked)
- [ ] No regressions: `mvn -f client/pom.xml test` exits with code 0
- [ ] E2E: reproduce the exact false-positive scenario (issue commits touching only Java source, worktree
  has stale plan.md files with old paths) and confirm the rebase proceeds without blocking

## Implementation

### Wave 1: Write failing regression test

**Step 1.** Open `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseTest.java` and read the
existing test structure to understand the test helper patterns used (repo setup, commit helpers, how
`validatePathConsistency` is invoked).

**Step 2.** Add a new test method `validatePathConsistency_doesNotFlagUntouchedFiles` that:

- Sets up an isolated temporary git repo (using the existing helper pattern).
- Creates a merge-base commit that contains:
  - A Java source file `src/Foo.java` with content referencing `old/path`
  - A markdown file `docs/notes.md` with content referencing `old/path`
- Creates a target-branch commit (simulating v2.1 advancing) that updates `docs/notes.md` to reference
  `new/path` (resolving the old reference in that file).
- Creates an issue-branch commit (on top of the merge base) that renames `src/Foo.java` to
  `src/Bar.java` and updates its content to reference `new/path` — but does NOT touch `docs/notes.md`.
- Calls `validatePathConsistency(targetBranch, mergeBase)` (or the equivalent test entry point).
- Asserts that the result contains NO content conflicts — `docs/notes.md` must not appear in
  `contentConflicts` because the issue's commits never touched it.

**Step 3.** Run `mvn -f client/pom.xml test` and confirm the new test FAILS (red) while all existing
tests still pass. This validates the test exercises the real bug.

### Wave 2: Implement the fix

**Step 4.** Open
`client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebase.java` and locate the
`validatePathConsistency(String targetBranch, String mergeBase)` method (approximately line 340).

**Step 5.** After the `git grep -l -- oldPrefix` call that produces `grepResult`, add a call to obtain
the set of files modified by the issue's commits:

```java
ProcessRunner.Result diffResult = runGit("diff", "--name-only", mergeBase, "HEAD");
Set<String> changedByIssue = Set.of(diffResult.stdout().strip().split("\n"));
```

Handle the empty-output edge case: if `diffResult.stdout().isBlank()`, use an empty set rather than
splitting on a blank string (which would yield `[""]`).

**Step 6.** In the loop that builds `contentConflicts`, add a filter so only files present in
`changedByIssue` are added:

```java
// Only flag files that the issue's commits actually modified.
// Untouched files will take the target-branch version during rebase,
// which already has the updated path references.
if (!changedByIssue.contains(file)) {
    continue;
}
```

Place this filter after the existing `renamedOnCurrentBranch` skip check and before adding to
`contentConflicts`, so the ordering of guards remains logical (skip already-renamed → skip
untouched → add conflict).

**Step 7.** Review the change for correctness: true positives are still caught because a file that the
issue's commits modified AND still contains old-path references will appear in BOTH `grepResult` and
`changedByIssue`, so it will still be added to `contentConflicts`.

### Wave 3: Verify

**Step 8.** Run `mvn -f client/pom.xml test`. All tests must pass (exit code 0), including the new
regression test added in Wave 1.
