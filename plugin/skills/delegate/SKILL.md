---
description: Use for batch skill execution, long-running issues, or isolating verbose skill output
user-invocable: false
---

# Delegate

## Purpose

Delegate issues or skills to subagents in isolated worktrees. Multiple items execute in parallel by
default; use `--sequential` to force sequential execution when needed.

## Parameters

| Parameter | Description |
|-----------|-------------|
| `--skill <name> [args...]` | Skill to invoke with its arguments (everything after skill name passed through) |
| `--issues <id1,id2,...>` | CAT issue IDs (dependencies from TaskList) |
| `--sequential` | Force sequential execution (default: parallel for multiple items) |

**Skill arguments**: Everything after the skill name is passed to that skill.
```bash
# Example: delegate shrink-doc with 3 files as arguments
/cat:delegate --skill shrink-doc file1.md file2.md file3.md
#                     ^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^
#                     skill name   skill arguments (passed through)
```

**CAT issues**: Dependencies are automatically detected from TaskList.
```bash
# Example: delegate 3 CAT issues (TaskList provides dependency info)
/cat:delegate --issues 2.1-issue-a,2.1-issue-b,2.1-issue-c
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

## Model Selection for Subagents

**MANDATORY: Always specify a model explicitly. Never use the default.**

Choose the model based on issue complexity:

| Issue Type | Model | Reasoning |
|-----------|-------|-----------|
| Skill invocation (skill does the work) | `haiku` | Skill handles complexity; subagent just invokes |
| Simple file operations | `haiku` | Explicit instructions, no reasoning needed |
| Run commands, check output | `haiku` | Purely mechanical execution |
| Code refactoring | `sonnet` | Requires understanding patterns and context |
| Multi-file changes | `sonnet` | Needs to maintain consistency across files |
| Exploration/research | `sonnet` | Requires judgment about what's relevant |
| Complex logic changes | `sonnet` | Must reason about correctness |
| Two-stage planning (Stage 1) | `opus` | Generating high-quality approach options requires deep reasoning |
| Critical validation gates | `opus` | Asymmetric failure costs justify higher accuracy |

**Decision rule:** If the execution plan can be followed with zero reasoning (copy-paste level
explicit), use `haiku`. If the subagent needs to understand WHY to do something correctly,
use `sonnet`. If failure would be very costly or the task requires generating novel approaches,
consider `opus`.

### When to Use Opus (Rare Cases)

**Opus is the exception, not the default.** Most delegated work should use haiku or sonnet.

Use Opus only when:

1. **Two-stage planning (Stage 1)** - Generating approach options requires creative problem-solving
   and deep architectural understanding. The quality of options directly affects the whole
   implementation path.

2. **Critical validation gates** - When the cost of a false positive (incorrectly passing) is much
   higher than the cost of running a more capable model. Examples:
   - Security review of authentication changes
   - Validating semantic equivalence of compressed documentation
   - Final quality gate before production deployment

3. **Complex architectural analysis** - Evaluating tradeoffs across multiple systems, identifying
   non-obvious dependencies, or reasoning about emergent behavior.

**Signal to reconsider delegation:** If you find yourself reaching for Opus, ask whether this work
should be delegated at all. Work requiring Opus-level reasoning often benefits from:
- Main agent handling it directly (with user oversight)
- Breaking into smaller pieces that sonnet can handle
- More explicit specifications that reduce reasoning requirements

**Anti-pattern:**
```
❌ model: "opus" for mechanical file operations (wasteful)
❌ model: "opus" for straightforward code changes (sonnet suffices)
❌ model: "opus" as a "just to be safe" default (defeats cost efficiency)
✅ model: "opus" for Stage 1 planning with 3+ viable approaches
✅ model: "opus" for security-critical validation gates
```

**Anti-pattern:**
```
❌ Task tool: subagent_type: "general-purpose" (missing model - uses expensive default)
❌ Task tool: model: "haiku" for code refactoring (will likely fail)
✅ Task tool: model: "haiku" for "/cat:shrink-doc file.md" (skill does the work)
✅ Task tool: model: "sonnet" for "refactor these 4 handlers" (needs reasoning)
```

## Hook Inheritance (A008)

**Subagents inherit project hooks automatically** when running in the same project directory.
However, subagents may not follow hook guidance if not explicitly reminded.

**MANDATORY: Include key requirements in subagent prompt:**

```
CRITICAL REQUIREMENTS (enforced by hooks):
- Always decompose code instead of adding lint suppression annotations
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

### For CAT Issues (`--issues`)
- Issue has a well-defined PLAN.md ready for execution
- **All ambiguities resolved** - main agent has made all decisions
- Issue is independent enough to execute in isolation
- Parent agent needs to continue with other work

## Subagent Types and Two-Stage Planning

**Planning Subagent (two stages for token efficiency):**

| Stage | Purpose | Output | Tokens |
|-------|---------|--------|--------|
| Stage 1 | High-level approach outlines | 3 brief options + agent_id | ~5K |
| Stage 2 | Detailed implementation spec | Full PLAN.md for selected approach | ~20K |

**Stage 1 prompt template:**
```
Analyze the issue and produce HIGH-LEVEL outlines (1-2 sentences each) for:
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

This skill respects issue-level locking. Before spawning, verify the parent agent holds the issue lock.
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
  echo "Lock was NOT acquired. Another session may own this issue."
  exit 1
fi

# Verify session_id matches current session
LOCK_SESSION=$(grep "^session_id=" "$LOCK_FILE" | cut -d= -f2)
if [[ "$LOCK_SESSION" != "$SESSION_ID" ]]; then
  echo "ERROR: Lock owned by different session: $LOCK_SESSION"
  echo "Current session: $SESSION_ID"
  echo "Do NOT proceed - another Claude instance is working on this issue."
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
7. **Produce comprehensive execution plan** - Numbered steps the subagent follows mechanically

## Comprehensive Execution Plan Format

**MANDATORY: Every subagent prompt must include a numbered execution plan.**

The plan must be detailed enough that the subagent can follow it mechanically without making
decisions. Each step should be atomic and verifiable.

**Execution Plan Template:**
```
## Execution Plan

Follow these steps IN ORDER. Do not skip steps. Do not make decisions not covered here.

### Step 1: [Action verb] [specific target]
- Input: [exact file path or data source]
- Action: [precise operation to perform]
- Output: [expected result or artifact]
- Verification: [how to confirm step succeeded]

### Step 2: [Action verb] [specific target]
...

### Step N: Commit and Report
- Stage files: [exact list]
- Commit message: "[exact message text]"
- Report: [specific metrics to include in completion]

## Fail-Fast Conditions
If ANY of these occur, STOP and report BLOCKED:
- [Condition 1]
- [Condition 2]
```

**Example - Good Execution Plan:**
```
## Execution Plan

### Step 1: Read the source file
- Input: plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/GetWorkOutput.java
- Action: Read entire file content
- Output: File content in memory
- Verification: File exists and is readable

### Step 2: Add the Approach record
- Input: File content from Step 1
- Action: Add after line 15 (after class declaration):
  ```java
  public record Approach(String name, String description, String risk, int scope, int configAlignment)
  {
    public Approach
    {
      requireThat(name, "name").isNotBlank();
      requireThat(description, "description").isNotBlank();
      requireThat(risk, "risk").isNotBlank();
      requireThat(scope, "scope").isPositive();
      requireThat(configAlignment, "configAlignment").isBetween(0, 100);
    }
  }
  ```
- Output: Modified file with record added
- Verification: Record appears after class declaration

### Step 3: Verify compilation
- Action: Run `mvn compile -f plugin/hooks/java/pom.xml`
- Output: BUILD SUCCESS
- Verification: Exit code 0

### Step 4: Commit changes
- Stage: plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/GetWorkOutput.java
- Commit message: "feature: add Approach record to GetWorkOutput"
- Report: Files changed, lines added
```

**Anti-pattern - Vague Plan:**
```
❌ "Update the handler to use parameters instead of placeholders"
❌ "Refactor following the pattern in GetAddOutput"
❌ "Make appropriate changes to support the new design"
```

## Prompt Requirements: Zero Decision Delegation

**MANDATORY**: Before delegating, ensure the prompt contains everything needed for mechanical execution.

### What the Prompt MUST Include

| Element | Why Required |
|---------|--------------|
| Clear issue type | "Explore and report" OR "Execute these steps" - never both |
| Fail-fast conditions | When to stop and report BLOCKED |
| Exact file paths | For implementation issues |
| Specific code changes | Before/after examples, not descriptions |
| Test verification steps | Explicit commands to run, expected outcomes |
| Edge cases to handle | Subagent won't discover these independently |
| Commit message format | Exact text, not guidelines |
| **STATE.md update** | Issue STATE.md must be updated to completed IN THE SAME COMMIT |

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

**CRITICAL: Before delegating, load and follow the full checklist.**

<conditional_load trigger="before_delegation">

@SUBAGENT-PROMPT-CHECKLIST.md

</conditional_load>

The checklist covers: STATE.md requirements, trust/curiosity settings, token tracking,
context limits, and skill delegation rules. Load it before constructing any subagent prompt.

## Workflow

### Progress Output (MANDATORY)

**Check for SCRIPT OUTPUT DELEGATE PROGRESS in context.**

If found: Use the appropriate template (PARALLEL or SEQUENTIAL) and replace placeholders.

If NOT found: **FAIL immediately.**

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/check-hooks-loaded.sh" "delegate progress" "/cat:delegate"
if [[ $? -eq 0 ]]; then
  echo "ERROR: SCRIPT OUTPUT DELEGATE PROGRESS not found."
  echo "Handler delegate_handler.py should have provided this via additionalContext."
  echo "Check that hooks are properly loaded."
fi
```

Output the error and STOP. Do NOT manually construct progress output.

### 1. Parse Arguments

```bash
# Parse parameters
SKILL=""
SKILL_ARGS=()
ISSUES=()
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
    --issues)
      IFS=',' read -ra ISSUES <<< "$2"
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
  ITEM_COUNT=${#ISSUES[@]}
fi

# Determine execution mode
if [[ $ITEM_COUNT -eq 1 ]] || [[ "$SEQUENTIAL" == "true" ]]; then
  EXECUTION_MODE="sequential"
else
  EXECUTION_MODE="parallel"
fi
```

### 3. For CAT Issues: Analyze Dependencies

When delegating CAT issues, use TaskList to identify dependencies:

```bash
if [[ ${#ISSUES[@]} -gt 0 ]]; then
  # Get dependency info from TaskList
  TASK_INFO=$(TaskList)

  # Build dependency graph
  declare -A BLOCKED_BY
  for issue in "${ISSUES[@]}"; do
    deps=$(echo "$TASK_INFO" | jq -r ".issues[] | select(.id == \"$issue\") | .blockedBy[]")
    BLOCKED_BY[$issue]="$deps"
  done

  # Group into parallel waves based on dependencies
  # Wave 1: issues with no dependencies in our list
  # Wave 2: issues that depend on Wave 1 items
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

**MANDATORY: Always specify model explicitly based on issue type.**

**For skill delegation (haiku - skill does the reasoning):**
```
Task tool invocation:
  description: "Execute {skill} for {item}"
  subagent_type: "general-purpose"
  model: "haiku"  # Skill handles complexity
  prompt: |
    Execute /cat:{skill} {item}

    WORKING DIRECTORY: ${WORKTREE_PATH}

    CRITICAL REQUIREMENTS: [hook inheritance block]

    ## Execution Plan
    [Numbered steps - see Comprehensive Execution Plan Format above]

    POSTCONDITION REPORTING: Report validation score and pass/fail.

    FAIL-FAST: If skill fails validation, report BLOCKED.
```

**For CAT issue delegation - simple issues (haiku):**
```
Task tool invocation:
  description: "Execute issue {issue-id}"
  subagent_type: "general-purpose"
  model: "haiku"  # Simple/mechanical issue
  prompt: |
    Execute the following plan mechanically. Do not deviate.

    WORKING DIRECTORY: ${WORKTREE_PATH}

    ## Execution Plan
    [Numbered steps - 100% explicit, copy-paste level detail]

    CRITICAL REQUIREMENTS: [hook inheritance block]
```

**For CAT issue delegation - code changes (sonnet):**
```
Task tool invocation:
  description: "Execute issue {issue-id}"
  subagent_type: "general-purpose"
  model: "sonnet"  # Code refactoring/multi-file changes
  prompt: |
    Execute the following plan. Use judgment to maintain code quality.

    WORKING DIRECTORY: ${WORKTREE_PATH}

    ## Execution Plan
    [Numbered steps with context about WHY each change is needed]

    CRITICAL REQUIREMENTS: [hook inheritance block]

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

**Sequential with Subagent Reuse** (efficiency optimization):

When processing multiple items sequentially, the same subagent can handle consecutive items IF context usage remains under the **soft limit** (see agent-architecture.md § Context Limit Constants for current values).

**Reuse Protocol:**
```bash
AGENT_ID=""
CONTEXT_USED=0
# Reference: agent-architecture.md § Context Limit Constants
SOFT_LIMIT=$((CONTEXT_LIMIT * SOFT_TARGET_PCT / 100))

for item in "${ITEMS[@]}"; do
  if [[ -z "$AGENT_ID" ]] || [[ $CONTEXT_USED -ge $SOFT_LIMIT ]]; then
    # Spawn new subagent
    Task tool: spawn subagent for item
    AGENT_ID={returned_agent_id}
  else
    # Resume existing subagent
    Task tool: resume: $AGENT_ID with next item
  fi

  wait_for_completion
  CONTEXT_USED={tokens_from_completion}
  collect_results
done
```

**Resume invocation syntax:**
```
Task tool:
  subagent_type: "general-purpose"
  resume: {previous_agent_id}
  description: "Process next item"
  prompt: |
    Process the next item: {next_item}
    [Same skill/issue instructions]
    Report approximate token usage after completion.
```

**Why soft limit for reuse**: Quality degrades after 40-50% context usage (agent-architecture.md § Quality Degradation). Spawning fresh subagents above soft limit maintains output quality.

**When NOT to reuse:**
- Items require completely different skill invocations
- Previous item failed (fresh context may help)
- Context already near soft limit
- Skills requiring isolation to avoid bias (e.g., `/cat:compare-docs` - each comparison must have fresh context to prevent prior comparisons from influencing judgment)

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
  for issue in "${wave[@]}"; do
    # Verify dependencies are merged
    for dep in ${BLOCKED_BY[$issue]}; do
      verify_merged "$dep" || error "Dependency not merged"
    done

    # Merge this issue's branch
    merge_subagent "$issue"
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
    - Mark dependent issues as blocked
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

When `/cat:work` triggers auto-decomposition (issue exceeds context threshold),
this skill is automatically invoked:

```
work → analyze_task_size → (exceeds threshold) → decompose-issue → delegate --issues
```

**Example auto-trigger flow:**
```yaml
# work detects large issue
issue: 1.2-implement-parser
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
/cat:delegate --issues 1.2a,1.2b,1.2c
# Internally groups by dependencies and executes wave_1 parallel, then wave_2
```

## Examples

### Single Skill Delegation
```bash
/cat:delegate --skill shrink-doc plugin/skills/add/SKILL.md
# → 1 subagent executes /cat:shrink-doc plugin/skills/add/SKILL.md
```

### Batch Skill Delegation (Parallel by Default)
```bash
/cat:delegate --skill shrink-doc plugin/skills/add/SKILL.md plugin/skills/work/SKILL.md plugin/skills/status/SKILL.md
# → 3 subagents execute in parallel, each running shrink-doc on one file
```

### Batch Skill Delegation (Sequential Override)
```bash
/cat:delegate --sequential --skill shrink-doc file1.md file2.md file3.md
# → 3 subagents execute sequentially (wait for each before starting next)
```

### CAT Issue Delegation
```bash
/cat:delegate --issues 2.1-issue-a,2.1-issue-b,2.1-issue-c
# → Dependencies analyzed via TaskList
# → Independent issues execute in parallel
# → Dependent issues wait for their dependencies
```

### Mixed Dependencies
```yaml
# TaskList shows:
# - 2.1-issue-a: no dependencies
# - 2.1-issue-b: blocked by 2.1-issue-a
# - 2.1-issue-c: no dependencies

# Execution:
# Wave 1 (parallel): issue-a, issue-c
# Wave 2 (after wave 1): issue-b
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
| **Preparation** | Read PLAN.md, analyze issue size, create worktree | `preparation` object |
| **Exploration** | Search codebase, find relevant code, check duplicates | `findings` object |
| **Verification** | Verify findings exist, run preliminary tests | `verification` object |

**Structured JSON Return Format:**
```json
{
  "status": "READY|OVERSIZED|DUPLICATE|BLOCKED",
  "preparation": {
    "estimatedTokens": 45000,
    "percentOfThreshold": 56,
    "worktreePath": "/workspace/.worktrees/1.0-issue",
    "branch": "1.0-issue"
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
| `READY` | Issue within threshold, ready to proceed | Continue to implementation |
| `OVERSIZED` | Estimated tokens exceed threshold | Trigger decomposition |
| `DUPLICATE` | Issue already implemented elsewhere | Mark as duplicate, skip |
| `BLOCKED` | Cannot proceed (missing deps, etc.) | Present blocker to user |

## Waiting for Subagent Completion (M293)

**CRITICAL: Claude does NOT automatically wake up when background issues complete.**

When using `run_in_background: true`:
- Completion notifications appear as system reminders
- These do NOT trigger automatic response
- Conversation blocks until user sends another message

**To wait for results, use `TaskOutput` with `block: true`:**

```bash
# Spawn in background → returns task_id
# Wait: TaskOutput task_id="{id}" block=true timeout=120000
```

| Scenario | Approach |
|----------|----------|
| Single subagent, need results | Blocking (no `run_in_background`) |
| Multiple subagents, parallel | Background + `TaskOutput` polling |
| Long-running, user expects updates | Background + tell user to check back |
| High-trust autonomous | Background + `TaskOutput` with `block: true` |

**Anti-pattern (M293):**
```
❌ "I'll wait for subagents to complete and notify me"
✅ "Subagents running. Use TaskOutput to check, or prompt me when ready."
```

## Related Skills

- `cat:monitor-subagents` - Check status of running subagents
- `cat:collect-results` - Gather results when subagent completes
- `cat:merge-subagent` - Merge subagent work back to issue branch
- `cat:decompose-issue` - Creates parallelizable subtasks
