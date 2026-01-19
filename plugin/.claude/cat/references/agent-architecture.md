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
