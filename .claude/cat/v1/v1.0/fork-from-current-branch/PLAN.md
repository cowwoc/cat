# Task: fork-from-current-branch

## Goal

Change /cat:work to fork task branches from whatever branch is currently checked out in the main worktree (instead of always forking from main), and merge back to that same branch when the task completes.

## Type

Feature

## Requirements

- REQ-001: Task branches fork from current branch, not main
- REQ-002: Task branches merge back to the branch they forked from
- REQ-003: The "base branch" is recorded and tracked throughout task execution

## Approach

1. In the `create_worktree` step, detect the currently checked out branch in the main worktree
2. Store the base branch name in task context (e.g., in STATE.md or a variable)
3. Fork the task branch from this base branch instead of hardcoded "main"
4. In the `merge` step, merge back to the recorded base branch

## Acceptance Criteria

- [ ] Task branch forks from current branch (not main)
- [ ] Base branch name is recorded for later merge
- [ ] Merge step uses recorded base branch
- [ ] Works correctly when main worktree is on main
- [ ] Works correctly when main worktree is on a feature branch
- [ ] Works correctly when main worktree is on a version tag

## Files to Modify

- `commands/execute-task.md` - Update create_worktree and merge steps
- Possibly `skills/spawn-subagent/SKILL.md` if it references main branch

## Risk Assessment

- **Risk Level:** LOW
- **Rationale:** Isolated change to branch management logic, easy to test and verify
