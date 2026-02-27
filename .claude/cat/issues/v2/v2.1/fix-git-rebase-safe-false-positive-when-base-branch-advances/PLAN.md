# Plan: Fix git-rebase-safe false positive when base branch advances

## Problem

GitRebaseSafe.java Step 5 verification produces a false content-changed error when the base branch (e.g., v2.1)
receives new commits between worktree creation and the time git-rebase is invoked. The verification compares full
tree state (`git diff --quiet backup`) against a backup taken before rebase. When the base branch has advanced,
the post-rebase tree includes new base commits not present in the backup, causing a false positive even though the
issue branch content is intact.

## Satisfies

None

## Reproduction Code

```java
// 1. Create base branch "main" with initial commit
// 2. Create feature branch with one commit (worktree created at this point)
// 3. Add a new commit to "main" (base advances)
// 4. Run GitRebaseSafe.execute("main")
// Step 5 fires: git diff --quiet backup  -> exits 1 (false positive)
// Expected: OK status (issue branch content unchanged)
// Actual:   ERROR status "Content changed during rebase"
```

## Expected vs Actual

- **Expected:** When base branch advances and issue branch content is unchanged, `execute()` returns OK
- **Actual:** `execute()` returns ERROR "Content changed during rebase - backup preserved for investigation"
  because `git diff --quiet backup` sees the new base commits' file changes as content changes

## Root Cause

Step 5 uses tree-state comparison: `git diff --quiet backup`. This compares the full working tree of
post-rebase HEAD against the backup (pre-rebase HEAD). When the base branch has advanced:

- `base` = pinned NEW head of v2.1 (includes recently-added commits)
- `backup` = HEAD before rebase = issue branch on top of OLD v2.1 HEAD
- After `git rebase base`: HEAD = issue branch on top of NEW v2.1 HEAD
- `git diff --quiet backup` sees the new base commits' changes = false positive

The fix is patch-diff comparison: compare issue branch content relative to its base, not absolute tree states.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** The new verification could miss actual content changes if the patch-diff logic is wrong
- **Mitigation:** Add both a false-positive prevention test AND a true-positive detection test

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebaseSafe.java` - Replace Step 5 tree-state
  comparison with patch-diff comparison using `git diff base backup` vs `git diff base HEAD`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseSafeTest.java` - Add two new test methods

## Test Cases

- [ ] Original bug scenario: base advances after worktree creation, rebase returns OK (no false positive)
- [ ] Content change detection: actual content modification is still caught as ERROR

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1 (TDD - write failing tests first):** Add two test methods to
   `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseSafeTest.java`:

   **`verifyPatchDiffWhenBaseAdvances`:** Create temp git repo with branch "main" and initial commit.
   Create "feature" branch, add "feature.txt", commit. Checkout "main", add "main-advance.txt", commit
   (base advances). Checkout "feature". Run `GitRebaseSafe.execute("main")`. Assert result contains `"OK"`.

   **`verifyDetectsActualContentChanges`:** Create temp git repo with "main". Create "feature" branch, add
   "feature.txt" with "original content", commit. Checkout "main", add "main-advance.txt", commit (base
   advances). Checkout "feature". Before executing GitRebaseSafe, amend the last feature commit to change
   "feature.txt" to "corrupted content". Run `GitRebaseSafe.execute("main")`. Assert result contains `"ERROR"`.
   Note: This simulates a scenario where the issue branch content diverges - after amending, the feature commit
   has different content than what would be produced by a clean rebase of the original commit.

   Run: `mvn -f client/pom.xml test -Dtest=GitRebaseSafeTest` - both new tests should FAIL (red).

2. **Step 2 (Implement fix):** Modify `GitRebaseSafe.java` Step 5 (lines 156-170).

   Replace current tree-state comparison block with patch-diff comparison. The variable `base` is already in
   scope from Step 2 (the pinned commit hash). Both diffs compare relative to the same base point, so new base
   commits are excluded from both sides of the comparison:

   Replace the block starting with the comment "Step 5: Rebase succeeded" through the closing brace of the
   if-block that calls `buildContentChangedErrorJson`, with:

   ```java
   // Step 5: Rebase succeeded - verify no content changes using patch-diff comparison
   // Compare what the issue branch contributes relative to base (ignores new base commits)
   ProcessRunner.Result oldPatchResult = ProcessRunner.run(
     "git", "-C", directory, "diff", base, backup);
   ProcessRunner.Result newPatchResult = ProcessRunner.run(
     "git", "-C", directory, "diff", base, "HEAD");

   String oldPatch = oldPatchResult.exitCode() == 0 ? oldPatchResult.stdout() : "";
   String newPatch = newPatchResult.exitCode() == 0 ? newPatchResult.stdout() : "";

   if (!oldPatch.equals(newPatch))
   {
     // Patch content differs - get stat for error message
     ProcessRunner.Result diffStatResult = ProcessRunner.run(
       "git", "-C", directory, "diff", backup, "--stat");
     String diffStat = "";
     if (diffStatResult.exitCode() == 0)
       diffStat = diffStatResult.stdout().strip();
     return buildContentChangedErrorJson(base, backup, diffStat);
   }
   ```

   Update the class-level Javadoc bullet: change "verify no content changes vs backup" to "verify no content
   changes using patch-diff comparison".

3. **Step 3 (Run all tests):** Run the full test suite:
   ```bash
   mvn -f client/pom.xml test
   ```
   All tests must pass (exit code 0). Both new tests should now pass (green).

## Post-conditions

- [ ] `verifyPatchDiffWhenBaseAdvances` test passes: no false positive when base branch advances
- [ ] `verifyDetectsActualContentChanges` test passes: actual content changes are still detected as ERROR
- [ ] All existing `GitRebaseSafeTest.java` tests pass
- [ ] No regressions: `mvn -f client/pom.xml test` exits with code 0
- [ ] E2E: `verifyPatchDiffWhenBaseAdvances` reproduces the exact bug scenario end-to-end and passes