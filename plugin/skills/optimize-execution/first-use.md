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

## Analysis Steps

### Step 1: Run Session Analysis

Execute the session analyzer to extract all mechanical data:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/session-analyzer" "${CLAUDE_SESSION_ID}"
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

### Step 3: Extract Subagent Delegation Token Data from JSONL

When the session used subagent delegation (Task tool calls), extract empirical token measurements from the JSONL
transcript before generating recommendations. Do NOT use theoretical estimates — measure from the actual JSONL.

**If no Task tool calls are present in the session, skip directly to Step 5.**

This step has three subsections: Terminology defines key metrics used throughout, Primary Method covers the
session-analyzer tool for routine analysis, and Manual Extraction covers direct JSONL inspection for debugging.
The Measurements section at the end collects per-delegation data for the output in Step 5. Start with Terminology
if you are unfamiliar with delegation metrics; otherwise go straight to Primary Method.

#### Terminology

Before proceeding, key terms used below:

- **`C_main`**: Total context size of a main agent assistant turn =
  `input_tokens + cache_read_input_tokens + cache_creation_input_tokens`
- **`N_sub_turns`**: Number of subagent assistant turns for a given delegation
- **`total_context`**: Same formula as `C_main`, applied to any assistant turn (main or subagent)
- **Raw tokens**: Unweighted cumulative token counts; measures volume but not cost
- **Cost-weighted tokens**: Tokens adjusted by cache pricing multipliers to reflect actual billing cost:
  `(input_tokens * 1.0) + (cache_read * 0.1) + (cache_write_5m * 1.25) + (cache_write_1h * 2.0)`.
  Cache reads at 0.1x pricing make delegation dramatically cheaper than raw token counts suggest.

#### Primary Method: session-analyzer (recommended)

Use the session-analyzer tool for structured, reliable extraction of delegation metrics:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/session-analyzer" analyze "${CLAUDE_SESSION_ID}"
```

This produces structured JSON output with per-delegation metrics already computed, avoiding fragile text parsing.
Use this output to populate the per-delegation analysis in the output format below.

**Error conditions:**
- If the JSONL file is missing or unreadable, report: "Session JSONL not found — skipping delegation analysis"
- If no subagent turns are found, report: "No delegations detected — skipping delegation analysis"
- If output is incomplete (e.g., truncated JSONL), note which delegations could not be fully measured

#### Manual Extraction (advanced / debugging)

If session-analyzer is unavailable or you need to inspect raw data, extract directly from JSONL:

```bash
# Locate the session JSONL file
SESSION_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/sessions/${CLAUDE_SESSION_ID}.jsonl"

# Extract main agent assistant turns (no parentToolUseID), deduplicated by message ID
# Each line has input_tokens, cache_read_input_tokens, cache_creation_input_tokens
# Note: sort -t'"' -k4,4 -u deduplicates by the 4th double-quoted field (message uuid).
# This assumes standard Claude JSONL field order: {"type":"...", "uuid":"..."}
# If field order changes, this deduplication will silently fail — prefer session-analyzer.
grep '"type":"assistant"' "$SESSION_FILE" | grep -v '"parentToolUseID"' | \
  sort -t'"' -k4,4 -u

# Extract subagent assistant turns (has parentToolUseID), deduplicated by message ID
grep '"type":"assistant"' "$SESSION_FILE" | grep '"parentToolUseID"' | \
  sort -t'"' -k4,4 -u

# Per turn: total_context = input_tokens + cache_read_input_tokens + cache_creation_input_tokens
```

#### Measurements to collect

For each Task tool_use (delegation point) identified in the main agent JSONL:

1. **Identify the delegation point and main context:**
   - Find the Task tool_use entry; record the main agent assistant turn immediately preceding it
   - `C_main = input_tokens + cache_read_input_tokens + cache_creation_input_tokens` (from that turn)

2. **Measure main agent cost of delegation:**
   - Count main agent turns involved in delegation (typically 2: the Task call turn + result processing turn)
   - `main_delegation_cost = sum(total_context for each of those turns)`

3. **Measure subagent cost:**
   - Identify subagent turns by `parentToolUseID` matching the Task tool_use ID
   - `subagent_cost = sum(total_context for each subagent assistant turn)`
   - Record: `N_sub_turns`, `C_sub_first` (first turn total context), `C_sub_last` (last turn total context)
   - Record first turn breakdown: `cache_read`, `cache_create`, `new_tokens`

4. **Estimate inline alternative:**
   - If the same work had been done inline on the main agent, each turn would have used C_main as its baseline context
   - `inline_cost = N_sub_turns * C_main` (conservative lower bound; actual inline cost would be higher because main
     context grows with each turn's output)

5. **Compute cost-weighted totals using cache pricing multipliers:**
   - `cost_weighted = (input_tokens * 1.0) + (cache_read * 0.1) + (cache_write_5m * 1.25) + (cache_write_1h * 2.0)`
   - Compute for both the inline estimate and the actual delegation

For each delegation, produce the following analysis output:

```
Phase: [phase name, e.g., "squash"]
Main context at delegation: [C_main] tokens
Subagent turns: [N_sub_turns]
Subagent first turn context: [C_sub_first] (cache_read: X, cache_create: Y, new: Z)
Subagent last turn context: [C_sub_last]

Raw token comparison:
  Inline estimate:    [inline_cost] cumulative tokens
  Actual delegation:  [main_delegation_cost + subagent_cost] cumulative tokens
  Delta:              [difference] tokens ([percentage]%)

Cost-weighted comparison (cache pricing applied):
  Inline estimate:    [inline_cost_weighted] cost-equivalent tokens
  Actual delegation:  [delegation_cost_weighted] cost-equivalent tokens
  Delta:              [difference] cost-equivalent tokens ([percentage]%)

Cache efficiency: [cache_read / total_context]% of subagent tokens were cache hits
```

### Step 4: Pre-Delegation Estimation

Use this section to decide whether to delegate a phase BEFORE implementing the delegation. Unlike the post-hoc analysis
in Step 3, this uses known constants and estimates to predict outcomes at planning time.

**Known constants (empirically derived, stable across sessions):**

| Constant | Value | Source | Variability |
|----------|-------|--------|-------------|
| Subagent baseline context | ~17-20k tokens | First-turn `total_context` across multiple sessions | <15% |
| Subagent cache hit rate (first turn) | ~54% | `cache_read / total_context` on first subagent turn | Depends on cache TTL |
| Main agent spawn overhead | 2 turns | Task tool call + result processing | Fixed |
| Subagent per-turn growth | ~500-2000 tokens | Delta between consecutive subagent turns | Varies with tool output size |

**Estimation formula:**

```
Inputs:
  C_main          = current main agent context (read from last usage in JSONL or /context)
  N_estimated     = estimated turns the work will take
  C_sub_base      = 18,500 (empirical median subagent first-turn context)
  C_sub_growth    = 1,200 (empirical median per-turn context growth in subagent)

Inline cost estimate:
  inline_cost = N_estimated × C_main

Delegation cost estimate:
  main_cost   = 2 × C_main
  sub_cost    = sum(C_sub_base + i × C_sub_growth for i in 0..N_estimated-1)
              = N_estimated × C_sub_base + N_estimated × (N_estimated - 1) / 2 × C_sub_growth
  delegate_cost = main_cost + sub_cost

Decision:
  savings = inline_cost - delegate_cost
  savings_pct = savings / inline_cost × 100
```

**Decision rule (simplified):**

Delegation saves tokens when the main agent's context significantly exceeds the subagent's baseline. The breakeven
point is approximately:

```
C_main > C_sub_base + (2 × C_main / N_estimated)
```

Delegation is almost always beneficial when `C_main > 2 × C_sub_base` (~37k tokens), typically reached after 3-5 main
agent turns. For very short tasks (1-2 turns), spawn overhead dominates and inline is cheaper.

**Quick decision table (using empirical constants):**

| Main Context | Estimated Turns | Delegate? | Estimated Savings |
|-------------|----------------|-----------|-------------------|
| <20k | Any | No | Subagent baseline ≈ main context; no benefit |
| 20-40k | 1-2 | No | Spawn overhead exceeds savings |
| 20-40k | 3+ | Maybe | Marginal; depends on task |
| 40-80k | 2+ | Yes | ~20-40% raw token savings |
| 80k+ | 2+ | Yes | ~50-70% raw token savings |
| 120k+ | Any | Yes | Always; even 1-turn delegation saves context |

**Cost-weighted adjustment:** Raw token savings understate actual cost savings because subagent tokens have a higher
cache-read ratio (0.1x pricing) than main agent tokens at the same context size. Multiply raw savings by ~1.5-2x for
a cost-weighted estimate.

When optimize-execution detects a phase that was NOT delegated but meets the "Delegate?" criteria above, emit a
`delegation_opportunity` entry in the JSON output (see Output Format below).

### Step 5: Generate Recommendations

Compile analysis into actionable recommendations based on the skill output:

1. **Batching opportunities**: Use `batch_candidates` to identify consecutive operations that could be combined
2. **Caching opportunities**: Use `cache_candidates` to identify repeated operations
3. **Parallel opportunities**: Use `parallel_candidates` to identify independent operations that could run in parallel
4. **Pipelining opportunities**: Use `pipeline_candidates` to identify dependent operations where phase N+1 can start
   with partial output from phase N
5. **Script extraction opportunities**: Use `script_extraction_candidates` to identify deterministic multi-step
   operations embedded in skill markdown that could be extracted into standalone scripts
6. **Delegation trade-off analysis**: For each subagent delegation detected, produce the per-delegation analysis from
   Step 3 above, quantifying whether delegation saved or cost tokens vs. inline execution
7. **Missed delegation opportunities**: Apply the Step 4 decision table to phases that ran inline — if C_main and
   estimated turns meet the "Delegate?" threshold, emit a `delegation_opportunity` entry
8. **Token optimization**: Use `token_usage` to identify high-cost operations
9. **Output management**: Use `output_sizes` and UX categorization to suggest hiding/summarizing patterns

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
    },
    {
      "category": "delegation",
      "phase": "squash",
      "subagent_turns": 8,
      "main_context_at_delegation": 52341,
      "subagent_first_turn_context": 21503,
      "subagent_first_turn_breakdown": {
        "cache_read": 12847,
        "cache_create": 8653,
        "new": 3
      },
      "subagent_last_turn_context": 74219,
      "inline_estimate_cumulative": 418728,
      "delegation_actual_cumulative": 367841,
      "delta_tokens": -50887,
      "delta_percent": -12.2,
      "cost_weighted_inline": 187432,
      "cost_weighted_delegation": 94210,
      "cost_weighted_delta": -93222,
      "cache_hit_rate": 0.78
    },
    {
      "category": "delegation_opportunity",
      "phase": "implement",
      "main_context_at_opportunity": 68423,
      "estimated_turns": 5,
      "estimated_savings_tokens": 183645,
      "estimated_savings_percent": 35.8,
      "recommendation": "delegate"
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
    },
    {
      "category": "delegation",
      "phase": "explore",
      "subagent_turns": 15,
      "main_context_at_delegation": 40396,
      "subagent_first_turn_context": 17761,
      "subagent_first_turn_breakdown": {
        "cache_read": 9539,
        "cache_create": 8219,
        "new": 3
      },
      "subagent_last_turn_context": 62294,
      "inline_estimate_cumulative": 605940,
      "delegation_actual_cumulative": 584651,
      "delta_tokens": -21289,
      "delta_percent": -3.5,
      "cost_weighted_inline": 393861,
      "cost_weighted_delegation": 140188,
      "cost_weighted_delta": -253673,
      "cache_hit_rate": 0.84
    },
    {
      "category": "delegation_opportunity",
      "phase": "implement",
      "main_context_at_opportunity": 45200,
      "estimated_turns": 8,
      "estimated_savings_tokens": 214040,
      "estimated_savings_percent": 59.1,
      "recommendation": "delegate"
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

## Worked Example: Subagent Delegation Trade-off Analysis

The following uses empirical token measurements extracted from a real session JSONL transcript.

**Session context:** Main agent delegated codebase exploration to a Haiku subagent for research.

```
Phase: explore (haiku subagent for codebase research)
Main context at delegation: 40,396 tokens
Subagent turns: 15
Subagent first turn: 17,761 (cache_read: 9,539, cache_create: 8,219, new: 3)
Subagent last turn: 62,294

Raw token comparison:
  Inline estimate:    15 * 40,396 = 605,940 cumulative tokens  [conservative lower bound: actual inline cost
                      would be higher as main context grows with each turn's output]
  Actual delegation:  80,792 (main: 2 turns at ~40,396 each) + 503,859 (subagent: 15 turns) = 584,651
  Delta:              -21,289 tokens (-3.5%)
  Note: inline_cost is a conservative lower bound — if delegation is cost-effective against this estimate,
        actual savings vs. true inline execution are likely even better.

Cost-weighted comparison:
  Subagent cache hit rate: 54-99% per turn (high due to shared prompt cache)
  Cost-weighted inline:     605,940 * ~0.65 effective rate = ~393,861 cost-equivalent tokens
  Cost-weighted delegation: main (80,792 at main rates) + subagent (503,859 at subagent rates with high cache hits)
                            = ~52,515 + ~87,673 = ~140,188 cost-equivalent tokens
  Cost-weighted delta:      -253,673 cost-equivalent tokens (-64.4%)
```

**Key insight from empirical data:** The raw token savings from delegation are modest (~3-5%) because
subagent context grows across turns. The **cost-weighted savings are dramatically larger** (60-70%) because
subagents achieve very high cache hit rates — the shared system prompt, tool definitions, and CLAUDE.md
are all cache reads at 0.1x pricing. A 78% cache hit rate on the subagent effectively prices those tokens
at 10 cents on the dollar, making delegation highly cost-efficient even when raw token counts are similar.

**When delegation helps most:**
- Subagent work has many turns (more turns = more cache reuse at 0.1x)
- Main agent context is large (high C_main = high inline cost per turn)
- Subagent work is cache-friendly (repeated reads of shared context)

**When delegation may not help:**
- Few subagent turns (overhead of spawning exceeds savings)
- Subagent work requires many unique new tokens (low cache hit rate)
- Main agent context is small (low C_main = low inline cost baseline)

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
