# Plan: fix-warn-base-branch-edit-branch-detection

## Problem
`WarnBaseBranchEdit.check()` calls `GitCommands.getCurrentBranch()` (no-arg) which checks the process working
directory's branch, not the branch for the file being edited. This causes two problems:
1. **Incorrect in worktree contexts:** When running in a worktree, `getCurrentBranch()` returns the worktree branch, not
   the branch of the repo containing the file being edited
2. **Untestable:** The test `warnBaseBranchEditWarnsOnBaseBranch` depends on the real branch state and must use
   `SkipException` when not on a base branch

## Root Cause
Same design flaw that was fixed in `EnforcePluginFileIsolation` (issue `fix-plugin-isolation-branch-detection`):
hardcoded `getCurrentBranch()` instead of deriving branch from the file path.

## Satisfies
None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Other callers of `WarnBaseBranchEdit` may depend on current behavior
- **Mitigation:** The handler is only called by the hook system with file_path in toolInput

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` - Use
  `findExistingAncestor(filePath)` + `getCurrentBranch(directory)` pattern
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java` - Replace `SkipException` workaround
  with temp git repo approach (same pattern as `EnforcePluginFileIsolationTest`)

## Acceptance Criteria
- [ ] `WarnBaseBranchEdit.check()` derives branch from `file_path` input, not process working directory
- [ ] `warnBaseBranchEditWarnsOnBaseBranch` test uses temp git repo, no `SkipException`
- [ ] All existing tests pass (433+)
- [ ] PMD/Checkstyle clean

## Execution Steps
1. **Update WarnBaseBranchEdit.java:** Replace `GitCommands.getCurrentBranch()` with
   `findExistingAncestor(filePath)` + `GitCommands.getCurrentBranch(directory)` pattern
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`
2. **Fix test:** Replace `SkipException` in `warnBaseBranchEditWarnsOnBaseBranch` with temp git repo on `main` branch
   - Files: `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java`
3. **Run tests and verify**
   - Command: `mvn -f hooks/pom.xml test`
