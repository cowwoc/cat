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
  "lines_added": 250,
  "lines_removed": 45,
  "discovered_issues": [],
  "verification": {
    "build_passed": true,
    "tests_passed": true,
    "test_count": 15
  }
}
```

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

### Step 1: Load Task PLAN.md

Read the task's PLAN.md to understand:
- Goal and requirements
- Execution steps
- Acceptance criteria
- Files to modify

**CRITICAL (M333):** The Execution Steps in PLAN.md specify HOW to implement. If they reference
skills like `/cat:shrink-doc`, the subagent MUST invoke those skills - not reimplement their
functionality manually. Skills provide validation (e.g., equivalence scores) that manual
implementation skips.

### Step 2: Prepare Subagent Prompt

Build comprehensive execution plan following delegate/SKILL.md guidelines:
- Include exact file paths
- Include code snippets (not descriptions)
- Include verification commands
- Include commit message format
- Include fail-fast conditions

### Step 3: Spawn Implementation Subagent

```bash
Task tool invocation:
  description: "Execute task ${TASK_ID}"
  subagent_type: "general-purpose"
  model: "sonnet"  # Code changes need reasoning
  prompt: |
    [Comprehensive execution plan]

    WORKING DIRECTORY: ${WORKTREE_PATH}

    CRITICAL REQUIREMENTS:
    - Always decompose code instead of adding PMD suppression
    - Include tests for bugfixes in SAME commit as fix
    - Update STATE.md in SAME commit as implementation

    FAIL-FAST: If blocked, report immediately.
```

### Step 4: Monitor Execution

Wait for subagent completion. If running in background, use TaskOutput to poll.

### Step 5: Collect Results

Read subagent completion output:
- Token usage from session file
- Commit history from git log
- Build/test results from verification

### Step 6: Verify Changes

```bash
# Run build verification
./gradlew compileJava 2>/dev/null || mvn compile 2>/dev/null || npm run build 2>/dev/null

# Run tests
./gradlew test 2>/dev/null || mvn test 2>/dev/null || npm test 2>/dev/null
```

### Step 7: Return Result

Output the JSON result with metrics and verification status.

## Fail-Fast Conditions

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
