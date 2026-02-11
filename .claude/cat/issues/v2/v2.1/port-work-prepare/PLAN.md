# Plan: port-work-prepare

## Current State
`work-prepare.py` (799 lines) orchestrates the full preparation phase for `/cat:work`, calling
`get-available-issues.sh`, `check-existing-work.sh`, and `issue-lock.sh` to find a task, create
a worktree, and prepare for execution.

## Target State
Java equivalent in the hooks module that produces identical JSON output.

## Satisfies
Parent: 2.1-port-workflow-scripts (sub-issue 4 of 4)

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** This is the top-level orchestrator - errors cascade to all workflow operations
- **Mitigation:** Integration test with real issue directories; verify JSON output matches exactly

## Scripts to Port
- `plugin/scripts/work-prepare.py` (799 lines) - Full preparation orchestration
  - Argument parsing (--session-id, --project-dir, --trust-level, --issue-id, --exclude-pattern)
  - Calls get-available-issues.sh → becomes IssueDiscovery.java call
  - Calls check-existing-work.sh → becomes ExistingWorkChecker.java call
  - Calls issue-lock.sh → becomes IssueLock.java call
  - Worktree creation and branch management
  - Potentially-complete detection (suspicious commits analysis)
  - Token estimation from PLAN.md
  - Returns comprehensive JSON with status, issue details, worktree path, diagnostics

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- Test files

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - if new exports needed

## Execution Steps
1. Read `work-prepare.py` thoroughly - understand full orchestration flow
2. Map Python subprocess calls to Java method calls on ported classes
3. Implement `WorkPrepare.java` integrating IssueLock, ExistingWorkChecker, IssueDiscovery
4. Implement worktree creation using GitCommands
5. Implement potentially-complete detection and token estimation
6. Write integration tests verifying JSON output contracts
7. Run `mvn -f hooks/pom.xml verify`
8. Update STATE.md (status: closed, progress: 100%)

## Success Criteria
- [ ] WorkPrepare produces identical JSON output for all status codes (READY, NO_TASKS, LOCKED, OVERSIZED, ERROR)
- [ ] Worktree creation and branch management works correctly
- [ ] Potentially-complete detection matches Python implementation
- [ ] Token estimation from PLAN.md matches Python implementation
- [ ] Integration with IssueLock, ExistingWorkChecker, IssueDiscovery works
- [ ] All tests pass (`mvn -f hooks/pom.xml verify`)
