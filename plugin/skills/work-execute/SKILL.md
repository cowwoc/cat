---
description: Execution phase for /cat:work - implements the task directly
user-invocable: false
---

# Work Phase: Execute

Subagent skill for the execution phase of `/cat:work`. Implements the task directly
by following PLAN.md execution steps. Does NOT spawn nested subagents (M388).

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

### Step 3: Implement the Task Directly (M388)

**CRITICAL: Implement the task yourself - do NOT spawn another subagent.**

You ARE the implementation agent. Follow the execution steps from PLAN.md directly:

1. **Apply CRITICAL REQUIREMENTS** to all your work:
   - Always decompose code instead of adding lint suppression
   - Include tests for bugfixes in SAME commit as fix
   - Update STATE.md in SAME commit as implementation
   - If PLAN.md references skills (e.g., /cat:shrink-doc), MUST invoke them - do NOT manually reimplement

2. **Execute each step from PLAN.md's Execution Steps section**:
   - If a step says "invoke /cat:skill-name", use the Skill tool to invoke it
   - If a step says to modify files, use Edit/Write tools
   - If a step says to run commands, use Bash tool

3. **Commit your changes** following the commit message format in PLAN.md

4. **Track what you did** for the result JSON:
   - List of commits made
   - Files changed count
   - Task-specific metrics (see Output Contract)

**Why direct implementation (M388):** Previously this skill spawned a nested subagent, creating
a 3-level chain where guidance was lost. You implementing directly keeps the chain at 2 levels
(batch-executor → work-execute) and ensures CRITICAL REQUIREMENTS are followed.

### Step 4: Verify Changes

```bash
# Run build verification
./gradlew compileJava 2>/dev/null || mvn compile 2>/dev/null || npm run build 2>/dev/null

# Run tests
./gradlew test 2>/dev/null || mvn test 2>/dev/null || npm test 2>/dev/null
```

### Step 5: Return Result

Output the JSON result with metrics and verification status.

## Fail-Fast Conditions

- **Wrong branch (M351):** Current branch doesn't match task ID: Return ERROR immediately
- PLAN.md missing or invalid: Return ERROR
- Blocked during implementation: Return BLOCKED with details
- Build fails: Return FAILED with error output
- Tests fail: Return FAILED with test output

## Context Loaded

This skill loads:
- subagent-delegation.md (delegation principles)
- delegate/SKILL.md (prompt construction)
- build-verification.md (verification steps)

Main agent does NOT need to load these - subagent handles internally.
