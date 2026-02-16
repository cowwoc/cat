# Plan: fix-block-unsafe-removal-cwd-detection

## Problem
`BlockUnsafeRemoval.java` has two bugs that allowed M506 (shell breakage from `git worktree remove` while CWD was
inside the target) to pass undetected:

1. **CWD detection uses JVM process CWD, not shell CWD:** `extractCwd()` (line 187) calls
   `System.getProperty("user.dir")` which returns the JVM's working directory (typically `/workspace`), not the shell's
   actual CWD from prior Bash tool calls. The `CD_PATTERN` only catches inline `cd` within the same command string.

2. **Regex doesn't handle flags:** `WORKTREE_REMOVE_PATTERN` (line 30) captures the first token after `remove` as the
   path. When `--force` is used (`git worktree remove --force <path>`), it captures `--force` instead of the actual
   worktree path.

## Satisfies
None (bug fix for safety hook)

## Reproduction Code
```bash
# Bug 1: Shell cd's into worktree in prior Bash call, then removes it in a new call
# Call 1:
cd /workspace/.claude/cat/worktrees/some-task
# Call 2 (separate Bash tool invocation):
git worktree remove --force /workspace/.claude/cat/worktrees/some-task
# Hook sees System.getProperty("user.dir") = /workspace, not the shell's actual CWD
# /workspace is NOT inside /workspace/.claude/cat/worktrees/some-task → allowed

# Bug 2: --force captured as path
echo "git worktree remove --force /workspace/.claude/cat/worktrees/foo" | grep -oP 'git\s+worktree\s+remove\s+(\S+)'
# Captures: --force (not the actual path)
```

## Expected vs Actual
- **Expected:** Hook blocks `git worktree remove` when shell CWD is inside target worktree
- **Actual:** Hook allows the command because it reads JVM CWD (`/workspace`) which is not inside the target, and/or
  captures `--force` as the target path

## Root Cause
1. PreToolUse hooks receive the command string but not the shell's current working directory from prior Bash calls.
   `System.getProperty("user.dir")` is the JVM CWD, which never changes.
2. The regex pattern doesn't account for git command flags before the positional argument.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could block legitimate worktree removals if CWD detection is too aggressive
- **Mitigation:** Add test cases for both bugs; verify existing tests still pass

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` - Fix regex to skip flags; investigate
  toolInput JSON for shell CWD context
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` - Add test cases for both bugs (TDD
  approach: write failing tests first)

## Test Cases
- [ ] `git worktree remove --force <path>` correctly extracts `<path>` not `--force`
- [ ] `git worktree remove -f <path>` correctly extracts `<path>` not `-f`
- [ ] CWD detection uses shell CWD from tool input if available, not `System.getProperty("user.dir")`
- [ ] Existing test cases still pass

## Execution Steps
1. **Investigate toolInput JSON:** Examine what Claude Code passes in the Bash tool input JSON — check if there's a `cwd`
   or `working_directory` field that provides the shell's actual CWD
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`,
     `client/src/main/java/io/github/cowwoc/cat/hooks/BashHandler.java`
2. **Write failing tests for Bug 2 (regex):** Add test cases where `--force` or `-f` appears before the path
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`
3. **Fix Bug 2 (regex):** Update `WORKTREE_REMOVE_PATTERN` to skip flag arguments (`-*`) and capture the first
   non-flag token as the path
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`
4. **Write failing tests for Bug 1 (CWD):** Add test cases that simulate shell CWD being inside the target directory
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java`
5. **Fix Bug 1 (CWD):** Update `extractCwd()` to read shell CWD from toolInput JSON if available, falling back to
   `System.getProperty("user.dir")` only as last resort
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`
6. **Run all tests:** `mvn -f client/pom.xml test` to verify fixes and no regressions

## Success Criteria
- [ ] All new test cases pass
- [ ] `git worktree remove --force <path>` correctly identifies `<path>` as the target
- [ ] CWD detection uses the best available source for shell CWD
- [ ] No regressions in existing BlockUnsafeRemoval tests
- [ ] `mvn -f client/pom.xml test` passes

## Commit Type
bugfix
