---
mainAgent: true
---
## Worktree Isolation
**CRITICAL**: NEVER work on issues in the main worktree. ALWAYS use isolated worktrees.
*(Enforced by hook - Edit/Write blocked on protected branches for plugin/ files)*

**Correct flow**: ask Claude to add an issue -> ask Claude to work on it (creates worktree) -> delegate to subagent -> merge back

**Working in worktrees**: `cd` into the worktree directory instead of using `git -C` from outside.
This ensures all file operations target the worktree, not the main workspace.

**Violation indicators**:
- Git dir does not end with `worktrees/<branch-name>` (not an issue worktree)
- Making issue-related edits without first asking Claude to work on an issue

**Why isolation matters**:
- Failed work doesn't pollute main branch
- Parallel work on multiple tasks possible
- Clean rollback if task is abandoned
- Clear separation between planning and implementation

**If hook blocks your edit**: Ask Claude to add an issue and ask Claude to work on it in an isolated worktree.
