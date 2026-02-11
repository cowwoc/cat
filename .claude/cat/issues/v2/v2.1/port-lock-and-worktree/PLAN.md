# Plan: port-lock-and-worktree

## Current State
`issue-lock.sh` (388 lines) provides file-based locking with session tracking, stale lock detection,
and atomic operations. `check-existing-work.sh` (86 lines) checks for in-progress worktrees with
existing commits.

## Target State
Java equivalents in `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/` that produce identical
JSON output.

## Satisfies
Parent: 2.1-port-workflow-scripts (sub-issue 1 of 4)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Lock behavior must be identical - wrong locking causes concurrent task corruption
- **Mitigation:** Test lock acquire/release/check/force-release/list; verify JSON output contracts

## Scripts to Port
- `plugin/scripts/issue-lock.sh` (388 lines) - File-based issue locking
  - Commands: acquire, update, release, force-release, check, list
  - Lock file format: JSON with session_id, created_at, worktree, created_iso
  - Atomic file operations using bash temp file + mv
- `plugin/scripts/check-existing-work.sh` (86 lines) - Worktree existing commit check
  - Compares worktree HEAD against base branch
  - Output: JSON with has_existing_work, existing_commits, commit_summary

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/ExistingWorkChecker.java`
- Test files for both classes

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - if new exports needed

## Execution Steps
1. Read `issue-lock.sh` thoroughly - understand all 6 commands and JSON output format
2. Read `check-existing-work.sh` - understand worktree commit detection logic
3. Implement `IssueLock.java` with all commands (acquire, update, release, force-release, check, list)
4. Implement `ExistingWorkChecker.java` with worktree commit comparison
5. Write tests for both classes verifying JSON output contracts
6. Run `mvn -f hooks/pom.xml verify`
7. Update STATE.md (status: closed, progress: 100%)

## Success Criteria
- [ ] IssueLock produces identical JSON output for all 6 commands
- [ ] ExistingWorkChecker produces identical JSON output
- [ ] Lock file format preserved (JSON with session_id, created_at, worktree, created_iso)
- [ ] Atomic file operations maintained (temp file + rename)
- [ ] All tests pass (`mvn -f hooks/pom.xml verify`)
