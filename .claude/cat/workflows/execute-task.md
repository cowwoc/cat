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
    +---> Task Dependencies: All completed
    |
    +---> Minor Version Dependency: Met
    |
    v
Proceed to execution
```

**Minor version dependency rules:**

| Scenario | Dependency |
|----------|------------|
| First minor of first major (v0.0) | None - always executable |
| Subsequent minors (e.g., v0.5) | Previous minor must be complete (v0.4) |
| First minor of new major (e.g., v1.0) | Last minor of previous major must be complete |

A minor is complete when all its tasks have `status: completed`.

If blocked:
- Identify blocking task dependencies
- Identify blocking minor version dependency
- Report to user or execute blockers first

### 2. Analyze Task Size and Auto-Decompose

**MANDATORY: Estimate task complexity before execution.**

```yaml
# Read config
context_limit: 200000  # from cat-config.json
target_usage: 40       # percentage
threshold: 80000       # context_limit * target_usage / 100

# Estimate task size from PLAN.md
estimation_factors:
  files_to_create: count × 5000
  files_to_modify: count × 3000
  test_files: count × 4000
  plan_steps: count × 2000
  exploration_buffer: 10000 if uncertain

estimated_tokens: sum of factors
```

**If estimated_tokens > threshold:**

```
AUTO-DECOMPOSITION TRIGGERED

Estimated: ~85,000 tokens
Threshold: 80,000 tokens (40% of 200,000)

Invoking /cat:decompose-task...
```

1. Invoke decompose-task skill automatically
2. Decompose-task creates subtasks with dependencies
3. Decompose-task generates parallel execution plan
4. If parallel tasks exist, invoke parallel-execute:

```
Parallel Execution Plan:
  Wave 1: [1.2a-parser-lexer, 1.2c-parser-tests] (concurrent)
  Wave 2: [1.2b-parser-ast] (after wave 1)

Spawning 2 subagents for wave 1...
```

**If estimated_tokens <= threshold:**

```
Task size OK: ~65,000 tokens (81% of threshold)
Proceeding with single subagent.
```

Continue to create worktree step.

### 3. Create Task Worktree

```bash
# Main agent creates task branch and worktree
git worktree add ../cat-worktree-{task-name} -b {major}.{minor}-{task-name}

# MANDATORY: Change to worktree directory for task execution
cd ../cat-worktree-{task-name}
pwd  # Verify we're in the worktree
```

Update STATE.md:
- Status: `in-progress`
- Last Updated: current timestamp

### 4. Pre-Spawn Decision Making

**CRITICAL**: Before spawning implementation, the main agent must resolve ALL ambiguities and decisions.

Users cannot supervise subagent execution. Claude Code provides no way to view subagent output
or correct mistakes in real-time. The subagent prompt must enable purely mechanical execution.

**Code Analysis via Exploration Subagent (M088)**:

Main agent does NOT read code files directly. Spawn an exploration subagent first:

```
Task tool invocation:
  description: "Explore {task} implementation"
  subagent_type: "Explore"
  prompt: |
    Analyze code for {task-name}. Return:
    1. Relevant method locations and signatures
    2. Current implementation patterns
    3. Existing test coverage
    4. Integration points

    RETURN FINDINGS ONLY. Do NOT implement changes.
```

Then use exploration results to make decisions.

**Main agent MUST** (after receiving exploration findings):
1. Make all architectural/design decisions based on findings
2. Write explicit code examples (not descriptions)
3. Specify exact verification steps and expected output
4. Determine error handling approaches
5. Compose the exact commit message

**Prompt completeness check**:
- Does it specify exact file paths? (not "find the auth file")
- Does it include actual code? (not "add error handling")
- Does it define success criteria? (not "make sure it works")
- Does it handle failure cases? (not "handle errors appropriately")

See `spawn-subagent` skill for detailed prompt requirements.

### 5. Spawn Subagent

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

### 6. Subagent Execution

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

### 7. Subagent Completion

On completion, subagent returns via `.completion.json`:
```json
{
  "status": "success|failure",
  "tokensUsed": 75000,
  "compactionEvents": 0,
  "summary": "Implemented switch statement parsing"
}
```

### 8. MANDATORY: Report Token Metrics to User

**After collecting subagent results, ALWAYS present token metrics to user:**

```
## Subagent Execution Report

**Task:** {task-name}
**Status:** {success|partial|failed}

**Token Usage:**
- Total tokens: 75,000 (37.5% of 200K context)
- Compaction events: 0
- Execution quality: Good ✓

**Work Summary:**
- Commits: 3
- Files changed: 5
- Lines: +450 / -120
```

**Why mandatory:**
- Users cannot observe subagent execution in real-time
- Token metrics are the only visibility into execution quality
- Compaction events indicate potential quality degradation
- Enables informed decisions about decomposition

**If compaction events > 0:**

```
⚠️ CONTEXT COMPACTION DETECTED

Compaction events: 2
Execution quality: DEGRADED - context was summarized during execution

RECOMMENDATION: Invoke /cat:decompose-task for remaining similar work.
The subagent may have lost context and produced lower quality output.
```

Present AskUserQuestion with decomposition as recommended option.

### 9. Main Agent Merge

```bash
# In task worktree
git merge {subagent-branch} --ff-only
```

If conflicts:
- Attempt automatic resolution
- Escalate to user if unresolved

### 10. Cleanup Subagent Resources

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

### 11. Update State

Update task STATE.md:
```markdown
- **Status:** in-progress
- **Progress:** 90%
- **Last Updated:** {timestamp}
- **Note:** Subagent work merged, awaiting approval
```

### 12. Approval Gate (Interactive Mode)

Present to user:
- Summary of changes
- Files modified
- Branch for review
- Test results

**CRITICAL: Approval Protocol**

1. Use AskUserQuestion with explicit "Approve" / "Reject" options
2. Wait for explicit approval response
3. **If user provides feedback instead of approval:**
   - **DELEGATE fixes to a new subagent** - main agent does NOT implement directly (M063)
   - Address the feedback via subagent execution
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

### 13. Final Merge

After approval:

**CRITICAL (M070/M090): Update STATE.md AND CHANGELOG.md BEFORE squashing (same commit as implementation)**

Per commit-types.md, task STATE.md changes must be in the SAME commit as implementation.
Minor version CHANGELOG.md should also be updated in the same commit for atomicity.

```bash
# In task worktree - update STATE.md to completed
# Edit .claude/cat/v{major}/v{major}.{minor}/{task-name}/STATE.md:
#   status: completed
#   progress: 100%
#   resolution: implemented
#   completed: {date}

# In task worktree - update minor version CHANGELOG.md
# Edit .claude/cat/v{major}/v{major}.{minor}/CHANGELOG.md:
# Add task entry to Tasks Completed table:
#   | {task-name} | {commit-type} | {description from PLAN.md} | implemented |

# Stage STATE.md and CHANGELOG.md with implementation
git add .claude/cat/v{major}/v{major}.{minor}/{task-name}/STATE.md
git add .claude/cat/v{major}/v{major}.{minor}/CHANGELOG.md
git commit --amend --no-edit  # Include in last implementation commit

# Squash commits by type
git rebase -i main  # Group by feature, bugfix, refactor, etc.

# Merge to main with linear history
git checkout main
git merge --ff-only {task-branch}
```

**Anti-pattern (M090):** Committing CHANGELOG.md as separate commit after merge.

**If --ff-only fails (M081):** Main has diverged from task branch base.

```bash
# DO NOT merge main into task branch (creates non-linear history)
# INSTEAD: Rebase task branch onto main first

# In task worktree:
git fetch origin
git rebase origin/main  # Or use /cat:git-rebase skill

# Then merge with fast-forward
git checkout main
git merge --ff-only {task-branch}
```

**Anti-pattern (M081):** Merging main INTO task branch creates merge commits and non-linear history.

**Anti-pattern (M070):** Committing STATE.md update as separate "planning:" commit after merge.

### 14. Cleanup

```bash
# MANDATORY: Return to main workspace before removing worktree
cd /workspace
pwd  # Verify we're in main workspace (not worktree)

# Task worktree and branch (subagent already cleaned in step 10)
git worktree remove ../cat-worktree-{task-name}
git branch -d {task-branch}
```

### 15. Update Parent State (Rollup Only)

**NOTE**: Minor version CHANGELOG.md was already updated in step 13 with the implementation commit.

This step handles only:
1. Parent STATE.md progress rollup (minor/major)
2. Major version CHANGELOG.md (if minor version completes)

**Major version CHANGELOG.md** (`.claude/cat/v{major}/CHANGELOG.md`):

Update aggregate summary only when a minor version completes (all tasks done).

**CHANGELOG table format** (minor version):
```markdown
| Task | Type | Description | Resolution |
|------|------|-------------|------------|
| {task-name} | {commit-type} | {description from PLAN.md} | implemented |
```

If CHANGELOG.md doesn't exist yet, create it using the template format with:
- Version header and pending status
- Empty Tasks Completed table with correct column order
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
