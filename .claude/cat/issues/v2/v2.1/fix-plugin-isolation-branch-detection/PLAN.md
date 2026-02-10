# Plan: fix-plugin-isolation-branch-detection

## Problem
`EnforcePluginFileIsolation` hook calls `GitCommands.getCurrentBranch()` without a directory parameter (line 55),
causing it to check the branch of the current working directory (`/workspace`, typically on `v2.1`) instead of the
git repository containing the file being edited. When editing plugin files in a worktree (e.g.,
`/workspace/.claude/cat/worktrees/2.1-hook-sh-fix/plugin/hooks/hook.sh`), the hook incorrectly detects the branch
as `v2.1` (protected) and blocks the edit, even though the file is on a task branch (`2.1-hook-sh-fix`).

## Root Cause
Line 55 of `EnforcePluginFileIsolation.java`:
```java
String branch = GitCommands.getCurrentBranch();
```
This calls the no-argument variant which defaults to the current working directory. The overload
`GitCommands.getCurrentBranch(String directory)` exists but is not used.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must extract directory correctly from file_path; edge case if file_path is empty (already handled)
- **Mitigation:** File path validation already exists at line 49; `Paths.get(filePath).getParent()` is safe

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java` - Pass file's parent
  directory to `getCurrentBranch(directory)` instead of calling the no-arg variant

## Acceptance Criteria
- [ ] Hook uses `getCurrentBranch(directory)` where directory is derived from the file being edited
- [ ] Plugin file edits in worktrees are allowed (worktree branch is not protected)
- [ ] Plugin file edits on actual protected branches (v2.1, main) are still blocked
- [ ] `mvn -f hooks/pom.xml verify` passes

## Execution Steps
1. **Read the current hook** at `hooks/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java`
2. **Fix branch detection:** Change line 55 from `GitCommands.getCurrentBranch()` to
   `GitCommands.getCurrentBranch(Paths.get(filePath).getParent().toString())`
3. **Run `mvn -f hooks/pom.xml verify`** to ensure the fix compiles and tests pass

## Success Criteria
- [ ] `mvn -f hooks/pom.xml verify` exits 0
- [ ] Hook correctly resolves worktree branch from file path
