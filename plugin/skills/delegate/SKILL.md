---
name: cat:delegate
description: Use for batch skill execution, long-running tasks, or isolating verbose skill output
---

# Delegate

## Purpose

Delegate tasks or skills to subagents in isolated worktrees. Multiple items execute in parallel by
default; use `--sequential` to force sequential execution when needed.

## Parameters

| Parameter | Description |
|-----------|-------------|
| `--skill <name> [args...]` | Skill to invoke with its arguments (everything after skill name passed through) |
| `--tasks <id1,id2,...>` | CAT task IDs (dependencies from TaskList) |
| `--sequential` | Force sequential execution (default: parallel for multiple items) |

**Skill arguments**: Everything after the skill name is passed to that skill.
```bash
# Example: delegate shrink-doc with 3 files as arguments
/cat:delegate --skill shrink-doc file1.md file2.md file3.md
#                     ^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^
#                     skill name   skill arguments (passed through)
```

**CAT tasks**: Dependencies are automatically detected from TaskList.
```bash
# Example: delegate 3 CAT tasks (TaskList provides dependency info)
/cat:delegate --tasks 2.1-task-a,2.1-task-b,2.1-task-c
```

## Execution Model

| Items | Default Behavior | `--sequential` |
|-------|------------------|----------------|
| 1 item | 1 subagent | Same |
| N items | N subagents in parallel | N subagents sequentially |

**Parallel is the default** for multiple items. The agent decides concurrency limits internally
based on resource constraints and item count.

## Critical: Unsupervised Execution

**Subagents run without user supervision.** Claude Code does not provide a way for users to view
subagent output while it runs. Users cannot:

- See what the subagent is doing
- Correct mistakes in real-time
- Answer questions or clarify requirements
- Provide feedback during execution

**All decision-making MUST happen in the main agent before delegating.** The subagent prompt must be
comprehensive enough that execution is purely mechanical - following explicit instructions without
judgment calls.

## Hook Inheritance (A008)

**Subagents inherit project hooks automatically** when running in the same project directory.
However, subagents may not follow hook guidance if not explicitly reminded.

**MANDATORY: Include key requirements in subagent prompt:**

```
CRITICAL REQUIREMENTS (enforced by hooks):
- Always decompose code instead of adding PMD suppression annotations
- Always use git merge --ff-only for linear history
- Always use git-filter-repo instead of git filter-branch
- Preserve .git/refs/original unless user explicitly requests deletion
- Include tests for bugfixes in the SAME commit as the fix

COMMIT SEPARATION (M089):
- .claude/rules/ updates → separate config: commit (not bundled with bugfix/feature)
- STATE.md updates → same commit as implementation (M076/M077)

These are ABSOLUTE rules. Violation will be detected and blocked.
```

## When to Use

### For Skills (`--skill`)
- Batch operations on multiple files (e.g., compress 10 files)
- Skill has well-defined inputs and outputs
- Skill produces measurable postconditions (validation scores)

### For CAT Tasks (`--tasks`)
- Task has a well-defined PLAN.md ready for execution
- **All ambiguities resolved** - main agent has made all decisions
- Task is independent enough to execute in isolation
- Parent agent needs to continue with other work

## Subagent Types and Two-Stage Planning

**Planning Subagent (two stages for token efficiency):**

| Stage | Purpose | Output | Tokens |
|-------|---------|--------|--------|
| Stage 1 | High-level approach outlines | 3 brief options + agent_id | ~5K |
| Stage 2 | Detailed implementation spec | Full PLAN.md for selected approach | ~20K |

**Stage 1 prompt template:**
```
Analyze the task and produce HIGH-LEVEL outlines (1-2 sentences each) for:
- Conservative approach: [minimal scope, low risk]
- Balanced approach: [moderate scope, medium risk]
- Aggressive approach: [comprehensive, high risk]

Do NOT produce detailed execution steps yet. Keep outlines brief.
Return your agent_id for later resumption.
```

**Stage 2 prompt (using Task tool with `resume` parameter):**
```
resume: {agent_id from Stage 1}
prompt: "User selected [approach]. Now produce the DETAILED spec with:
- Specific files to modify
- Exact code changes
- Step-by-step execution
- Verification commands"
```

**Implementation Subagent:** Receives completed PLAN.md, executes mechanically.

## Concurrent Execution Safety

This skill respects task-level locking. Before spawning, verify the parent agent holds the task lock.
The lock should have been acquired by `/cat:work`. Subagents inherit lock ownership through
their worktree association (recorded in the lock file).

**MANDATORY: Verify Lock Ownership (M082)**

After any lock acquisition attempt, verify ownership by reading the actual lock file:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
LOCK_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/locks/${TASK_ID}.lock"

# Verify lock file exists and we own it
if [[ ! -f "$LOCK_FILE" ]]; then
  echo "ERROR: Lock file does not exist at $LOCK_FILE"
  echo "Lock was NOT acquired. Another session may own this task."
  exit 1
fi

# Verify session_id matches current session
LOCK_SESSION=$(grep "^session_id=" "$LOCK_FILE" | cut -d= -f2)
if [[ "$LOCK_SESSION" != "$SESSION_ID" ]]; then
  echo "ERROR: Lock owned by different session: $LOCK_SESSION"
  echo "Current session: $SESSION_ID"
  echo "Do NOT proceed - another Claude instance is working on this task."
  exit 1
fi

echo "Lock verified: $LOCK_FILE owned by current session"
```

**Anti-pattern (M082):** Trusting lock script return value without verifying the actual lock file.
Lock directory must be `.claude/cat/locks/` (NOT `/tmp/cat-locks/` or other paths).

**After session restart (M083):** Re-run lock verification commands. Always re-verify state after
restart - the filesystem may have changed while the session was inactive.

## Main Agent Responsibilities (BEFORE Delegating)

1. **Read all relevant code** - Complete exploration before delegating
2. **Make architectural decisions** - Which pattern, which API, which approach
3. **Resolve ambiguities** - If PLAN.md says "handle errors appropriately", decide HOW
4. **Identify edge cases** - Subagent executes happy path unless told otherwise
5. **Write explicit examples** - Code snippets, not prose descriptions
6. **Specify verification** - Exact test commands and expected output

## Prompt Requirements: Zero Decision Delegation

**MANDATORY**: Before delegating, ensure the prompt contains everything needed for mechanical execution.

### What the Prompt MUST Include

| Element | Why Required |
|---------|--------------|
| Clear task type | "Explore and report" OR "Execute these steps" - never both |
| Fail-fast conditions | When to stop and report BLOCKED |
| Exact file paths | For implementation tasks |
| Specific code changes | Before/after examples, not descriptions |
| Test verification steps | Explicit commands to run, expected outcomes |
| Edge cases to handle | Subagent won't discover these independently |
| Commit message format | Exact text, not guidelines |
| **STATE.md update** | Task STATE.md must be updated to completed IN THE SAME COMMIT |

### Fail-Fast Requirements

**CRITICAL**: Every prompt must include fail-fast conditions.

```bash
# Always include:
FAIL-FAST CONDITIONS:
- If [specific condition], report "BLOCKED: [reason]" and stop
- Report status and return to main agent for decisions
- Main agent handles all workarounds and fallback choices
```

Subagents use fail-fast behavior: report BLOCKED and stop. Fallback decisions require user oversight.

### Prompt Completeness Checklist

Before delegating, verify your prompt answers:

- [ ] Is this exploration/research OR implementation? (never both)
- [ ] What are the fail-fast conditions? (when to stop and report BLOCKED)
- [ ] What files to create/modify? (exact paths, for implementation)
- [ ] What code to write? (actual code, not descriptions)
- [ ] What tests to run? (exact commands)
- [ ] What does success look like? (specific criteria)
- [ ] What if the build fails? (fail-fast, not recovery)
- [ ] What commit message to use? (exact text, for implementation)
- [ ] **Does prompt include STATE.md update?** (MUST be in same commit as implementation - M076/M077)
- [ ] **Does prompt include PLAN.md acceptance criteria?** (M260: validation commands, expected scores)
- [ ] **If delegating a skill, does prompt require invoking it?** (M261: "Use /cat:shrink-doc" not "compress")

### Mandatory Subagent Prompt Checklist (A013)

**CRITICAL: Cross-reference recent learnings before delegating.**

Every subagent prompt MUST include these items based on past mistakes:

**STATE.md Requirements (M076, M077, M085, M087, M092):**
```
STATE.md UPDATE (required in SAME commit as implementation):
- Path: .claude/cat/issues/v{major}/v{major}.{minor}/{task-name}/STATE.md
- Set: Status: completed
- Set: Progress: 100%
- Set: Resolution: implemented (MANDATORY - not optional)
- Set: Completed: {YYYY-MM-DD HH:MM}
- Set: Tokens Used: {tokensUsed from .completion.json}
- Include STATE.md in git add before commit
```

**Trust Setting (from cat-config.json, for PLANNING subagents):**
```bash
TRUST_PREF=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
```

| Value | Include in Planning Prompt |
|-------|---------------------------|
| `short` | "Present multiple options for the user to choose from." |
| `medium` | "Present options for meaningful trade-offs. Proceed with balanced for routine." |
| `long` | "Make autonomous decisions. Only present options for significant choices." |

**Curiosity Setting (from cat-config.json, for IMPLEMENTATION subagents):**
```bash
CURIOSITY_PREF=$(jq -r '.curiosity // "low"' .claude/cat/cat-config.json)
```

| Value | Include in Implementation Prompt |
|-------|----------------------------------|
| `low` | "Focus ONLY on the assigned task. Report only task-related issues." |
| `medium` | "NOTE obvious issues in files you touch. Report in .completion.json." |
| `high` | "Actively look for code quality issues. Report ALL findings." |

**Issues Return Format** (subagent writes to .completion.json when curiosity is medium/high):
```json
{
  "status": "success",
  "tokensUsed": 65000,
  "inputTokens": 45000,
  "outputTokens": 20000,
  "compactionEvents": 0,
  "summary": "Implemented feature with full test coverage",
  "discoveredIssues": [
    {
      "file": "src/Example.java",
      "line": 142,
      "type": "code-quality",
      "severity": "medium",
      "description": "Duplicate logic could be extracted",
      "benefitCost": 2.5
    }
  ]
}
```

**Patience Setting — Main Agent Uses This (NOT passed to subagent):**

The patience setting determines what the MAIN AGENT does with issues returned from subagents.
Do NOT include patience instructions in implementation subagent prompts.

| Value | Main Agent Action on Returned Issues |
|-------|--------------------------------------|
| `low` | Resume PLANNER subagent to update plan with fixes, then continue |
| `medium` | Create tasks for discovered issues in CURRENT version backlog |
| `high` | Create tasks for discovered issues in FUTURE version backlog |

**Token Tracking Requirements (A017 - CRITICAL):**

**MAIN AGENT MUST include session ID in prompt** - subagents cannot measure tokens without it.

```
TOKEN MEASUREMENT (required):
Session ID: {paste actual session ID from your CAT SESSION INSTRUCTIONS}
Session file: /home/node/.config/claude/projects/-workspace/{SESSION_ID}.jsonl

On completion, measure tokens:
TOKENS=$(jq -s '[.[] | select(.type == "assistant") | .message.usage |
  select(. != null) | (.input_tokens + .output_tokens)] | add // 0' "$SESSION_FILE")
```

**Token tracking detailed requirements:**
- Track cumulative token usage across the ENTIRE session
- If context compaction occurs, PRESERVE pre-compaction token count
- Write TOTAL tokens (pre-compaction + post-compaction) to .completion.json
- Include: inputTokens, outputTokens, tokensUsed (total), compactionEvents count

**Context Limit Enforcement (A018):**

**MANDATORY: Validate task size BEFORE delegating.**

| Limit | Percentage | Tokens (200K) | Purpose |
|-------|------------|---------------|---------|
| Soft target | 40% | 80,000 | Recommended task size |
| Hard limit | 80% | 160,000 | MANDATORY decomposition above |
| Context limit | 100% | 200,000 | Absolute ceiling |

```bash
# Pre-delegation validation
if [ "${ESTIMATED_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "ERROR: Task estimate (${ESTIMATED_TOKENS}) exceeds hard limit (${HARD_LIMIT})"
  echo "MANDATORY: Decompose task before delegating. Use /cat:decompose-task"
  exit 1
fi
```

**Post-Execution Limit Check:**

After subagent completes (in collect_results), verify actual usage:

```bash
ACTUAL_TOKENS=$(jq -r '.tokensUsed' "$COMPLETION_JSON")
if [ "${ACTUAL_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "EXCEEDED: Subagent used ${ACTUAL_TOKENS} tokens (hard limit: ${HARD_LIMIT})"
  # Trigger learn-from-mistakes with A018 reference
fi
```

**Skill Delegation Requirement (M264):**

When delegating a skill to a subagent, the subagent MUST:
1. Invoke the exact same skill (not apply its "principles" manually)
2. Return the skill's outputs back to the parent agent

```
# ❌ WRONG - Describes principles instead of invoking skill
"Apply the skill's principles: [manual steps]..."

# ✅ CORRECT - Requires skill invocation and output reporting
"Invoke /cat:{skill-name} and return its output to the parent agent."
```

**Skill Postcondition Reporting (M258):**

When delegating a skill that has postconditions, the prompt MUST require reporting:

```
POSTCONDITION REPORTING (required for skills with validation):
When executing /cat:{skill-name}, you MUST report:
- The validation score returned by the skill
- Whether the postcondition was met (e.g., score = 1.0)
- If postcondition failed, what was the actual value
```

| Skill | Postcondition | Required Report |
|-------|---------------|-----------------|
| /cat:shrink-doc | execution_equivalence_score = 1.0 | Score value and pass/fail |
| /cat:compare-docs | semantic comparison complete | Score and component breakdown |
| /cat:stakeholder-review | all stakeholders approve | Individual stakeholder verdicts |

### Verification Checklist

Before invoking Task tool, confirm:

| Checklist Item | Required For | Mistake Ref |
|----------------|--------------|-------------|
| STATE.md path specified | All implementation tasks | M076, M085 |
| Resolution field mentioned | All implementation tasks | M092 |
| CRITICAL REQUIREMENTS block | All tasks | A008 |
| Exact code examples | Non-trivial changes | M062 |
| Fail-fast conditions | All tasks | spawn-subagent |
| **Session ID in prompt** | All tasks | A017, M099, M109 |
| Token measurement instructions | All tasks | A017 |
| **Pre-delegation limit validation** | All tasks | A018 |
| **Skill postcondition reporting** | Skill delegation tasks | M258 |
| **Skill invocation (not principles)** | Skill delegation tasks | M264 |

**Anti-pattern:** Delegating without reviewing this checklist against your prompt.

## Workflow

### Progress Output (MANDATORY)

**For parallel execution:**
```
═══════════════════════════════════════════════════
Delegating: K items (parallel)
═══════════════════════════════════════════════════
◆ Spawning subagent 1/K: item-a...
◆ Spawning subagent 2/K: item-b...

Monitoring K subagents (X% complete | Ys elapsed)
  ✓ item-a: complete (12s, 45K tokens)
  ⏳ item-b: running (8s elapsed)

Collecting results:
  ✓ item-a: collected
  ✓ item-b: collected

✅ Delegation complete: K/K items succeeded
```

**For sequential execution:**
```
═══════════════════════════════════════════════════
Delegating: K items (sequential)
═══════════════════════════════════════════════════
◆ [1/K] item-a...
  ✓ Complete (12s, 45K tokens)

◆ [2/K] item-b...
  ✓ Complete (15s, 52K tokens)

✅ Delegation complete: K/K items succeeded
```

### 1. Parse Arguments

```bash
# Parse parameters
SKILL=""
SKILL_ARGS=()
TASKS=()
SEQUENTIAL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skill)
      SKILL="$2"
      shift 2
      # Everything remaining until next flag is skill args
      while [[ $# -gt 0 && ! "$1" =~ ^-- ]]; do
        SKILL_ARGS+=("$1")
        shift
      done
      ;;
    --tasks)
      IFS=',' read -ra TASKS <<< "$2"
      shift 2
      ;;
    --sequential)
      SEQUENTIAL=true
      shift
      ;;
  esac
done
```

### 2. Determine Execution Mode

```bash
# Count items
if [[ -n "$SKILL" ]]; then
  ITEM_COUNT=${#SKILL_ARGS[@]}
  [[ $ITEM_COUNT -eq 0 ]] && ITEM_COUNT=1  # Skill with no args = 1 invocation
else
  ITEM_COUNT=${#TASKS[@]}
fi

# Determine execution mode
if [[ $ITEM_COUNT -eq 1 ]] || [[ "$SEQUENTIAL" == "true" ]]; then
  EXECUTION_MODE="sequential"
else
  EXECUTION_MODE="parallel"
fi
```

### 3. For CAT Tasks: Analyze Dependencies

When delegating CAT tasks, use TaskList to identify dependencies:

```bash
if [[ ${#TASKS[@]} -gt 0 ]]; then
  # Get dependency info from TaskList
  TASK_INFO=$(TaskList)

  # Build dependency graph
  declare -A BLOCKED_BY
  for task in "${TASKS[@]}"; do
    deps=$(echo "$TASK_INFO" | jq -r ".tasks[] | select(.id == \"$task\") | .blockedBy[]")
    BLOCKED_BY[$task]="$deps"
  done

  # Group into parallel waves based on dependencies
  # Wave 1: tasks with no dependencies in our list
  # Wave 2: tasks that depend on Wave 1 items
  # etc.
fi
```

### 4. Generate Subagent Identifiers

```bash
for item in "${ITEMS[@]}"; do
  UUID=$(uuidgen | cut -c1-8)
  WORKTREE_NAME="${item}-sub-${UUID}"
  BRANCH_NAME="${item}-sub-${UUID}"
  WORKTREE_PATH=".worktrees/${WORKTREE_NAME}"

  # Create worktree
  git worktree add -b "${BRANCH_NAME}" "${WORKTREE_PATH}" HEAD
done
```

### 5. Launch Subagents

**For skill delegation:**
```
Task tool invocation:
  description: "Execute {skill} for {item}"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Execute /cat:{skill} {item}

    WORKING DIRECTORY: ${WORKTREE_PATH}

    CRITICAL REQUIREMENTS: [hook inheritance block]

    POSTCONDITION REPORTING: Report validation score and pass/fail.

    FAIL-FAST: If skill fails validation, report BLOCKED.
```

**For CAT task delegation:**
```
Task tool invocation:
  description: "Execute task {task-id}"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Execute PLAN.md at .claude/cat/issues/v{major}/v{major}.{minor}/{task}/PLAN.md

    WORKING DIRECTORY: ${WORKTREE_PATH}

    [Full prompt including all checklist items]
```

### 6. Execute Based on Mode

**Parallel (default for multiple items):**
```bash
# Spawn all subagents concurrently
for item in "${ITEMS[@]}"; do
  Task tool: spawn subagent for item (run_in_background: true)
done

# Monitor until all complete
while has_running_subagents; do
  for subagent in $(get_completed); do
    collect_results "$subagent"
  done
  sleep 10
done
```

**Sequential (when `--sequential` specified):**
```bash
for item in "${ITEMS[@]}"; do
  Task tool: spawn subagent for item
  wait_for_completion
  collect_results
done
```

### 7. Collect Results

For each completed subagent:
```bash
# Read completion status
COMPLETION_JSON="${WORKTREE}/.completion.json"
STATUS=$(jq -r '.status' "$COMPLETION_JSON")
TOKENS=$(jq -r '.tokensUsed' "$COMPLETION_JSON")

# For skill delegations: extract postcondition values
if [[ -n "$SKILL" ]]; then
  VALIDATION_SCORE=$(jq -r '.validationScore // "N/A"' "$COMPLETION_JSON")
  POSTCONDITION_MET=$(jq -r '.postconditionMet // "unknown"' "$COMPLETION_JSON")
fi
```

### 8. Merge in Dependency Order

```bash
# For parallel execution with dependencies
for wave in "${WAVES[@]}"; do
  for task in "${wave[@]}"; do
    # Verify dependencies are merged
    for dep in ${BLOCKED_BY[$task]}; do
      verify_merged "$dep" || error "Dependency not merged"
    done

    # Merge this task's branch
    merge_subagent "$task"
  done
done
```

### 9. Handle Partial Failures

```yaml
failure_handling:
  strategy: CONTINUE_ON_FAILURE

  on_failure:
    - Record failure details
    - Collect any partial results
    - Continue with successful subagents
    - Mark dependent tasks as blocked
    - Report failures to orchestrator
```

### 10. Report Results

```bash
# Aggregate results
TOTAL_ITEMS=$ITEM_COUNT
SUCCEEDED=$(count_status "success")
FAILED=$(count_status "failed")
TOTAL_TOKENS=$(sum_tokens)

# Report
echo "✅ Delegation complete: ${SUCCEEDED}/${TOTAL_ITEMS} items succeeded"
[[ $FAILED -gt 0 ]] && echo "❌ Failed: ${FAILED} items"
echo "Total tokens: ${TOTAL_TOKENS}"

# For skills: report postcondition summary
if [[ -n "$SKILL" ]]; then
  echo ""
  echo "Postcondition Results:"
  for item in "${ITEMS[@]}"; do
    score=$(get_validation_score "$item")
    status=$(get_postcondition_status "$item")
    echo "  ${item}: score=${score}, ${status}"
  done
fi
```

## Auto-Trigger from Decomposition

When `/cat:work` triggers auto-decomposition (task exceeds context threshold),
this skill is automatically invoked:

```
work → analyze_task_size → (exceeds threshold) → decompose-task → delegate --tasks
```

**Example auto-trigger flow:**
```yaml
# work detects large task
task: 1.2-implement-parser
estimated_tokens: 120000

# Auto-decomposition triggered
decomposed_into:
  - 1.2a-parser-lexer (25K tokens)
  - 1.2b-parser-ast (30K tokens)
  - 1.2c-parser-semantic (25K tokens)

# Parallel plan generated based on dependencies
wave_1: [1.2a, 1.2c]  # Independent, run concurrently
wave_2: [1.2b]        # Depends on 1.2a

# Auto-delegation
/cat:delegate --tasks 1.2a,1.2b,1.2c
# Internally groups by dependencies and executes wave_1 parallel, then wave_2
```

## Examples

### Single Skill Delegation
```bash
/cat:delegate --skill shrink-doc plugin/commands/add.md
# → 1 subagent executes /cat:shrink-doc plugin/commands/add.md
```

### Batch Skill Delegation (Parallel by Default)
```bash
/cat:delegate --skill shrink-doc plugin/commands/add.md plugin/commands/work.md plugin/commands/status.md
# → 3 subagents execute in parallel, each running shrink-doc on one file
```

### Batch Skill Delegation (Sequential Override)
```bash
/cat:delegate --sequential --skill shrink-doc file1.md file2.md file3.md
# → 3 subagents execute sequentially (wait for each before starting next)
```

### CAT Task Delegation
```bash
/cat:delegate --tasks 2.1-task-a,2.1-task-b,2.1-task-c
# → Dependencies analyzed via TaskList
# → Independent tasks execute in parallel
# → Dependent tasks wait for their dependencies
```

### Mixed Dependencies
```yaml
# TaskList shows:
# - 2.1-task-a: no dependencies
# - 2.1-task-b: blocked by 2.1-task-a
# - 2.1-task-c: no dependencies

# Execution:
# Wave 1 (parallel): task-a, task-c
# Wave 2 (after wave 1): task-b
```

## Anti-Patterns

### Parallel is the default (don't specify for batch)

```bash
# ❌ WRONG - Asking for parallel explicitly
/cat:delegate --parallel --skill shrink-doc file1.md file2.md

# ✅ CORRECT - Parallel is automatic for multiple items
/cat:delegate --skill shrink-doc file1.md file2.md
```

### Use --sequential only when order matters

```bash
# ❌ WRONG - Sequential for independent items
/cat:delegate --sequential --skill shrink-doc independent1.md independent2.md

# ✅ CORRECT - Sequential only when order matters
/cat:delegate --sequential --skill migrate-schema v1-to-v2 v2-to-v3
```

### Describe skill principles instead of invoking (M264)

```
# ❌ WRONG - Tells subagent to apply principles
"Compress the file using shrink-doc's principles: reduce size while maintaining equivalence..."

# ✅ CORRECT - Tells subagent to invoke the skill
"Invoke /cat:shrink-doc {file} and report the validation score."
```

### Require postcondition reporting (M258)

```
# ❌ WRONG - No postcondition reporting required
"Run shrink-doc on each file."

# ✅ CORRECT - Explicit postcondition reporting
"Run /cat:shrink-doc on each file. Report: validation_score, postcondition_met (true/false)."
```

### Verify findings through delegation, not direct investigation (M147)

```
# ❌ WRONG - Main agent investigates subagent findings directly
Subagent returns: "DUPLICATE: fix already exists"
Main agent: "Let me verify by reading the file..."

# ✅ CORRECT - Spawn verification subagent if uncertain
Subagent returns: "DUPLICATE: fix already exists"
Main agent: Either accept finding OR spawn verification subagent
```

## Exploration Subagent Structure

The exploration subagent handles three phases internally:

| Phase | Responsibilities | Output |
|-------|------------------|--------|
| **Preparation** | Read PLAN.md, analyze task size, create worktree | `preparation` object |
| **Exploration** | Search codebase, find relevant code, check duplicates | `findings` object |
| **Verification** | Verify findings exist, run preliminary tests | `verification` object |

**Structured JSON Return Format:**
```json
{
  "status": "READY|OVERSIZED|DUPLICATE|BLOCKED",
  "preparation": {
    "estimatedTokens": 45000,
    "percentOfThreshold": 56,
    "worktreePath": "/workspace/.worktrees/1.0-task",
    "branch": "1.0-task"
  },
  "findings": {
    "filesToModify": [...],
    "filesToCreate": [...],
    "patterns": [...],
    "duplicateCheck": "NOT_DUPLICATE",
    "blockers": []
  },
  "verification": {
    "allPathsExist": true,
    "patternsConfirmed": true,
    "preliminaryChecks": "PASSED"
  }
}
```

**Status Values:**

| Status | Meaning | Main Agent Action |
|--------|---------|-------------------|
| `READY` | Task within threshold, ready to proceed | Continue to implementation |
| `OVERSIZED` | Estimated tokens exceed threshold | Trigger decomposition |
| `DUPLICATE` | Task already implemented elsewhere | Mark as duplicate, skip |
| `BLOCKED` | Cannot proceed (missing deps, etc.) | Present blocker to user |

## Related Skills

- `cat:monitor-subagents` - Check status of running subagents
- `cat:collect-results` - Gather results when subagent completes
- `cat:merge-subagent` - Merge subagent work back to task branch
- `cat:decompose-task` - Creates parallelizable subtasks
