# Plan: fix-merge-dirty-worktree-check

## Problem

`MergeAndCleanup.execute()` does not verify the main workspace working tree is clean before calling
`fastForwardMerge()`. When the main workspace has pre-existing uncommitted changes to files the merge commit
also touches, `git merge --ff-only` can advance HEAD while leaving the working tree and index in a stale
pre-merge state, requiring manual recovery.

## Parent Requirements

None (infrastructure bugfix, recurrence of fix-merge-working-tree-sync)

## Research Findings

`MergeAndCleanup.java` line 110 calls `fastForwardMerge(projectPath, taskBranch)` → `mergeWithRetry()` using
`git merge --ff-only`. The Javadoc claims it "fails fast with a clear error if uncommitted changes would be
overwritten by the merge", but this is incorrect: `git merge --ff-only` only aborts if working tree changes
*conflict* with the merge (same hunk, different content). When the dirty file's content does not directly
conflict (e.g., a different line in the same file, or a file that the merge adds lines to without touching
the dirty region), git succeeds at advancing HEAD while the working tree retains the old content.

A method `hasUncommittedChanges(String worktreePath)` already exists at line 168 using `git status --porcelain`.
The fix mirrors that pattern for `projectPath` (main workspace) before the merge.

The issue `fix-merge-working-tree-sync` (closed) addressed `git push . HEAD:baseBranch` (pointer-only, no
working tree update). The current code correctly uses `git merge --ff-only`, but that fix did not add the
pre-merge dirty check. This issue adds the missing check.

## Reproduction Code

```java
// In execute(): projectPath has M .cat/retrospectives/index.json (uncommitted)
// The merge commit also modifies this file (adds a new field)
// git merge --ff-only succeeds — no conflict on the modified lines
// After merge: HEAD = new commit, working tree = old content
// git status --porcelain shows M  .cat/retrospectives/index.json (staged) + other files as D
fastForwardMerge(projectPath, taskBranch);
// Result: working tree inconsistent, requires git reset --hard HEAD to recover
```

## Expected vs Actual

- **Expected:** `execute()` throws `IOException` with message listing dirty files when `git status --porcelain`
  is non-empty in `projectPath` before the merge
- **Actual:** `execute()` calls `git merge --ff-only` unconditionally; merge succeeds but leaves working tree
  stale

## Root Cause

Missing pre-merge dirty check in `MergeAndCleanup.execute()`. The worktree dirty check
(`hasUncommittedChanges()`) is only called for the issue worktree (line 168), not for the main workspace
(`projectPath`) before `fastForwardMerge()`.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Any call site that invokes `execute()` with a dirty `projectPath` will now fail where
  it previously silently succeeded. This is the desired behavior, but callers (e.g., `work-merge-agent`) must
  ensure the workspace is clean before invoking.
- **Mitigation:** The new test explicitly covers both the dirty-fail and clean-success paths. Existing tests
  all use clean worktrees and are unaffected.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` — add
  `verifyMainWorkspaceClean(projectPath)` private method; call it at the start of `execute()` before
  `syncTargetBranchWithOrigin()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java` — add two test methods:
  one for dirty-workspace rejection, one confirming clean-workspace still succeeds

## Test Cases

- [ ] `execute()` throws `IOException` when `projectPath` has uncommitted changes (dirty working tree)
- [ ] Error message lists the dirty files returned by `git status --porcelain`
- [ ] `execute()` succeeds when `projectPath` is clean
- [ ] All existing `MergeAndCleanupTest` tests still pass

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Add `verifyMainWorkspaceClean` to `MergeAndCleanup`**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
   - Add private method:
     ```java
     /**
      * Verifies the main workspace working tree is clean before merge.
      *
      * @param projectPath the project root directory (main worktree)
      * @throws IOException if the working tree has uncommitted changes, with message listing dirty files
      */
     private void verifyMainWorkspaceClean(String projectPath) throws IOException
     {
       String dirty = runGit(Path.of(projectPath), "status", "--porcelain");
       if (!dirty.isEmpty())
       {
         throw new IOException(
           "Cannot merge: main workspace has uncommitted changes in '" + projectPath + "'.\n" +
           "Commit or discard these changes before merging:\n" + dirty);
       }
     }
     ```
   - Call `verifyMainWorkspaceClean(projectPath)` at the start of `execute()`, before line 97
     (`syncTargetBranchWithOrigin()`). Insert after the `.cat` directory check (after line 81).

2. **Add regression tests to `MergeAndCleanupTest`**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java`
   - Test 1 — dirty workspace is rejected:
     ```java
     /**
      * Verifies that execute throws IOException when projectPath has uncommitted changes.
      *
      * @throws IOException if an I/O error occurs
      */
     @Test(expectedExceptions = IOException.class,
       expectedExceptionsMessageRegExp = "(?s).*uncommitted changes.*dirty-file\\.txt.*")
     public void executeThrowsWhenMainWorkspaceIsDirty() throws IOException
     {
       Path originRepo = Files.createTempDirectory("origin-repo-");
       Path mainRepo = Files.createTempDirectory("main-repo-");
       Path worktreesDir = Files.createTempDirectory("worktrees-");
       Path pluginRoot = Files.createTempDirectory("test-plugin");
       try
       {
         // Initialize bare origin
         TestUtils.runGit(originRepo, "init", "--bare", "--initial-branch=v2.1");

         // Create main repo with initial commit
         TestUtils.runGit(mainRepo, "init", "--initial-branch=v2.1");
         TestUtils.runGit(mainRepo, "config", "user.email", "test@example.com");
         TestUtils.runGit(mainRepo, "config", "user.name", "Test User");
         Files.writeString(mainRepo.resolve("README.md"), "initial");
         TestUtils.runGit(mainRepo, "add", "README.md");
         TestUtils.runGit(mainRepo, "commit", "-m", "Initial commit");

         // Add origin remote and push
         TestUtils.runGit(mainRepo, "remote", "add", "origin", originRepo.toString());
         TestUtils.runGit(mainRepo, "push", "-u", "origin", "v2.1");

         // Create the issue branch via worktree
         String issueBranch = "dirty-workspace-issue";
         Path issueWorktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueBranch);
         TestUtils.runGit(issueWorktree, "config", "user.email", "test@example.com");
         TestUtils.runGit(issueWorktree, "config", "user.name", "Test User");
         Files.writeString(issueWorktree.resolve("issue-work.txt"), "issue work");
         TestUtils.runGit(issueWorktree, "add", "issue-work.txt");
         TestUtils.runGit(issueWorktree, "commit", "-m", "Issue commit");

         // Set up .cat structure in main repo
         Files.createDirectories(mainRepo.resolve(".cat"));

         // Introduce an uncommitted change in the main workspace to make it dirty
         Files.writeString(mainRepo.resolve("dirty-file.txt"), "uncommitted content");

         try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
         {
           MergeAndCleanup cmd = new MergeAndCleanup(scope);
           cmd.execute(mainRepo.toString(), issueBranch, "test-session", "v2.1",
             issueWorktree.toString(), pluginRoot.toString());
         }
       }
       finally
       {
         TestUtils.deleteDirectoryRecursively(worktreesDir);
         TestUtils.deleteDirectoryRecursively(mainRepo);
         TestUtils.deleteDirectoryRecursively(originRepo);
         TestUtils.deleteDirectoryRecursively(pluginRoot);
       }
     }
     ```
     Note: `dirty-file.txt` is written to `mainRepo` but never staged — it appears in
     `git status --porcelain` as `?? dirty-file.txt` (untracked), which is non-empty, triggering
     the check.
   - Test 2 — clean workspace succeeds:
     The existing test `executeSyncsMainWorkingTree` already calls `execute()` on a clean
     `mainRepo`. No new test is needed. This test verifies the clean-success path by passing
     when `projectPath` has no uncommitted changes.

3. **Run tests**
   - `mvn -f client/pom.xml test`

## Post-conditions

- [ ] `MergeAndCleanup.verifyMainWorkspaceClean()` method exists and is called before `fastForwardMerge()`
- [ ] `MergeAndCleanupTest` contains a test that verifies `execute()` throws `IOException` when
  `projectPath` is dirty, with message listing the dirty files
- [ ] All existing `MergeAndCleanupTest` tests pass
- [ ] `mvn -f client/pom.xml test` exits 0
- [ ] E2E: manually dirty the main workspace with an uncommitted file, invoke merge, confirm the error
  message is displayed rather than a silent inconsistent state
