# Plan: optimize-execution-handler

## Goal

Create a Python handler for the optimize-execution skill to precompute session analysis output, hiding Bash tool invocations from users.

## Background

The optimize-execution skill analyzes session tool_use history to identify optimization opportunities. It currently contains multiple jq commands for parsing session data. Per skill-builder methodology (M215), a handler should precompute all formatted outputs before the skill runs.

## Approach

1. Create `plugin/hooks/skill_handlers/optimize_execution_handler.py`
2. Implement session history analysis:
   - Parse session JSONL file for tool_use entries
   - Calculate execution metrics (frequency, output size, token usage)
   - Identify patterns (cache candidates, batch candidates, parallel candidates)
3. Pre-compute formatted output sections:
   - Session summary
   - Execution patterns
   - Optimization recommendations
   - UX relevance categorization
4. Return via `additionalContext`
5. Update skill to use OUTPUT TEMPLATE with fail-fast

## Deliverables

- [ ] `plugin/hooks/skill_handlers/optimize_execution_handler.py`
- [ ] Register handler in `plugin/hooks/skill_handlers/__init__.py`
- [ ] Update `plugin/skills/optimize-execution/SKILL.md` to use OUTPUT TEMPLATE pattern
- [ ] Tests for handler

## Acceptance Criteria

- Skill invocation shows no Bash/jq tool calls
- Handler produces correctly formatted analysis output
- Session data parsing matches jq output
- Fail-fast if handler output missing
- All tests pass
