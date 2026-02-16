# Plan: fix-merge-parent-shell-corruption

## Problem
During /cat:work merge phase, the work-merge subagent removes the worktree while the parent agent shell is still inside it. This corrupts the parent shell — all subsequent Bash commands fail with exit code 1.

## Satisfies
None

## Root Cause
The parent agent spawns the merge subagent while its own shell cwd is inside the worktree (e.g., /workspace/.claude/cat/worktrees/issue-name/). The subagent correctly cd's to /workspace before removing the worktree (its own shell is safe), but the parent shell remains in the deleted directory.

## Files to Modify
- plugin/skills/work-with-issue/first-use.md - Add cd /workspace before spawning merge subagent in Step 8

## Execution Steps
1. **Add cd /workspace instruction:** In plugin/skills/work-with-issue/first-use.md Step 8, add a Bash command `cd /workspace` after the progress-banner call and before the Task tool spawn. Include a comment explaining this prevents parent shell corruption when the merge subagent removes the worktree.

## Acceptance Criteria
- [ ] Step 8 includes cd /workspace before spawning the merge subagent
- [ ] Comment explains why the cd is needed
- [ ] No regressions in merge workflow