---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Command Optimizer Skill

**Purpose**: Analyze session tool_use history to identify optimization opportunities and categorize
tool outputs by user relevance. Provides actionable recommendations for batching, caching, and output
summarization.

## When to Use

- After completing a complex issue, to identify efficiency improvements
- When investigating slow sessions or high token usage
- To generate configuration rules for output hiding/summarization
- When preparing recommendations for Claude Code UX improvements
- After sessions with many redundant operations

## Prerequisites

This skill builds on the `get-history` skill for session data access. The session ID must be available
via `${CLAUDE_SESSION_ID}`.

## Usage

```bash
/cat:command-optimizer
```

Or invoke programmatically with a specific session file:

```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"
```

## Analysis Steps

### Step 1: Run Session Analysis

Execute the session analyzer to extract all mechanical data:

```bash
"${CLAUDE_PLUGIN_ROOT}/hooks/bin/session-analyzer" "$SESSION_FILE"
```

The skill outputs a JSON object with:
- `main`: Main agent analysis containing:
  - `tool_frequency`: Count of each tool type used
  - `token_usage`: Token consumption per tool type
  - `output_sizes`: Sizes of tool outputs from tool_result entries
  - `cache_candidates`: Repeated identical operations
  - `batch_candidates`: Consecutive similar operations
  - `parallel_candidates`: Independent operations in same message
  - `pipeline_candidates`: Dependent operations where next phase can start with partial output from current phase
  - `script_extraction_candidates`: Deterministic multi-step operations that could be extracted into standalone scripts
  - `summary`: Overall session statistics
- `subagents`: Dictionary of agentId to per-subagent analysis (same structure as main)
- `combined`: Aggregated metrics across main agent and all subagents

**Subagent Discovery**: The script automatically discovers subagent JSONL files by parsing Task tool_result
entries for `agentId` fields, then resolves paths as `{session_dir}/subagents/agent-{agentId}.jsonl`.
Only existing files are included.

### Step 2: Categorize UX Relevance

Classify tool outputs by user interest level based on the analysis data:

```yaml
ux_relevance_categories:
  HIGH:
    description: "User-requested operations, errors, final results"
    indicators:
      - Tool result contains error or exception
      - Final output of multi-step operation
      - Direct response to user query
      - File modifications user explicitly requested
    examples:
      - Error messages from Bash commands
      - Final test results
      - Completed file writes
      - Build/compile outputs

  MEDIUM:
    description: "Progress indicators, intermediate results"
    indicators:
      - Intermediate step in multi-operation sequence
      - Status checks during long operation
      - Partial results being accumulated
    examples:
      - File existence checks
      - Directory listings for navigation
      - Intermediate grep results
      - Git status during multi-commit operation

  LOW:
    description: "Internal bookkeeping, redundant checks, verbose diagnostics"
    indicators:
      - Repeated identical queries
      - Verbose output from diagnostic tools
      - Internal state verification
      - Redundant safety checks
    examples:
      - Repeated pwd commands
      - Multiple identical file reads
      - Verbose ls output not directly requested
      - Repeated git branch checks
```

Using the skill output, categorize each tool usage pattern by UX relevance. Consider:
- Tools in `cache_candidates` (repeated operations) are often LOW relevance
- Tools with errors are HIGH relevance
- File modifications (Write, Edit) are HIGH relevance
- Navigation commands (pwd, ls, cd) are LOW relevance
- Search operations (Grep, Glob) are MEDIUM relevance

### Step 3: Generate Recommendations

Compile analysis into actionable recommendations based on the skill output:

1. **Batching opportunities**: Use `batch_candidates` to identify consecutive operations that could be combined
2. **Caching opportunities**: Use `cache_candidates` to identify repeated operations
3. **Parallel opportunities**: Use `parallel_candidates` to identify independent operations that could run in parallel
4. **Pipelining opportunities**: Use `pipeline_candidates` to identify dependent operations where phase N+1 can start with partial output from phase N
5. **Script extraction opportunities**: Use `script_extraction_candidates` to identify deterministic multi-step operations embedded in skill markdown that could be extracted into standalone scripts
6. **Token optimization**: Use `token_usage` to identify high-cost operations
7. **Output management**: Use `output_sizes` and UX categorization to suggest hiding/summarizing patterns

Generate a comprehensive analysis report with specific recommendations for:
- Which operations to batch together
- Which results to cache or reference from context
- Which independent operations to parallelize
- Which dependent operations could use pipelining
- Which deterministic workflows could be extracted to scripts
- Which tool outputs to hide or summarize
- Configuration rules for Claude Code UX

### Optimization Pattern Details

#### Pipelining Opportunities

**Definition**: Dependent operations where phase N+1 can start with partial output from phase N, rather than waiting for complete output.

**Detection Criteria**: Sequential phases where phase N+1 only needs partial output from phase N to begin work.

**Examples**:
- Stakeholder review spawn after diff available, before commit squash (review needs diff, not squash order)
- PLAN.md read overlapping with lock acquisition (independent operations masked as sequential)
- Implementation subagent start after first execution step read, before full PLAN.md parse

**Applicability Note**: Claude Code tool calls are sequential within a message. Pipelining applies when skill steps have false serial dependencies - reordering steps to overlap output availability with consumption can reduce total wall-clock time even though tool calls remain sequential.

#### Script Extraction Opportunities

**Principle**: Skills must not contain inline bash for deterministic operations. All deterministic bash
belongs in external script files. Skills contain only: when to use, script invocation, result handling,
and judgment-dependent guidance.

This principle is enforced by `/cat:skill-builder`. When optimize-execution detects skill files with
inline bash, recommend running `/cat:skill-builder` on the skill to extract deterministic operations
into scripts.

**Detection**: Any skill file containing bash code blocks with deterministic operations (no judgment
branching, no user interaction) is a candidate for script extraction.

**Impact**: High — reduces token consumption (Claude doesn't read/reason about implementation), ensures
deterministic execution, and produces fewer tool call round-trips.

See `/cat:skill-builder` for the full script extraction architecture and hybrid workflow pattern.

## Output Format

The skill produces a JSON structure with main agent metrics, subagent analysis, and combined aggregations:

```json
{
  "main": {
    "tool_frequency": [...],
    "token_usage": [...],
    "output_sizes": [...],
    "cache_candidates": [...],
    "batch_candidates": [...],
    "parallel_candidates": [...],
    "pipeline_candidates": [...],
    "script_extraction_candidates": [...],
    "summary": {
      "total_tool_calls": 45,
      "unique_tools": ["Read", "Grep", "Edit"],
      "total_entries": 120
    }
  },
  "subagents": {
    "agent-abc123": {
      "tool_frequency": [...],
      "token_usage": [...],
      "output_sizes": [...],
      "cache_candidates": [...],
      "batch_candidates": [...],
      "parallel_candidates": [...],
      "pipeline_candidates": [...],
      "script_extraction_candidates": [...],
      "summary": {
        "total_tool_calls": 23,
        "unique_tools": ["Read", "Bash"],
        "total_entries": 58
      }
    }
  },
  "combined": {
    "tool_frequency": [...],
    "cache_candidates": [...],
    "token_usage": [...],
    "summary": {
      "total_tool_calls": 68,
      "unique_tools": ["Read", "Grep", "Edit", "Bash"],
      "total_entries": 178,
      "agent_count": 2
    }
  }
}
```

After running the analysis script, categorize tool outputs and generate recommendations:

```json
{
  "executionPatterns": [
    {
      "type": "repeated_operation",
      "tool": "Read",
      "input_signature": "/path/to/file.md",
      "count": 3,
      "recommendation": "Cache file content or reference earlier read"
    },
    {
      "type": "consecutive_batch",
      "tool": "Glob",
      "count": 5,
      "recommendation": "Combine into single glob with multiple patterns"
    },
    {
      "type": "parallelizable",
      "tools": ["Grep", "Glob"],
      "context": "Independent searches in same directory",
      "recommendation": "Execute in parallel for faster results"
    },
    {
      "type": "pipelinable",
      "phases": ["generate_diff", "spawn_reviewer"],
      "context": "Reviewer only needs diff, not subsequent squash operation",
      "recommendation": "Spawn reviewer immediately after diff available"
    },
    {
      "type": "script_extractable",
      "skill": "git-merge-linear",
      "bash_lines": 348,
      "context": "Deterministic git operations with no judgment required",
      "recommendation": "Extract to standalone script with JSON output"
    }
  ],
  "optimizations": [
    {
      "category": "batch",
      "impact": "high",
      "description": "5 consecutive Read operations could be combined",
      "current_cost": "5 tool calls, ~15s",
      "optimized_cost": "1-2 tool calls, ~5s",
      "implementation": "Use batch-read skill"
    },
    {
      "category": "cache",
      "impact": "medium",
      "description": "File read 3 times with identical content",
      "recommendation": "Reference content from earlier in conversation"
    },
    {
      "category": "parallel",
      "impact": "medium",
      "description": "3 independent Grep operations executed sequentially",
      "recommendation": "Use parallel tool calls in single response"
    },
    {
      "category": "pipeline",
      "impact": "medium",
      "description": "Stakeholder review waited for commit squash to complete",
      "current_cost": "Sequential: diff generation + squash + review spawn",
      "optimized_cost": "Pipelined: diff generation + parallel(squash, review spawn)",
      "implementation": "Reorder skill steps to spawn reviewer immediately after diff available"
    },
    {
      "category": "script_extraction",
      "impact": "high",
      "description": "git-merge-linear skill executes 15 sequential bash commands for deterministic merge",
      "current_cost": "15 tool calls, ~348 lines markdown, Claude reads and reasons each step",
      "optimized_cost": "1 script call, ~167 lines script + 97 lines skill markdown, deterministic execution",
      "implementation": "Extract to plugin/hooks/scripts/git-merge-linear.sh with JSON output"
    }
  ],
  "uxRelevance": {
    "HIGH": {
      "count": 12,
      "tools": ["Write", "Edit", "Bash (errors)"],
      "recommendation": "Always display full output"
    },
    "MEDIUM": {
      "count": 25,
      "tools": ["Grep", "Glob", "Read"],
      "recommendation": "Show summary with expansion option"
    },
    "LOW": {
      "count": 18,
      "tools": ["pwd", "ls", "repeated reads"],
      "recommendation": "Hide by default, show on request"
    }
  },
  "configuration": {
    "suggested_rules": [
      {
        "rule": "hide_tool_output",
        "tool": "Bash",
        "condition": "command matches '^pwd$'",
        "reason": "Internal navigation check"
      },
      {
        "rule": "summarize_output",
        "tool": "Grep",
        "condition": "output_lines > 50",
        "format": "First 10 lines + '{remaining} more matches'",
        "reason": "Reduce visual noise for large search results"
      },
      {
        "rule": "collapse_consecutive",
        "tool": "Read",
        "condition": "same_file_within_5_calls",
        "format": "Show only first read, collapse repeats",
        "reason": "Repeated reads indicate cache opportunity"
      }
    ]
  }
}
```

## Example Output

For a session with 45 tool calls building a feature:

```json
{
  "executionPatterns": [
    {
      "type": "repeated_operation",
      "tool": "Read",
      "input_signature": "CLAUDE.md",
      "count": 4,
      "recommendation": "Cache CLAUDE.md content early in session"
    },
    {
      "type": "consecutive_batch",
      "tool": "Grep",
      "count": 6,
      "recommendation": "Combine related searches into single Grep with regex alternation"
    }
  ],
  "optimizations": [
    {
      "category": "batch",
      "impact": "high",
      "description": "6 Grep operations searching same directory",
      "current_cost": "6 tool calls",
      "optimized_cost": "1-2 tool calls",
      "implementation": "Combine patterns: 'pattern1|pattern2|pattern3'"
    },
    {
      "category": "cache",
      "impact": "medium",
      "description": "CLAUDE.md read 4 times, content unchanged",
      "recommendation": "Read once at session start, reference from context"
    },
    {
      "category": "pipeline",
      "impact": "low",
      "description": "Lock acquisition and PLAN.md read executed sequentially",
      "current_cost": "Sequential operations with no true dependency",
      "optimized_cost": "Reorder to overlap independent operations",
      "implementation": "Read PLAN.md first, then acquire lock while analyzing"
    },
    {
      "category": "script_extraction",
      "impact": "medium",
      "description": "5 sequential bash commands performing deterministic validation",
      "current_cost": "5 tool calls, Claude reasons about each step",
      "optimized_cost": "1 script call with structured JSON output",
      "implementation": "Extract validation logic to standalone script"
    }
  ],
  "uxRelevance": {
    "HIGH": {
      "count": 8,
      "examples": ["git commit output", "test results", "build errors"]
    },
    "MEDIUM": {
      "count": 22,
      "examples": ["file searches", "intermediate reads"]
    },
    "LOW": {
      "count": 15,
      "examples": ["pwd checks", "repeated ls", "git status"]
    }
  },
  "configuration": {
    "suggested_rules": [
      {
        "rule": "hide_tool_output",
        "tool": "Bash",
        "condition": "command == 'pwd'",
        "reason": "37% of Bash calls were pwd"
      },
      {
        "rule": "summarize_output",
        "tool": "Glob",
        "condition": "matches > 20",
        "format": "'{count} files found, showing first 10'"
      }
    ]
  }
}
```

## Integration with Other Skills

- **get-history**: Provides raw session data for analysis
- **batch-read**: Implements batch optimization recommendations
- **token-report**: Complements with token-focused metrics
- **learn**: Optimization findings may reveal error patterns

## Limitations

- Analysis is post-hoc; cannot optimize in real-time
- Cache detection is heuristic (same input = same output assumption)
- Parallel opportunity detection may miss complex dependencies
- UX relevance categorization uses general heuristics, may need tuning

## Related Concepts

- Session storage format in get-history skill
- Token budgeting in token-report skill
- Efficiency patterns in batch-read skill
