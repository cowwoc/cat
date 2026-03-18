# Plan: expand-worktree-commit-detection-hook

## Goal
Expand BashHandler hook coverage to detect and warn when a `git commit` is made targeting the main
workspace while an active issue worktree exists. Addresses A003/PATTERN-003 (path_config_assumption),
escalated after documentation-level and Edit/Write-isolation prevention proved insufficient — agents
still route commits to the main workspace from skill code (e.g., learn skill phase-record) instead of
the active worktree.

## Background

PATTERN-003 has 3 occurrences before fix, 5 after — documentation-level prevention is counter-productive.
`WarnBaseBranchEdit.java` covers `Edit`/`Write` tool isolation (warns when absolute `/workspace/` paths
are used from a worktree), but does NOT cover `git commit` commands. Commit routing to the main workspace
bypasses worktree isolation and pollutes the main branch with issue-specific changes.

The ESCALATE-A003 escalation proposes: "Expand hook coverage to detect commits to main workspace while
active worktree exists."

Detection strategy: in `PreToolUse:Bash`, when a `git commit` command is detected and the current working
directory is NOT inside a CAT worktree but an active worktree lock exists for the current session, emit
a warning instructing the agent to route the commit to the active worktree instead.

## Satisfies

None — escalation A003 from retrospective (PATTERN-003: path_config_assumption)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** False positives for legitimate main-workspace commits (e.g., planning commits, config
  commits not related to an active issue)
- **Mitigation:** Warning-only (not blocking); check worktree lock presence to scope detection to sessions
  with active issue work; include clear guidance in warning message

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnMainWorkspaceCommit.java` — new PreToolUse
  Bash handler; detects `git commit` in main workspace when session has an active worktree lock, and
  warns with routing guidance
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnMainWorkspaceCommitTest.java` — tests for
  detection, false-positive suppression, and warning content
- `client/src/main/java/io/github/cowwoc/cat/hooks/BashHandler.java` — register the new handler in the
  pre-tool Bash handler list

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **Read BashHandler.java and WarnBaseBranchEdit.java:** Understand handler registration and worktree
  detection patterns
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/BashHandler.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`
- **Read WorktreeContext.java and IssueLock.java:** Understand how to detect active worktree locks for
  the current session
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/WorktreeContext.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`
- **Implement WarnMainWorkspaceCommit.java:** Warning handler that checks for git commit in main
  workspace when an active worktree lock exists
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnMainWorkspaceCommit.java`
- **Write tests:** Cover true-positive (commit in main with active worktree), false-positive suppression
  (commit in worktree, commit in main with no active worktree), and warning message content
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnMainWorkspaceCommitTest.java`
- **Register handler:** Add to BashHandler pre-tool handler list
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/BashHandler.java`
- **Run tests:** `mvn -f client/pom.xml test`
- **Commit**

## Post-conditions

- [ ] `WarnMainWorkspaceCommit.java` exists and is registered in `BashHandler.java`
- [ ] Handler emits a warning when `git commit` is issued in the main workspace while an active
  worktree lock exists for the current session
- [ ] Handler does NOT warn when committing from inside an issue worktree
- [ ] Handler does NOT warn when no active worktree lock exists
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
