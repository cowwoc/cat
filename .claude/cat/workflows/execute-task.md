# Workflow: Execute Task

## Overview
Detailed workflow for executing a task from start to completion.

## Prerequisites
- Task exists with STATE.md, PLAN.md
- All task dependencies completed
- Main agent in orchestration mode

## Workflow Steps

### 1. Validate Task Ready

```
Check STATE.md
    |
    +---> Status: pending or in-progress
    |
    +---> Dependencies: All completed
    |
    v
Proceed to execution
```

If blocked:
- Identify blocking dependencies
- Report to user or execute blockers first

### 2. Create Task Worktree

```bash
# Main agent creates task branch and worktree
git worktree add ../cat-worktree-{task-name} -b {major}.{minor}-{task-name}
```

Update STATE.md:
- Status: `in-progress`
- Last Updated: current timestamp

### 3. Pre-Spawn Decision Making

**CRITICAL**: Before spawning, the main agent must resolve ALL ambiguities and decisions.

Users cannot supervise subagent execution. Claude Code provides no way to view subagent output
or correct mistakes in real-time. The subagent prompt must enable purely mechanical execution.

**Main agent MUST**:
1. Read all files the subagent will modify
2. Make all architectural/design decisions
3. Write explicit code examples (not descriptions)
4. Specify exact verification steps and expected output
5. Determine error handling approaches
6. Compose the exact commit message

**Prompt completeness check**:
- Does it specify exact file paths? (not "find the auth file")
- Does it include actual code? (not "add error handling")
- Does it define success criteria? (not "make sure it works")
- Does it handle failure cases? (not "handle errors appropriately")

See `spawn-subagent` skill for detailed prompt requirements.

### 4. Spawn Subagent

Main agent spawns subagent with:
- Task PLAN.md content **expanded with decisions from step 3**
- Worktree path
- Session tracking info
- Token monitoring instructions
- **Complete code examples and verification steps**

Subagent branch:
```
{major}.{minor}-{task-name}-sub-{uuid}
```

### 5. Subagent Execution

Subagent in worktree:
1. Read PLAN.md execution steps
2. Implement changes following TDD
3. Run tests continuously
4. Track token usage
5. Commit changes to subagent branch

Token tracking:
- Read session JSONL file
- Sum input_tokens + output_tokens
- Count compaction events

### 6. Subagent Completion

On completion, subagent returns:
```json
{
  "status": "success|failure",
  "tokensUsed": 75000,
  "compactionEvents": 0,
  "summary": "Implemented switch statement parsing"
}
```

### 7. Main Agent Merge

```bash
# In task worktree
git merge {subagent-branch} --no-ff
```

If conflicts:
- Attempt automatic resolution
- Escalate to user if unresolved

### 8. Cleanup Subagent Resources

**After merging subagent branch to task branch, cleanup BEFORE approval gate:**

```bash
# Remove subagent worktree
git worktree remove {subagent-worktree-path}

# Delete subagent branch
git branch -d {subagent-branch}
```

This ensures:
- Only the task branch remains for review
- No orphaned worktrees/branches if user rejects
- Clean state for approval decision

### 9. Update State

Update task STATE.md:
```markdown
- **Status:** in-progress
- **Progress:** 90%
- **Last Updated:** {timestamp}
- **Note:** Subagent work merged, awaiting approval
```

### 10. Approval Gate (Interactive Mode)

Present to user:
- Summary of changes
- Files modified
- Branch for review
- Test results

**CRITICAL: Approval Protocol**

1. Use AskUserQuestion with explicit "Approve" / "Reject" options
2. Wait for explicit approval response
3. **If user provides feedback instead of approval:**
   - Address the feedback (fix issues, adjust changes)
   - **RE-PRESENT the approval gate** with updated changes
   - **Feedback is NOT approval** - do not proceed to merge
4. Only proceed to Final Merge after explicit "Approve" response

```
Approval Gate Flow:

Present changes → User responds
                      |
        +-------------+-------------+
        |                           |
    "Approve"                   Feedback/Request
        |                           |
        v                           v
    Proceed to              Address feedback
    Final Merge                     |
                                    v
                            RE-PRESENT approval gate
                                    |
                                    v
                            (loop until Approve/Reject)
```

**Anti-pattern (M052):** Interpreting feedback as implicit approval and merging without re-confirmation.

### 11. Final Merge

After approval:
```bash
# Squash commits by type
git rebase -i main  # Group by feature, bugfix, refactor, etc.

# Merge to main
git checkout main
git merge {task-branch}
```

### 12. Cleanup

```bash
# Task worktree and branch (subagent already cleaned in step 8)
git worktree remove ../cat-worktree-{task-name}
git branch -d {task-branch}
```

### 13. Update Changelogs

Update minor and major version CHANGELOG.md files with completed task summary.

**Minor version CHANGELOG.md** (`.claude/cat/v{major}/v{major}.{minor}/CHANGELOG.md`):

Add task entry to Tasks Completed table:
```markdown
| {task-name} | {commit-type} | {brief description from PLAN.md goal} |
```

**Major version CHANGELOG.md** (`.claude/cat/v{major}/CHANGELOG.md`):

Update aggregate summary if this completes a minor version.

> **NOTE**: Task changelog content is embedded in commit messages, not separate files.
> See `templates/changelog.md` for full changelog format.

If CHANGELOG.md doesn't exist yet, create it using the template format with:
- Version header and pending status
- Empty Tasks Completed table
- Placeholder sections

## Error Recovery

### Subagent Failure
1. Subagent returns error status
2. Main agent logs failure
3. Attempt resolution or escalate

### Merge Conflict
1. Identify conflicting files
2. Attempt automatic resolution
3. Escalate with conflict details if unresolved

### Session Interruption
1. STATE.md preserves progress
2. Worktree may have partial work
3. Resume resumes from last state

## Parallel Execution

For independent tasks:
```
Main Agent
    |
    +---> Subagent A (task-1)
    +---> Subagent B (task-2)
    +---> Subagent C (task-3)
    |
    v
Process completions as they arrive
```
