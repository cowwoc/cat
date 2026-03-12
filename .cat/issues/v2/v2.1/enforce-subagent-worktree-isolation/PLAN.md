# Plan: enforce-subagent-worktree-isolation

## Goal
Ensure that subagents (both parallel and sequential) cannot modify each other's or the main agent's
files by enforcing worktree isolation between agents. All cross-agent communication must go through
committed git state.

## Background
When the main agent spawns implementation subagents, they share the same issue worktree. This causes
problems even with a single sequential subagent:

1. **Subagents can overwrite the main agent's uncommitted changes.** A subagent may do `git checkout`
   or `git restore` to clean its working directory, discarding the main agent's uncommitted edits.
   Observed example: a single sequential subagent reverted PLAN.md updates made by the main agent
   because the changes were not yet committed.

2. **Uncommitted changes are invisible across agents.** If the main agent modifies a file without
   committing, the subagent cannot reliably see or preserve the change. Conversely, if a subagent
   makes uncommitted changes, the main agent may overwrite them after the subagent returns.

3. **Parallel subagents race on shared disk state.** When multiple wave subagents run in parallel,
   they can silently overwrite each other's in-flight changes — unpredictable disk-level races with
   no git-level protection.

**Root cause:** The work-with-issue orchestration spawns subagents into the same worktree as the
main agent. Even sequential subagents share the working directory, so any uncommitted state is
vulnerable to being discarded.

**Required invariant:** Worktree isolation is bidirectional — subagents cannot write to the main
agent's worktree, and the main agent cannot write to subagent worktrees. The main agent must commit
all changes before spawning any subagent. Each subagent must work in its own isolated worktree.
Cross-agent state must only flow through committed git history, never through shared on-disk state.

## Satisfies
- None (correctness fix)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Changes to subagent delegation protocol affect all wave workflows (parallel and sequential)
- **Mitigation:** Enforce commit-before-spawn at the orchestration level; each subagent gets its own
  worktree; validate with empirical tests

## Files to Modify
- `plugin/skills/work-with-issue-agent/SKILL.md` — add requirement: subagents must commit all
  changes before returning; the main agent must not modify worktree files between wave spawns
  without committing first
- Hook enforcement: add a hook or validator that detects when a subagent attempts to write files
  outside its designated worktree scope (or flag uncommitted writes before subagent handoff)
- Consider: give each parallel wave subagent its own branch, merge after completion

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Audit `work-with-issue-agent` orchestration: identify all points where the main agent or a
  subagent could leave uncommitted state that another agent touches
  - Files: `plugin/skills/work-with-issue-agent/SKILL.md`
- Add explicit requirement: main agent must commit all file changes (PLAN.md, etc.) before
  spawning any subagent — including single sequential subagents, not just parallel waves
  - Files: `plugin/skills/work-with-issue-agent/SKILL.md`
- Change subagent spawning to use isolated worktrees (one per subagent) instead of sharing
  the issue worktree; merge subagent branches back after completion
  - Files: `plugin/skills/work-with-issue-agent/SKILL.md`
- Add hook enforcement: block file writes across worktree boundaries (subagent → main,
  main → subagent, subagent → sibling subagent)
  - Files: hook scripts
- Write empirical test to verify subagent isolation (both sequential and parallel scenarios)
  - Files: `client/src/test/java/.../WorkExecuteIsolationTest.java` (or equivalent)

## Post-conditions
- [ ] Main agent commits all changes before spawning any subagent (sequential or parallel)
- [ ] Each subagent (including single sequential ones) works in its own isolated worktree
- [ ] No subagent can silently overwrite main agent or sibling subagent uncommitted changes
- [ ] Hook enforcement blocks cross-worktree writes bidirectionally (subagent ↔ main, subagent ↔ subagent)
- [ ] Empirical test validates isolation under both sequential and parallel subagent execution
