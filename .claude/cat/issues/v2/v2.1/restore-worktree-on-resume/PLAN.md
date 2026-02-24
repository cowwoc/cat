# Plan: restore-worktree-on-resume

## Problem

When a Claude Code session is resumed via `--resume`, `--continue`, or `/resume`, the working directory is NOT
restored to the worktree that was active during `/cat:work`. The agent resumes in whatever directory the user
launched `claude --resume` from, losing the worktree isolation context.

This means the agent may edit files in the main workspace instead of the worktree, violating worktree isolation.

## Goal

Detect session resume and inject `additionalContext` telling the agent which worktree to `cd` into, based on
the lock file that maps session IDs to active issues/worktrees.

## Satisfies

None (infrastructure improvement)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Must not break fresh session starts or compaction events
- **Mitigation:** Only activate when `source` is `resume` and a matching lock file exists

## Research Findings

- `SessionStart` hook fires on resume with `"source": "resume"` in the JSON input
- `HookInput` already parses `session_id` and has `getString()` for reading `source`
- Lock files at `.claude/cat/locks/{issue-name}.lock` contain `session_id` and `worktree` fields
- The `worktree` field in lock files is currently always empty - needs to be populated by `/cat:work`
- Worktree paths follow pattern: `.claude/cat/worktrees/{issue-id}/`
- `CLAUDE_ENV_FILE` is available in `SessionStart` hooks for injecting environment variables
- `additionalContext` in hook output is injected into the agent context as a `<system-reminder>`

## Files to Modify

1. `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreWorktreeOnResume.java` (NEW)
   - New `SessionStartHandler` that activates only when `source` is `resume`
   - Reads `session_id` from input, scans `.claude/cat/locks/*.lock` for matching session
   - If match found and worktree path exists, injects `additionalContext` instructing agent to `cd` into worktree
   - If no match or worktree gone, silently does nothing (not an error)

2. `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java`
   - Add `RestoreWorktreeOnResume` to the handler list

3. Lock file population (ensure worktree path is stored):
   - Find where lock files are created during `/cat:work` (the `work-prepare` skill or its preprocessor)
   - Ensure the `worktree` field is populated with the actual worktree path
   - Files: search for lock creation code in `client/src/main/java/` and `plugin/skills/work-prepare/`

4. `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreWorktreeOnResumeTest.java` (NEW)
   - Test: resume with matching lock and existing worktree -> injects cd instruction
   - Test: resume with matching lock but missing worktree -> no injection
   - Test: resume with no matching lock -> no injection
   - Test: fresh start (source != resume) -> no injection

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. Find and update lock file creation to populate the `worktree` field with the worktree path
2. Create `RestoreWorktreeOnResume.java` SessionStartHandler
3. Register the handler in `SessionStartHook.java`
4. Create tests in `RestoreWorktreeOnResumeTest.java`
5. Run `mvn -f client/pom.xml verify` to ensure all tests pass

## Post-conditions

- [ ] Lock files created by `/cat:work` contain the worktree path in the `worktree` field
- [ ] On `--resume`, if the session has an active lock with a worktree, the agent receives `additionalContext`
      instructing it to `cd` into the worktree
- [ ] On fresh `--startup`, no worktree restoration is attempted
- [ ] If the worktree directory no longer exists, no error is raised
- [ ] All tests pass (`mvn -f client/pom.xml verify` exit code 0)