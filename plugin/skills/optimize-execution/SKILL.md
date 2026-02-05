---
description: Use after complex issues to analyze session efficiency and generate optimization recommendations
user-invocable: true
---

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

## Execution Method

The skill uses a Python script (`plugin/scripts/analyze-session.py`) to extract mechanical data from
the session file. This avoids shell escaping issues with inline jq commands (M431).

## Analysis Steps

### Step 1: Run Session Analysis Script

Execute the Python script to extract all mechanical metrics:

```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"
python3 plugin/scripts/analyze-session.py "$SESSION_FILE"
```

The script outputs a JSON object containing:
- `tool_frequency`: Count of each tool type used
- `token_usage`: Token consumption per tool type
- `output_sizes`: Tool result output lengths
- `cache_candidates`: Repeated identical operations
- `batch_candidates`: Consecutive same-tool operations
- `parallel_candidates`: Multiple tools in single message
- `summary`: Overall session statistics

### Step 2: Categorize UX Relevance

Classify tool outputs by user interest level using the metrics from Step 1:

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

Apply categorization logic:
- Bash commands with error indicators: HIGH
- Write/Edit operations: HIGH
- Repeated Read operations (from cache_candidates): LOW
- Navigation commands (pwd, ls, cd): LOW
- Search operations (Glob, Grep): MEDIUM
- All others: MEDIUM

### Step 3: Generate Recommendations

Compile the analysis into actionable recommendations based on:

1. **Batch Opportunities**: Use `batch_candidates` from script output
   - Consecutive same-tool operations suggest batching
   - Recommendation: Combine into single operation with multiple inputs

2. **Cache Opportunities**: Use `cache_candidates` from script output
   - Repeated identical operations suggest caching
   - Recommendation: Reference earlier results instead of re-executing

3. **Parallel Opportunities**: Use `parallel_candidates` from script output
   - Multiple tools in single message already parallelized
   - Recommendation: Continue this pattern for independent operations

4. **UX Configuration Rules**: Based on Step 2 categorization
   - HIGH relevance: Always display full output
   - MEDIUM relevance: Show summary with expansion option
   - LOW relevance: Hide by default, show on request

## Output Format

The skill produces a JSON structure with four main sections:

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
