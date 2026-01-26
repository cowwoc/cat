---
name: command-optimizer
description: Analyze command execution history to optimize execution and improve output UX
---

# Command Optimizer Skill

**Purpose**: Analyze session tool_use history to identify optimization opportunities and categorize
tool outputs by user relevance. Provides actionable recommendations for batching, caching, and output
summarization.

## When to Use

- After completing a complex task, to identify efficiency improvements
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

### Step 1: Extract Execution History

Use get-history concepts to access session data:

```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"

# Extract all tool_use entries with metadata
jq -s '[.[] | select(.type == "assistant") | .message.content[]? |
  select(.type == "tool_use") | {
    id: .id,
    name: .name,
    input: .input,
    timestamp: (now | todate)
  }]' "$SESSION_FILE"
```

### Step 2: Calculate Execution Metrics

Analyze tool usage patterns:

```bash
# Tool frequency analysis
jq -s '[.[] | select(.type == "assistant") | .message.content[]? |
  select(.type == "tool_use") | .name] |
  group_by(.) | map({tool: .[0], count: length}) |
  sort_by(-.count)' "$SESSION_FILE"

# Output size analysis (from tool_result entries)
jq -s '[.[] | select(.type == "tool_result") | {
    tool_use_id: .tool_use_id,
    output_length: (.content | tostring | length)
  }] | sort_by(-.output_length)' "$SESSION_FILE"

# Token usage per tool type
jq -s '
  [.[] | select(.type == "assistant")] |
  map({
    usage: .message.usage,
    tools: [.message.content[]? | select(.type == "tool_use") | .name]
  }) |
  group_by(.tools[0] // "none") |
  map({
    tool: .[0].tools[0] // "conversation",
    total_input_tokens: (map(.usage.input_tokens // 0) | add),
    total_output_tokens: (map(.usage.output_tokens // 0) | add),
    count: length
  }) | sort_by(-.total_input_tokens)' "$SESSION_FILE"
```

### Step 3: Identify Execution Patterns

Detect optimization opportunities:

```bash
# Repeated identical operations (cache candidates)
jq -s '[.[] | select(.type == "assistant") | .message.content[]? |
  select(.type == "tool_use") | {name: .name, input: .input}] |
  group_by({name: .name, input: .input}) |
  map(select(length > 1) | {
    operation: .[0],
    repeat_count: length,
    optimization: "CACHE_CANDIDATE"
  })' "$SESSION_FILE"

# Consecutive similar operations (batch candidates)
jq -s '[.[] | select(.type == "assistant") | .message.content[]? |
  select(.type == "tool_use")] |
  reduce .[] as $item (
    {prev: null, batches: []};
    if .prev != null and .prev.name == $item.name
    then .batches[-1].items += [$item]
    else .batches += [{tool: $item.name, items: [$item]}]
    end |
    .prev = $item
  ) | .batches | map(select(.items | length > 2) | {
    tool: .tool,
    consecutive_count: (.items | length),
    optimization: "BATCH_CANDIDATE"
  })' "$SESSION_FILE"

# Independent operations (parallel candidates)
# Operations between the same parent message that don't depend on each other
jq -s '[.[] | select(.type == "assistant") |
  {msg_id: .message.id, tools: [.message.content[]? | select(.type == "tool_use")]}] |
  map(select(.tools | length > 1) | {
    message_id: .msg_id,
    parallel_tools: [.tools[].name],
    count: (.tools | length),
    optimization: "PARALLEL_CANDIDATE"
  })' "$SESSION_FILE"
```

### Step 4: Categorize UX Relevance

Classify tool outputs by user interest level:

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

Apply categorization:

```bash
# Categorize by tool type and context
jq -s '
  def categorize_relevance:
    if .name == "Bash" and (.input.command | test("error|fail|Error|FAIL"; "i"))
    then "HIGH"
    elif .name == "Write" or .name == "Edit"
    then "HIGH"
    elif .name == "Read" and (.repeat_count // 1) > 1
    then "LOW"
    elif .name == "Bash" and (.input.command | test("^(pwd|ls|cd)"))
    then "LOW"
    elif .name == "Glob" or .name == "Grep"
    then "MEDIUM"
    else "MEDIUM"
    end;

  [.[] | select(.type == "assistant") | .message.content[]? |
    select(.type == "tool_use")] |
  map(. + {ux_relevance: categorize_relevance}) |
  group_by(.ux_relevance) |
  map({relevance: .[0].ux_relevance, tools: [.[].name], count: length})
' "$SESSION_FILE"
```

### Step 5: Generate Recommendations

Compile analysis into actionable recommendations:

```bash
# Generate comprehensive analysis report
jq -s '
{
  session_summary: {
    total_tool_calls: [.[] | select(.type == "assistant") | .message.content[]? |
      select(.type == "tool_use")] | length,
    unique_tools: [.[] | select(.type == "assistant") | .message.content[]? |
      select(.type == "tool_use") | .name] | unique,
    total_tokens: [.[] | select(.type == "assistant") | .message.usage |
      select(. != null) | (.input_tokens + .output_tokens)] | add
  },
  optimizations: {
    batch_opportunities: "See Step 3 batch analysis",
    cache_opportunities: "See Step 3 cache analysis",
    parallel_opportunities: "See Step 3 parallel analysis"
  },
  ux_configuration: {
    hide_patterns: [
      {tool: "Bash", pattern: "^pwd$", reason: "Internal navigation"},
      {tool: "Bash", pattern: "^ls -la", reason: "Verbose listing"},
      {tool: "Read", condition: "repeat_count > 1", reason: "Redundant reads"}
    ],
    summarize_patterns: [
      {tool: "Grep", condition: "result_lines > 50", action: "Show first 10 + count"},
      {tool: "Glob", condition: "result_count > 20", action: "Show first 10 + count"}
    ],
    always_show: [
      {tool: "Write", reason: "File modifications"},
      {tool: "Edit", reason: "Code changes"},
      {tool: "Bash", pattern: "git commit", reason: "State changes"}
    ]
  }
}' "$SESSION_FILE"
```

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
- **learn-from-mistakes**: Optimization findings may reveal error patterns

## Limitations

- Analysis is post-hoc; cannot optimize in real-time
- Cache detection is heuristic (same input = same output assumption)
- Parallel opportunity detection may miss complex dependencies
- UX relevance categorization uses general heuristics, may need tuning

## Related Concepts

- Session storage format in get-history skill
- Token budgeting in token-report skill
- Efficiency patterns in batch-read skill
