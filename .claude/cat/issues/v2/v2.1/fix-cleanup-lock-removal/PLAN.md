# Plan: fix-cleanup-lock-removal

## Problem

The cleanup skill cannot remove stale lock files because the `BlockLockManipulation` hook blocks all `rm` commands targeting `.claude/cat/locks/`. The hook error message only suggests running `/cat:cleanup` or choosing a different task, but does not mention the authorized `issue-lock.sh force-release` command that the cleanup skill should use. This causes agents executing the cleanup skill to attempt direct `rm` commands, which get blocked, creating a circular failure.

## Satisfies

None

## Expected vs Actual

- **Expected:** The cleanup skill removes stale locks using the authorized `issue-lock.sh force-release` mechanism, and the hook error message guides agents to the correct command when direct `rm` is attempted.
- **Actual:** The hook blocks lock removal with an error message that does not mention the authorized alternative, leading agents to get stuck.

## Root Cause

The `BlockLockManipulation` hook error message does not include information about the authorized `issue-lock.sh force-release` command. When an agent tries direct `rm` inside the cleanup skill (instead of following Step 5 which uses `issue-lock.sh force-release`), the error message provides no path forward.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Error message changes could affect other consumers of the hook output
- **Mitigation:** Test both error message branches

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockLockManipulation.java` - Update both error messages (LOCK_FILE_PATTERN and LOCKS_DIR_PATTERN branches) to include the authorized `issue-lock.sh force-release` command
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockLockManipulationTest.java` - Add/update tests verifying both error messages contain the authorized alternative

## Test Cases

- [ ] LOCK_FILE_PATTERN error message contains `issue-lock.sh force-release` reference
- [ ] LOCKS_DIR_PATTERN error message contains `issue-lock.sh force-release` reference
- [ ] Hook still blocks direct `rm` commands targeting lock files
- [ ] Hook still blocks direct `rm` commands targeting locks directory

## Acceptance Criteria

- [ ] Both error message branches in BlockLockManipulation updated to mention `issue-lock.sh force-release`
- [ ] Error message distinguishes when to use `/cat:cleanup` (user-facing) vs `issue-lock.sh force-release` (skill-internal)
- [ ] Tests verify error message content for both LOCK_FILE_PATTERN and LOCKS_DIR_PATTERN code paths
- [ ] Hook still blocks direct `rm` commands (no regression in blocking behavior)
- [ ] All existing tests pass

## Execution Steps

1. **Update BlockLockManipulation.java error messages:** In the first `Result.block()` call (LOCK_FILE_PATTERN match), add a section about the authorized alternative for skill-internal use. In the second `Result.block()` call (LOCKS_DIR_PATTERN match), add the same reference.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockLockManipulation.java`

2. **Add/update tests:** Create or update `BlockLockManipulationTest.java` with test methods that verify both error branches contain the `issue-lock.sh` reference.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockLockManipulationTest.java`

3. **Run tests:** Execute `mvn -f client/pom.xml verify` to ensure all tests pass.

## Success Criteria

- [ ] Both error messages mention `issue-lock.sh force-release` as the authorized alternative
- [ ] Test suite passes with exit code 0
- [ ] No regressions in existing BlockLockManipulation blocking behavior