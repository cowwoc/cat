---
name: cat:parallel-execute
description: Orchestrate multiple independent subagents concurrently with coordinated collection
---

# Parallel Execute

## Purpose

Launch and manage multiple independent subagents simultaneously to maximize throughput. Coordinates
spawning, monitoring, result collection, and merging for tasks that have no dependencies between
them. Essential for efficient use of CAT's multi-agent capabilities.

## When to Use

- Multiple tasks have no dependencies between them
- Tasks can execute in complete isolation
- Parent agent needs to coordinate multiple work streams
- Optimizing for wall-clock time rather than token efficiency
- **AUTO-TRIGGERED:** After decompose-task creates independent subtasks

## Auto-Trigger from Decomposition

When `/cat:work` triggers auto-decomposition (task exceeds context threshold),
this skill is automatically invoked for parallel execution:

```
work → analyze_task_size → (exceeds threshold) → decompose-task → parallel-execute
```

**Integration workflow:**

1. `work` estimates task size > threshold (e.g., 80K tokens)
2. `work` auto-invokes `decompose-task`
3. `decompose-task` creates subtasks and generates parallel execution plan
4. `decompose-task` identifies sub-task-based parallelization
5. `work` auto-invokes `parallel-execute` with the sub-task plan
6. `parallel-execute` spawns subagents for each sub-task

**Example auto-trigger flow:**

```yaml
# work detects large task
task: 1.2-implement-parser
estimated_tokens: 120000
threshold: 80000  # 40% of 200K

# Auto-decomposition triggered
decomposed_into:
  - 1.2a-parser-lexer (25K tokens)
  - 1.2b-parser-ast (30K tokens)
  - 1.2c-parser-semantic (25K tokens)

# Parallel plan generated
parallel_plan:
  sub_task_1: [1.2a, 1.2c]  # Independent, run concurrently
  sub_task_2: [1.2b]         # Depends on 1.2a

# Auto-parallel execution
action: spawn 2 subagents for sub_task_1
```

## Workflow

**Progress Output (MANDATORY):**

Display sub-task-based progress for parallel execution:
```
═══════════════════════════════════════════════════
Sub-task N/M: Spawning K subagents (P% overall | Xs elapsed)
═══════════════════════════════════════════════════
[Subagent 1/K] task-name-a... spawned
[Subagent 2/K] task-name-b... spawned

Sub-task N/M: Monitoring K subagents (P% | Xs elapsed | ~Ys remaining)
  ✓ task-name-a: complete (12s, 45K tokens)
  ⏳ task-name-b: running (8s elapsed)

Sub-task N/M: Collecting results (P% | Xs elapsed)
  ✓ task-name-a: merged
  ✓ task-name-b: merged

✅ Sub-task N/M complete: 2/2 subagents merged
```

Steps per sub-task: 1. Spawn subagents, 2. Monitor progress, 3. Collect results, 4. Merge branches

### 1. Identify Parallelizable Tasks

Analyze task dependencies to find independent work:

```yaml
# Dependency analysis
tasks:
  1.2a-parser-lexer: []           # No dependencies
  1.2b-parser-ast: [1.2a]         # Depends on 1.2a
  1.3a-formatter-core: []          # No dependencies
  1.3b-formatter-wrapping: [1.3a]  # Depends on 1.3a
  1.4-documentation: []            # No dependencies

# Parallelizable groups
parallel_group_1: [1.2a, 1.3a, 1.4]  # All independent
# After group 1 completes:
parallel_group_2: [1.2b, 1.3b]       # Dependencies satisfied
```

### 2. Spawn Multiple Subagents

Use `spawn-subagent` skill for each independent task:

```bash
# Spawn all independent tasks concurrently
for task in "${PARALLEL_TASKS[@]}"; do
  UUID=$(uuidgen | cut -c1-8)
  BRANCH="${task}-sub-${UUID}"
  WORKTREE=".worktrees/${BRANCH}"

  # Create worktree
  git worktree add -b "${BRANCH}" "${WORKTREE}" HEAD

  # Launch subagent (non-blocking)
  (
    cd "${WORKTREE}"
    claude --prompt "Execute PLAN.md for task ${task}"
  ) &

  # Record spawn
  echo "${task}:${UUID}:${WORKTREE}" >> active_subagents.txt
done
```

### 3. Monitor All Concurrently

Use `monitor-subagents` skill to track all active subagents:

```yaml
# Monitoring loop
while has_active_subagents; do
  for subagent in $(get_active_subagents); do
    status=$(check_status "${subagent}")
    tokens=$(get_token_usage "${subagent}")

    case "${status}" in
      "completed")
        mark_ready_for_collection "${subagent}"
        ;;
      "warning")
        # Approaching context limit
        log_warning "${subagent}" "${tokens}"
        ;;
      "failed")
        handle_failure "${subagent}"
        ;;
    esac
  done

  sleep 30  # Poll interval
done
```

### 4. Collect Results as Each Completes

Don't wait for all to complete - collect progressively:

```bash
# Event-driven collection
while has_pending_subagents; do
  for subagent in $(get_completed_subagents); do
    # Use collect-results skill
    collect-results "${subagent}"

    # Update tracking
    mark_collected "${subagent}"

    # Check if any dependent tasks can now start
    check_unblock_dependents "${subagent}"
  done

  sleep 10
done
```

### 5. Merge in Dependency Order

Even for parallel execution, merge order matters:

```yaml
# Merge strategy for parallel group
merge_order:
  # Independent tasks can merge in any order
  - 1.2a-parser-lexer      # Merge first (1.2b depends on this)
  - 1.3a-formatter-core    # Merge second (1.3b depends on this)
  - 1.4-documentation      # Merge third (no dependents)

  # Dependent tasks merge after their dependencies
  - 1.2b-parser-ast        # After 1.2a merged
  - 1.3b-formatter-wrapping # After 1.3a merged
```

```bash
# Merge with dependency awareness
for task in "${MERGE_ORDER[@]}"; do
  subagent=$(get_subagent_for_task "${task}")

  # Verify dependencies are merged
  for dep in $(get_dependencies "${task}"); do
    verify_merged "${dep}" || error "Dependency ${dep} not yet merged"
  done

  # Use merge-subagent skill
  merge-subagent "${subagent}"
done
```

### 6. Handle Partial Failures

Some subagents may fail while others succeed:

```yaml
failure_handling:
  strategy: CONTINUE_ON_FAILURE

  on_failure:
    - Record failure details
    - Collect any partial results
    - Continue with successful subagents
    - Mark dependent tasks as blocked
    - Report failures to orchestrator

  recovery_options:
    - Retry failed task with fresh subagent
    - Decompose failed task into smaller pieces
    - Manual intervention for complex failures
```

```bash
handle_failure() {
  local subagent="$1"

  # Collect partial results if any
  collect-results "${subagent}" --partial

  # Mark task as failed
  update_state "${subagent}" "failed"

  # Block dependent tasks
  for dependent in $(get_dependents "${subagent}"); do
    mark_blocked "${dependent}" "dependency ${subagent} failed"
  done

  # Log for orchestrator
  log_failure "${subagent}" "$(get_error_details "${subagent}")"
}
```

### 7. Update Orchestration State

Track parallel execution progress:

```yaml
parallel_execution:
  id: pe-001
  started_at: 2026-01-10T14:00:00Z

  parallel_group: 1
  tasks:
    - task: 1.2a-parser-lexer
      subagent: a1b2c3d4
      status: completed
      collected: true
      merged: true

    - task: 1.3a-formatter-core
      subagent: b2c3d4e5
      status: completed
      collected: true
      merged: false  # Pending

    - task: 1.4-documentation
      subagent: c3d4e5f6
      status: running
      tokens: 45000

  aggregate_metrics:
    total_tokens: 145000
    elapsed_time: 1.5 hours
    tasks_complete: 2
    tasks_running: 1
    tasks_failed: 0
```

## Examples

### Simple Parallel Execution

```bash
# Three independent tasks
TASKS=("1.2a-parser-lexer" "1.3a-formatter-core" "1.4-documentation")

# Spawn all
for task in "${TASKS[@]}"; do
  spawn-subagent "${task}"
done

# Monitor until all complete
while [ $(count_running) -gt 0 ]; do
  monitor-subagents
  sleep 30
done

# Collect and merge all
for task in "${TASKS[@]}"; do
  collect-results "${task}"
  merge-subagent "${task}"
done
```

### Parallel with Dependencies

```yaml
execution_plan:
  sub_task_1:  # All parallel
    - 1.2a-parser-lexer
    - 1.3a-formatter-core
    - 1.4-documentation

  sub_task_2:  # After sub_task_1, parallel within sub-task
    - 1.2b-parser-ast       # Needs 1.2a
    - 1.3b-formatter-wrapping  # Needs 1.3a

execution:
  - spawn sub_task_1 tasks
  - monitor and collect sub_task_1
  - merge sub_task_1 results
  - spawn sub_task_2 tasks (now unblocked)
  - monitor and collect sub_task_2
  - merge sub_task_2 results
```

### Parallel with Failure Recovery

```yaml
parallel_execution:
  tasks:
    - 1.2a: completed
    - 1.3a: FAILED
    - 1.4: completed

  failure_recovery:
    task: 1.3a-formatter-core
    action: decompose_and_retry
    new_tasks:
      - 1.3a1-formatter-base
      - 1.3a2-formatter-indent

  execution_continues:
    - Merge 1.2a and 1.4
    - Spawn 1.3a1 and 1.3a2
    - 1.3b blocked until 1.3a1+1.3a2 complete
```

## Anti-Patterns

### Sequence dependent tasks (only parallelize independent ones)

```bash
# ❌ Launching tasks with dependencies
spawn-subagent "1.2a-parser-lexer"
spawn-subagent "1.2b-parser-ast"  # Depends on 1.2a!

# ✅ Sequence dependent tasks
spawn-subagent "1.2a-parser-lexer"
wait_for_completion "1.2a"
merge-subagent "1.2a"
spawn-subagent "1.2b-parser-ast"  # Now safe
```

### Limit concurrent subagents to safe maximum

```bash
# ❌ Spawning without limit
for task in "${ALL_100_TASKS[@]}"; do
  spawn-subagent "${task}"  # 100 concurrent subagents!
done

# ✅ Limit concurrent subagents
MAX_CONCURRENT=5
while has_tasks; do
  while [ $(count_running) -lt ${MAX_CONCURRENT} ]; do
    spawn-subagent "$(next_task)"
  done
  sleep 30
done
```

### Handle partial failures progressively

```bash
# ❌ All-or-nothing approach
parallel-execute "${TASKS[@]}"
if any_failed; then
  rollback_all
  abort
fi

# ✅ Progressive handling
for subagent in $(get_completed); do
  if is_successful "${subagent}"; then
    collect-results "${subagent}"
    merge-subagent "${subagent}"
  else
    handle_failure "${subagent}"
    # Continue with others
  fi
done
```

### Always collect results before merging

```bash
# ❌ Skipping collection
for subagent in $(get_completed); do
  merge-subagent "${subagent}"  # No metrics!
done

# ✅ Always collect first
for subagent in $(get_completed); do
  collect-results "${subagent}"
  merge-subagent "${subagent}"
done
```

### Clean up worktrees after parallel execution

```bash
# ❌ Leaving worktrees after parallel execution
# ... 5 worktrees orphaned

# ✅ Cleanup in merge step
for subagent in $(get_subagents); do
  merge-subagent "${subagent}"  # Includes cleanup
done

# Verify cleanup
git worktree list  # Should show no -sub- worktrees
```

## Related Skills

- `cat:spawn-subagent` - Launches individual subagents
- `cat:monitor-subagents` - Tracks all active subagents
- `cat:collect-results` - Gathers subagent results
- `cat:merge-subagent` - Integrates subagent work
- `cat:decompose-task` - Creates parallelizable subtasks
