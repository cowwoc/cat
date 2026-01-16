# Workflow: Execute Task

## Overview
Detailed workflow for executing a task from start to completion.

## Prerequisites
- Task exists with STATE.md, PLAN.md
- All task dependencies completed
- Main agent in orchestration mode
- **Task lock can be acquired** (not locked by another session)

## CRITICAL: Worktree Isolation (M101)

**ALL task implementation work MUST happen in the task worktree, NEVER in `/workspace` main.**

```
/workspace/                    ← MAIN WORKTREE - READ-ONLY during task execution
├── .worktrees/
│   └── 0.5-task-name/        ← TASK WORKTREE - All edits happen here
│       └── parser/src/...
└── parser/src/...            ← NEVER edit these files during task execution
```

**Rules:**
1. After creating worktree, immediately `cd` to it and verify with `pwd`
2. All file edits, git commits, and builds happen in the task worktree
3. Return to `/workspace` ONLY for final merge and cleanup
4. If confused about location, run `pwd` and `git branch --show-current`

**Why:** Multiple parallel tasks create separate worktrees. Editing main worktree:
- Corrupts other parallel tasks
- Creates merge conflicts
- Makes rollback impossible

## Workflow Steps

### 1. Validate Task Ready and Acquire Lock

```
Check STATE.md
    |
    +---> Status: pending or in-progress
    |
    +---> Task Dependencies: All completed
    |
    +---> Minor Version Dependency: Met
    |
    +---> Try to Acquire Lock (M097)
    |         |
    |         +---> If locked by another session: SKIP task, try next
    |         |
    |         +---> If lock acquired: Proceed
    |
    v
Proceed to execution
```

**MANDATORY: Lock Check Before Proceeding (M097)**

Before validating a task as executable, attempt to acquire its lock:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
# Session ID is auto-substituted as ${CLAUDE_SESSION_ID}

LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" acquire "$TASK_ID" "${CLAUDE_SESSION_ID}")

if echo "$LOCK_RESULT" | jq -e '.status == "locked"' > /dev/null 2>&1; then
  echo "⏸️ Task $TASK_ID is locked by another session"
  # Skip this task, try next candidate
fi
```

This prevents:
- Offering tasks that another Claude instance is executing
- Wasted exploration/planning on locked tasks
- Confusion about task availability

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
- **Check if task is locked by another session**
- Report to user or execute blockers first

### 2. Analyze Task Size and Auto-Decompose

**MANDATORY: Estimate task complexity before execution.**

```yaml
# Read config
context_limit: 200000  # from cat-config.json
target_usage: 40       # percentage
threshold: 80000       # context_limit * target_usage / 100

# Estimate task size - INCLUDE ALL PHASES
estimation_factors:
  # Fixed costs per subagent phase
  exploration_subagent: 10000    # codebase analysis
  planning_subagent: 15000       # approach design
  stakeholder_review: 5000       # per reviewer (5 reviewers typical)

  # Variable costs from PLAN.md (implementation phase)
  files_to_create: count × 5000
  files_to_modify: count × 3000
  test_files: count × 4000
  plan_steps: count × 2000
  exploration_buffer: 10000 if uncertain

# Total = exploration + planning + implementation + review
estimated_tokens: 10000 + 15000 + (implementation factors) + 25000
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

### 4. Delegate Exploration (with Duplicate Detection)

**CRITICAL**: Main agent is orchestrator only. Delegate ALL work to subagents.

Main agent does NOT read code files directly. Spawn an exploration subagent:

```
Task tool invocation:
  description: "Explore {task} implementation"
  subagent_type: "Explore"
  prompt: |
    Analyze code for {task-name}.

    **DUPLICATE CHECK (FIRST):**
    Before exploring, check if functionality already exists:
    1. Search for key methods/classes mentioned in PLAN.md
    2. Check if tests exist for scenarios in STATE.md
    3. If functionality exists AND tests pass:
       Return "DUPLICATE: [evidence]" and STOP immediately

    **If NOT duplicate, continue with:**
    1. Relevant method locations and signatures
    2. Current implementation patterns
    3. Existing test coverage
    4. Integration points

    RETURN FINDINGS ONLY. Do NOT implement changes.
```

**If exploration returns DUPLICATE:**

Skip remaining steps. Mark task as duplicate:
1. Update STATE.md: status=completed, resolution=duplicate
2. Commit STATE.md only (no Task ID footer)
3. Cleanup worktree, release lock
4. Offer next task

This saves ~10-15 minutes by avoiding unnecessary planning and implementation subagents.

### 5. Delegate Planning (M091)

**CRITICAL**: Main agent does NOT make decisions or write code. Delegate planning to a subagent.

After receiving exploration findings, spawn a planning subagent:

```
Task tool invocation:
  description: "Plan {task} implementation"
  subagent_type: "Plan"
  prompt: |
    Based on these exploration findings:
    {exploration_results}

    And the task PLAN.md:
    {plan_md_content}

    Create a detailed implementation specification:
    1. Make all architectural/design decisions
    2. Write explicit code examples (actual code, not descriptions)
    3. Specify exact file paths to modify
    4. Define verification steps with expected output
    5. Determine error handling approaches
    6. Compose the commit message

    RETURN IMPLEMENTATION SPEC ONLY. Do NOT implement changes.
```

### 6. Spawn Implementation Subagent

Main agent spawns implementation subagent with:
- Planning subagent's implementation specification
- Worktree path
- Session tracking info
- Token monitoring instructions

Subagent branch:
```
{major}.{minor}-{task-name}-sub-{uuid}
```

### 7. Subagent Execution

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

### 8. Subagent Completion

On completion, subagent returns via `.completion.json`:
```json
{
  "status": "success|failure",
  "tokensUsed": 75000,
  "compactionEvents": 0,
  "summary": "Implemented switch statement parsing"
}
```

### 9. MANDATORY: Report Token Metrics to User

**Subagents measure their own tokens and return them to main agent (M099).**

The subagent is responsible for measuring its token usage before completion:
```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"
cat > /tmp/token_count.jq << 'EOF'
[.[] | select(.type == "assistant") | .message.usage | select(. != null) |
  (.input_tokens + .output_tokens)] | add // 0
EOF
TOKENS=$(jq -s -f /tmp/token_count.jq "$SESSION_FILE")
```

Main agent reports ONLY measured values from subagent output. Never fabricate estimates.
If subagent didn't report metrics, state "NOT MEASURED" - do not invent numbers.

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

### 10. Main Agent Merge

```bash
# In task worktree
git merge {subagent-branch} --ff-only
```

If conflicts:
- Attempt automatic resolution
- Escalate to user if unresolved

### 11. Cleanup Subagent Resources

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

### 12. Update State

Update task STATE.md:
```markdown
- **Status:** in-progress
- **Progress:** 90%
- **Last Updated:** {timestamp}
- **Note:** Subagent work merged, awaiting approval
```

### 13. Approval Gate (Interactive Mode)

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

### 14. Final Merge

After approval:

**CRITICAL (M070/M090): Update STATE.md AND CHANGELOG.md BEFORE squashing (same commit as implementation)**

Per commit-types.md, task STATE.md changes must be in the SAME commit as implementation.
Minor version CHANGELOG.md should also be updated in the same commit for atomicity.

```bash
# In task worktree - update STATE.md to completed
# Edit .claude/cat/v{major}/v{major}.{minor}/task/{task-name}/STATE.md:
#   status: completed
#   progress: 100%
#   resolution: implemented
#   completed: {date}

# In task worktree - update minor version CHANGELOG.md
# Edit .claude/cat/v{major}/v{major}.{minor}/CHANGELOG.md:
# Add task entry to Tasks Completed table:
#   | {task-name} | {commit-type} | {description from PLAN.md} | implemented |

# Stage STATE.md and CHANGELOG.md with implementation
git add .claude/cat/v{major}/v{major}.{minor}/task/{task-name}/STATE.md
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

### 15. Cleanup

```bash
# MANDATORY: Return to main workspace before removing worktree
cd /workspace
pwd  # Verify we're in main workspace (not worktree)

# Task worktree and branch (subagent already cleaned in step 10)
git worktree remove ../cat-worktree-{task-name}
git branch -d {task-branch}
```

### 16. Update Parent State (Rollup Only)

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
