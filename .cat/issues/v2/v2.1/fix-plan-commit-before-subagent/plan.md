# Plan

## Goal

Fix work-implement skill instructions to commit plan.md changes before spawning subagents. The
EnforceCommitBeforeSubagentSpawn hook blocks subagent spawning when uncommitted changes exist in the
worktree. Currently, the work-implement-agent skill updates plan.md during execution but doesn't
commit those changes before spawning implementation subagents, causing the hook to block the spawn.
The fix is to update the skill instructions to commit plan.md changes before spawning subagents,
keeping the hook in place.

## Pre-conditions

(none)

## Post-conditions

- [ ] work-implement skill instructions commit plan.md changes before spawning subagents
- [ ] Regression test added for the commit-before-spawn flow
- [ ] No new issues introduced
- [ ] E2E: Execute work-implement workflow and confirm subagent spawning succeeds after plan.md
  modifications
