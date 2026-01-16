# Agent Architecture

## Main Agent

### Responsibilities

| Area | Actions |
|------|---------|
| Orchestration | Coordinate subagent execution |
| Planning | Read code, make decisions, decompose tasks |
| Metadata | Create/update planning documents |
| Git operations | Branch creation, merging |
| Conflict resolution | Resolve merge conflicts |
| Queue processing | Handle subagent returns serially |
| State updates | Update STATE.md after completions |

### Does NOT

- Write production code directly
- Execute implementation tasks
- Work in worktrees except for merging

### No Exceptions for "Small Fixes" (M097)

**MANDATORY**: Main agent NEVER edits source code directly, even for:
- Merge conflict resolution (spawn subagent)
- 1-3 line fixes (spawn subagent)
- Compilation error fixes (spawn subagent)
- Style corrections (spawn subagent)

**Anti-pattern**: "This is just a small fix, I'll do it directly" → ALWAYS delegate.

The delegation boundary exists for quality and traceability, not efficiency.
"Quick fixes" bypass fresh context and create untraceable changes.

### Work Request Handling

**Default behavior:** When a user requests work, the main agent proposes task creation first.

This ensures all work is tracked in the planning structure. The agent should respond:

> "I'll create a task for this so it's tracked properly. Let me add it via `/cat:add`."

**Trust-level variations:**

| Trust Level | Behavior |
|-------------|----------|
| `low` | Always ask before any work, even trivial changes |
| `medium` | Propose task creation for non-trivial work; ask for trivial |
| `high` | Create task automatically via `/cat:add`, then proceed to `/cat:work` |

**Trivial work definition:**
- Single-line changes (typos, import fixes, obvious corrections)
- Changes affecting only 1 file
- No logic changes, purely cosmetic

**User override:** User can bypass with phrases like "just do it", "quick fix", or "no task needed".
When overridden, the agent should still warn: "Working directly without task tracking."

**Example interactions:**

User: "Fix the bug where parsing fails on empty input"
Agent (medium trust): "I'll create a task for this so it's tracked properly. Running `/cat:add fix parsing failure on empty input`..."

User: "Fix the typo in README"
Agent (medium trust): "This looks like a trivial fix. Should I create a task for tracking, or just fix it directly?"

User: "Just fix it"
Agent: "Working directly without task tracking. [proceeds to fix]"

**What this does NOT change:**
- `/cat:work` workflow remains unchanged
- Subagent delegation rules remain unchanged
- Main agent still does not write production code directly

### Worktree Usage

Main agent uses worktrees ONLY for:
- Merging subagent branches into task branches
- Merging task branches into main

## Subagent Types

### Implementation Subagent

Standard subagent for executing coding tasks.

| Area | Actions |
|------|---------|
| Execution | Perform coding tasks |
| Isolation | Work in dedicated worktree |
| Token tracking | Monitor context usage |
| Compaction detection | Track summary events |
| Reporting | Return metrics on completion |
| Fail-fast | Return immediately on plan issues |

### Exploration Subagent

Specialized subagent for task preparation, codebase exploration, and verification.
Handles three phases internally to hide noisy tool calls from user.

| Phase | Responsibilities | Output |
|-------|------------------|--------|
| **Preparation** | Read PLAN.md, analyze task size, create worktree | Estimate and worktree path |
| **Exploration** | Search codebase, find patterns, check duplicates | File locations and patterns |
| **Verification** | Validate findings, confirm paths exist | Validation results |

**Returns structured JSON** for clean main agent display:

```json
{
  "status": "READY|OVERSIZED|DUPLICATE|BLOCKED",
  "preparation": {"estimatedTokens": 45000, "worktreePath": "..."},
  "findings": {"filesToModify": [...], "patterns": [...]},
  "verification": {"allPathsExist": true}
}
```

**Benefits:**
- User sees clean summary instead of Bash/Read/Grep tool calls
- Main agent receives structured data for decision-making
- Preparation work isolated from main agent context

See `spawn-subagent` skill → "Expanded Exploration Subagent" for full details.

## Subagent Responsibilities

### Token Tracking

Subagents read session file:
```
/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl
```

Collect:
1. Sum `input_tokens + output_tokens`
2. Count `type: "summary"` entries

### Return Protocol

**MANDATORY**: On completion, subagent MUST output a completion report.

**Format**:
```json
{
  "status": "success|failure",
  "tokensUsed": 75000,
  "compactionEvents": 0,
  "summary": "Brief description of work completed"
}
```

**How to calculate**:
```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"

# Total tokens
TOTAL=$(jq -s '[.[] | select(.type == "assistant") | .message.usage |
  (.input_tokens + .output_tokens)] | add' "${SESSION_FILE}")

# Compaction events
COMPACTIONS=$(jq -s '[.[] | select(.type == "summary")] | length' "${SESSION_FILE}")
```

**Output**: Print the JSON to stdout before exiting. Main agent will capture this.

## Communication Flow

```
Main Agent
    |
    +---> Spawn Subagent 1 (task-a)
    |         |
    +---> Spawn Subagent 2 (task-b)
    |         |
    v         v
    [Wait for completions]
         |
         v
    Process returns serially
         |
         v
    Merge branches
         |
         v
    Update STATE.md
```

## Parallel Execution

- No arbitrary limits on concurrent subagents
- Main agent manages based on available resources
- Independent tasks execute simultaneously
- Dependent tasks wait for prerequisites

## Context Limit Constants (A018)

**These values are FIXED and defined here as the single source of truth.**

| Constant | Value | Purpose |
|----------|-------|---------|
| `CONTEXT_LIMIT` | 200000 | Claude's context window (tokens) |
| `SOFT_TARGET_PCT` | 40 | Ideal task size percentage |
| `HARD_LIMIT_PCT` | 80 | Maximum safe execution percentage |

**Derived values:**
- Soft target: 80,000 tokens (40% of 200K)
- Hard limit: 160,000 tokens (80% of 200K)

**Usage in scripts:**
```bash
# Reference: agent-architecture.md § Context Limit Constants
CONTEXT_LIMIT=200000
SOFT_TARGET_PCT=40
HARD_LIMIT_PCT=80
SOFT_TARGET=$((CONTEXT_LIMIT * SOFT_TARGET_PCT / 100))  # 80000
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))    # 160000
```

**Why fixed, not configurable:**
- Claude's context window is model-determined, not user preference
- Quality thresholds are based on empirical testing
- Consistency across all CAT installations ensures reliable behavior

### Limit Hierarchy

| Limit | Percentage | Tokens (200K) | Purpose |
|-------|------------|---------------|---------|
| Soft target | 40% | 80,000 | Recommended task size for optimal quality |
| Hard limit | 80% | 160,000 | Maximum allowed - MANDATORY decomposition above |
| Context limit | 100% | 200,000 | Absolute ceiling - compaction occurs |

### Main Agent Responsibilities

**Pre-Spawn (BEFORE spawning any subagent):**

1. Calculate estimated tokens from task analysis
2. Calculate hard limit: `HARD_LIMIT = CONTEXT_LIMIT * 80 / 100`
3. Validate: `estimate < hard_limit`
4. If validation fails: MANDATORY decomposition (do NOT spawn)

**Post-Spawn (AFTER subagent completes):**

1. Collect actual token usage from `.completion.json`
2. Compare actual against hard limit
3. Flag violations with EXCEEDED status
4. Trigger `/cat:learn-from-mistakes` for each violation

### Enforcement Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    MAIN AGENT (Pre-Spawn)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Analyze task → estimate tokens                              │
│                     │                                           │
│                     v                                           │
│  2. Calculate: HARD_LIMIT = CONTEXT_LIMIT * 80%                │
│                     │                                           │
│                     v                                           │
│  3. Validate: estimate < HARD_LIMIT?                           │
│                     │                                           │
│           ┌────────┴────────┐                                  │
│           │                 │                                   │
│           v                 v                                   │
│        YES: Spawn      NO: MANDATORY                            │
│        subagent        decomposition                            │
│           │            (do NOT spawn)                           │
│           v                                                     │
│  4. Monitor execution                                           │
│           │                                                     │
│           v                                                     │
│  5. Collect results → actual tokens                             │
│           │                                                     │
│           v                                                     │
│  6. Check: actual >= HARD_LIMIT?                               │
│           │                                                     │
│     ┌─────┴─────┐                                              │
│     │           │                                               │
│     v           v                                               │
│   NO: OK     YES: Flag EXCEEDED                                 │
│              + learn-from-mistakes                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Aggregate Reporting Format

For multi-subagent tasks, generate aggregate token report:

```
## Aggregate Token Report

| Subagent | Tokens | % of Limit | Status |
|----------|--------|------------|--------|
| task-sub-a1b2c3d4 | 65,000 | 32% | OK |
| task-sub-e5f6g7h8 | 170,000 | 85% | EXCEEDED |
| task-sub-i9j0k1l2 | 45,000 | 22% | OK |

**Total tokens:** 280,000
**Subagents exceeded hard limit:** 1
```

### Violation Handling Process

When a subagent exceeds the hard limit:

1. **Flag:** Mark subagent status as EXCEEDED in aggregate report
2. **Record:** Invoke `/cat:learn-from-mistakes` with:
   - Mistake reference: A018
   - Subagent ID
   - Actual tokens used
   - Hard limit value
   - Task context
3. **Analyze:** Review why estimation failed
4. **Improve:** Update estimation factors based on pattern
