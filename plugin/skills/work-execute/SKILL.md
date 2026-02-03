---
description: Execution phase for /cat:work - spawns implementation subagent and collects results
user-invocable: false
---

# Work Phase: Execute

Subagent skill for the execution phase of `/cat:work`. Spawns implementation subagent(s),
monitors progress, and collects results.

## Input

The main agent provides:

```json
{
  "session_id": "uuid",
  "task_id": "2.1-task-name",
  "task_path": "/workspace/.claude/cat/issues/v2/v2.1/task-name",
  "worktree_path": "/workspace/.worktrees/2.1-task-name",
  "estimated_tokens": 45000,
  "trust_level": "low|medium|high"
}
```

## Output Contract

Return JSON on success:

```json
{
  "status": "SUCCESS|PARTIAL|FAILED",
  "tokens_used": 65000,
  "percent_of_context": 32,
  "compaction_events": 0,
  "commits": [
    {"hash": "abc123", "message": "feature: add parser", "type": "feature"}
  ],
  "files_changed": 8,
  "task_metrics": {},
  "discovered_issues": [],
  "verification": {
    "build_passed": true,
    "tests_passed": true,
    "test_count": 15
  }
}
```

**Task-Specific Metrics (M343):** The `task_metrics` field captures metrics meaningful to the task type.
Adapt the requested output based on what the task actually measures:

| Task Type | Metrics to Request |
|-----------|-------------------|
| Code changes | `lines_added`, `lines_removed` |
| Document compression (/shrink-doc) | `tokens_before`, `tokens_after`, `equivalence_score` per file |
| Refactoring | `files_moved`, `symbols_renamed` |
| Test additions | `tests_added`, `coverage_delta` |

When constructing the subagent prompt, specify the metrics that match the task goal.

**CRITICAL: No Example Values in Output Format (M346)**

When specifying metrics in delegation prompts, use descriptive placeholders, NOT example values:

```yaml
# ❌ WRONG - primes agent to report the example value
"equivalence_scores": {"file.md": 1.0, ...}

# ✅ CORRECT - requires agent to obtain actual value
"equivalence_scores": {"<filename>": <actual score from /compare-docs>, ...}
```

**Why this matters**: Showing expected values (like `1.0`) in output format examples primes the
agent to report those values instead of running actual validation. The agent may fabricate scores
to match the expected format rather than invoke the validation skill.

**For document compression tasks specifically**:
- Subagent MUST invoke `/cat:shrink-doc` skill for each file
- Subagent MUST report the ACTUAL score returned by `/compare-docs`

Return JSON on failure:

```json
{
  "status": "FAILED|BLOCKED",
  "message": "Human-readable explanation",
  "partial_work": {
    "commits": [...],
    "files_changed": 3
  },
  "blocker": "Description of what blocked execution"
}
```

## Process

### Step 1: Verify Worktree Branch (M351)

**MANDATORY: Before any work, verify you are on the correct branch.**

```bash
cd "${WORKTREE_PATH}"
CURRENT_BRANCH=$(git branch --show-current)
EXPECTED_BRANCH="${TASK_ID}"  # e.g., "2.1-task-name"

if [[ "$CURRENT_BRANCH" != "$EXPECTED_BRANCH" ]]; then
  echo "FAIL: Wrong branch. Expected: $EXPECTED_BRANCH, Got: $CURRENT_BRANCH"
  exit 1
fi
```

**FAIL-FAST:** If branch doesn't match task ID, return ERROR immediately. Do NOT attempt to fix
by checking out the correct branch - this indicates a prepare phase failure that must be investigated.

**Why this matters (M351):** Without this check, commits may go to the base branch (v2.1) instead
of the isolated task branch, bypassing the review/merge workflow.

### Step 2: Load Task PLAN.md

Read the task's PLAN.md to understand:
- Goal and requirements
- Execution steps
- Acceptance criteria
- Files to modify

**CRITICAL (M333):** The Execution Steps in PLAN.md specify HOW to implement. If they reference
skills like `/cat:shrink-doc`, the subagent MUST invoke those skills - not reimplement their
functionality manually. Skills provide validation (e.g., equivalence scores) that manual
implementation skips.

### Step 3: Prepare Subagent Prompt

Build comprehensive execution plan following delegate/SKILL.md guidelines:
- Include exact file paths
- Include code snippets (not descriptions)
- Include verification commands
- Include commit message format
- Include fail-fast conditions

**Prompt Structure (M350):** Lead with CRITICAL requirements, not context.

Subagents process early content with higher priority. Structure prompts as:

```
1. CRITICAL REQUIREMENTS (skill invocations, blocking rules)
2. Working directory and environment
3. Task goal (1 sentence)
4. Step-by-step instructions with skill invocations inline
5. Output format
6. Context/metadata (SESSION_ID, etc.)
```

**Mandatory CRITICAL REQUIREMENTS (M367):** Always include these in section 1 of every delegation prompt:

```
CRITICAL REQUIREMENTS:
- Update STATE.md to status: completed in SAME commit as implementation
- Include tests for bugfixes in SAME commit as fix
- Always decompose code instead of adding lint suppression
```

These requirements apply to ALL tasks regardless of PLAN.md content.

**Anti-pattern:** Placing skill invocation requirements in "Execution Steps" section after context
sections. The subagent may skip them in favor of earlier-processed content.

### Step 4: Spawn Implementation Subagent

```bash
Task tool invocation:
  description: "Execute task ${TASK_ID}"
  subagent_type: "general-purpose"
  model: "sonnet"  # Code changes need reasoning
  prompt: |
    [Comprehensive execution plan]

    WORKING DIRECTORY: ${WORKTREE_PATH}

    CRITICAL REQUIREMENTS:
    - Always decompose code instead of adding lint suppression
    - Include tests for bugfixes in SAME commit as fix
    - Update STATE.md in SAME commit as implementation

    FAIL-FAST: If blocked, report immediately.
```

### Step 5: Monitor Execution

Wait for subagent completion. If running in background, use TaskOutput to poll.

### Step 6: Collect Results

Read subagent completion output:
- Token usage from session file
- Commit history from git log
- Build/test results from verification

### Step 7: Verify Changes

```bash
# Run build verification
./gradlew compileJava 2>/dev/null || mvn compile 2>/dev/null || npm run build 2>/dev/null

# Run tests
./gradlew test 2>/dev/null || mvn test 2>/dev/null || npm test 2>/dev/null
```

### Step 8: Return Result

Output the JSON result with metrics and verification status.

## Fail-Fast Conditions

- **Wrong branch (M351):** Current branch doesn't match task ID: Return ERROR immediately
- PLAN.md missing or invalid: Return ERROR
- Subagent reports BLOCKED: Return BLOCKED with details
- Build fails: Return FAILED with error output
- Tests fail: Return FAILED with test output

## Context Loaded

This skill loads:
- subagent-delegation.md (delegation principles)
- delegate/SKILL.md (prompt construction)
- build-verification.md (verification steps)

Main agent does NOT need to load these - subagent handles internally.
