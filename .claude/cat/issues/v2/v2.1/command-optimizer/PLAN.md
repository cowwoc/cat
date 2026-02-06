# Plan: command-optimizer

## Goal

Create a skill that analyzes command execution history to optimize execution and improve output UX by hiding tool
execution details that don't interest users.

## Background

During CAT workflows, many tool calls and command executions happen behind the scenes. Not all of these are relevant to
users - some are implementation details that add noise to the output. This skill will:

1. Analyze command execution patterns from session history
2. Identify opportunities to optimize execution (caching, batching, parallel execution)
3. Recommend which tool outputs should be hidden or summarized for cleaner UX

## Approach

### Phase 1: Execution Analysis
- Use `/cat:get-history` skill to access raw conversation history from Claude Code session storage
- Parse session history to extract command/tool execution patterns
- Calculate execution time, frequency, and output size metrics
- Identify repeated or redundant operations

### Phase 2: Optimization Recommendations
- Suggest commands that could be batched or run in parallel
- Identify cacheable operations
- Flag redundant tool calls

### Phase 3: UX Enhancement
- Categorize tool outputs by user relevance (high/medium/low)
- Recommend which outputs to hide, summarize, or show in full
- Generate configuration for hiding uninteresting tool execution

## Dependencies

- `/cat:get-history` skill - provides access to raw conversation history

## Deliverables

- [ ] `/cat:command-optimizer` skill
- [ ] Execution pattern analyzer (built on get-history output)
- [ ] UX relevance categorizer
- [ ] Optimization recommendation engine
- [ ] Optional: Auto-configuration generator for hiding tool outputs

## Acceptance Criteria

- Skill can analyze a session's command history
- Produces actionable optimization recommendations
- Categorizes tool outputs by user relevance
- Generates configuration suggestions for improved UX
