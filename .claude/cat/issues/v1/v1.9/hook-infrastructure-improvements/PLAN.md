# Plan: hook-infrastructure-improvements

## Objective
improve hook guidance for worktree-based rebase

## Details
When blocking commands like `git checkout <branch> && git rebase`,
the hook now provides specific guidance:
- Explains why main worktree checkout changes are blocked
- Shows the correct workflow: rebase from task worktree, then ff-merge
- Includes the detected branch name in the guidance

This helps agents recover from the block without falling back to workarounds.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
