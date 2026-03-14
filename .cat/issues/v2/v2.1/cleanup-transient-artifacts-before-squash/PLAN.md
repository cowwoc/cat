# Plan: cleanup-transient-artifacts-before-squash

## Current State
The work-merge-agent squashes commits without first cleaning up transient worktree artifacts. Subagent worktrees
(`.claude/worktrees/agent-*`) and review artifacts (`.cat/review/*.json`) get committed during implementation and
auto-fix phases, then end up in the squashed commit.

## Target State
The work-merge-agent workflow includes a cleanup step before squashing that:
1. Waits for all subagents to complete
2. Removes subagent worktree marker files (`.claude/worktrees/agent-*`)
3. Removes review artifact files (`.cat/review/*.json`)
4. Commits the cleanup (or amends the previous commit) so squash produces a clean result

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — removes files that should never be committed
- **Mitigation:** The cleanup step only removes known transient file patterns; verify with git diff after

## Files to Modify
- `plugin/skills/work-merge-agent/first-use.md` - add cleanup step before Step 9 (squash commits by topic)

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Add a new step to work-merge-agent between Step 8 (Skill-Builder Review) and Step 9 (Squash Commits) that:
  1. Runs `git rm -r --ignore-unmatch .claude/worktrees/ .cat/review/` to remove transient artifacts
  2. If any files were staged, commits with `config: remove transient worktree and review artifacts`
  3. Proceeds to squash
  - Files: `plugin/skills/work-merge-agent/first-use.md`

## Post-conditions
- [ ] `work-merge-agent/first-use.md` contains a cleanup step before the squash step
- [ ] The cleanup step removes `.claude/worktrees/agent-*` and `.cat/review/*.json`
- [ ] The cleanup step only acts when transient files are present (no empty commits)
