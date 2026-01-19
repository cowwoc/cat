# Worktree Management

## Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Task | `{major}.{minor}-{task-name}` | `1.0-parse-tokens` |
| Subagent | `{major}.{minor}-{task-name}-sub-{uuid}` | `1.0-parse-tokens-sub-a1b2c3` |

## Worktree Allocation

| Agent | Worktree Purpose |
|-------|------------------|
| Main agent | One per task (merging only) |
| Subagent | One per execution |

## Lifecycle

### Creation
- Task worktree: Created when task execution begins
- Subagent worktree: Created when subagent spawns

### During Execution
- Subagent works in isolated worktree
- Changes committed to subagent branch
- Main agent worktree used for merge operations

### Cleanup
- Immediate after successful merge to main
- Use locking to prevent conflicts
- `autoRemoveWorktrees` config controls automatic removal

## Merge Flow

```
Subagent completes in worktree/branch
          |
          v
Main agent merges subagent branch -> task branch
          |
          v
Repeat for all task subagents
          |
          v
User approval gate (interactive mode)
          |
          v
Squash commits by type (feature, bugfix, refactor, etc.)
          |
          v
Merge task branch -> main
          |
          v
Cleanup worktrees
```

## Commands

```bash
# Create worktree
git worktree add ../<path> -b <branch-name>

# List worktrees
git worktree list

# Remove worktree
git worktree remove <path>

# Prune stale worktrees
git worktree prune
```
