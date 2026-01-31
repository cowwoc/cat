---
description: Review phase for /cat:work - runs stakeholder review and handles approval
user-invocable: false
---

# Work Phase: Review

Subagent skill for the review phase of `/cat:work`. Runs multi-perspective stakeholder review
and determines if implementation is ready for merge.

## Input

The main agent provides:

```json
{
  "session_id": "uuid",
  "task_id": "2.1-task-name",
  "task_path": "/workspace/.claude/cat/issues/v2/v2.1/task-name",
  "worktree_path": "/workspace/.worktrees/2.1-task-name",
  "trust_level": "low|medium|high",
  "verify_level": "none|changed|all",
  "execution_result": {
    "commits": [...],
    "files_changed": 8
  }
}
```

## Output Contract

Return JSON on success:

```json
{
  "status": "APPROVED|CONCERNS|REJECTED",
  "stakeholders_run": ["requirements", "architect", "security", "design", "testing"],
  "stakeholders_skipped": [
    {"name": "ux", "reason": "No UI changes detected"},
    {"name": "legal", "reason": "No licensing keywords"}
  ],
  "concerns": {
    "critical": [],
    "high": [
      {"stakeholder": "security", "issue": "Input not validated", "file": "Parser.java:45"}
    ],
    "medium": []
  },
  "recommendation": "Proceed with noted concerns|Fix before merge|Reject"
}
```

## Process

### Step 1: Check Verify Level

```bash
if [[ "$VERIFY_LEVEL" == "none" ]]; then
  echo '{"status":"APPROVED","stakeholders_run":[],"concerns":{},"recommendation":"Skipped per config"}'
  exit 0
fi
```

### Step 2: Analyze Context for Stakeholder Selection

Detect which stakeholders are relevant based on:
- Issue type (documentation, refactor, bugfix, performance)
- Keywords in PLAN.md (license, UI, API, security)
- Changed file patterns (test files, UI files, security files)

See stakeholder-review/SKILL.md for full selection algorithm.

### Step 3: Spawn Stakeholder Subagents

For each selected stakeholder, spawn a reviewer subagent:

```bash
Task tool invocation:
  description: "Review as ${STAKEHOLDER}"
  subagent_type: "general-purpose"
  model: "sonnet"  # Reviews require reasoning about code quality
  prompt: |
    You are the ${STAKEHOLDER} stakeholder reviewing implementation.

    ## Your Role
    [Content from stakeholders/${STAKEHOLDER}.md]

    ## Files Changed
    [Full file content for holistic review]

    ## Changes (Diff)
    [Git diff for reference]

    Return JSON: {"approval": "APPROVED|CONCERNS|REJECTED", "concerns": [...]}
```

### Step 4: Collect Reviews

Wait for all stakeholder subagents to complete. Parse each response as JSON.

### Step 5: Aggregate Results

Count concerns by severity:
- Any CRITICAL: Overall REJECTED
- 3+ HIGH across all: Overall REJECTED
- Any HIGH: Overall CONCERNS
- Otherwise: APPROVED

### Step 6: Return Result

Output the aggregated JSON result.

## Fail-Fast Conditions

- Stakeholder returns invalid JSON: Treat as CONCERNS
- All stakeholders fail: Return ERROR

## Context Loaded

This skill loads:
- stakeholder-review/SKILL.md (review orchestration)
- stakeholders/index.md (stakeholder definitions)
- stakeholders/*.md (individual stakeholder criteria)

Main agent does NOT need to load these - subagent handles internally.
