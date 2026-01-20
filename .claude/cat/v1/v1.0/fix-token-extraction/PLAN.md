# Plan: fix-token-extraction

## Problem

The /cat:work workflow (M146) tells the main agent to manually parse the Task tool output summary
line to extract token usage. In practice, agents ignore this and report pre-execution estimates
instead. The workflow should invoke existing skills (`/cat:collect-results`, `/cat:token-report`)
rather than requiring manual parsing.

## Satisfies

None - infrastructure/workflow fix

## Reproduction Code

```
# Current behavior after Task tool returns:
# Agent sees: Done (14 tool uses · 27.8k tokens · 1m 18s)
# Agent reports: ~28K tokens (the pre-execution ESTIMATE)
# Agent skips: Formal "Subagent Execution Report" format

# Expected behavior:
# Agent invokes /cat:token-report or /cat:collect-results
# Agent reports: 27.8K tokens (ACTUAL from skill output)
# Agent presents: Formal "Subagent Execution Report" format
```

## Expected vs Actual

- **Expected:** Workflow invokes existing skills to get accurate token metrics
- **Actual:** Workflow tells agents to manually parse Task output line (not followed in practice)

## Root Cause

M146 guidance in work.md tells agents to "extract from the summary line" which requires manual
parsing. The existing `/cat:token-report` and `/cat:collect-results` skills already extract this
data properly from the session file. The workflow should reference these skills instead.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None - this improves accuracy of reported metrics
- **Mitigation:** Verify skill invocation produces correct metrics matching CLI display

## Files to Modify

- `plugin/.claude/cat/workflows/work.md` - Update M146 section to invoke skills instead of manual parsing
- `plugin/commands/work.md` - Sync if this is the authoritative source

## Test Cases

- [ ] After Task tool returns, `/cat:token-report` or `/cat:collect-results` is invoked
- [ ] Reported token value matches CLI "Done (X tool uses · YK tokens)" display
- [ ] Formal "Subagent Execution Report" format is presented
- [ ] Pre-execution estimates are NOT used when actual metrics are available

## Execution Steps

1. **Update work.md M146 section**
   - Replace "extract from summary line" with "invoke `/cat:token-report`"
   - Add explicit instruction to use skill-derived metrics, not estimates
   - Include formal report format requirement
   - Verify: Read updated workflow, confirm skill invocation is specified

2. **Sync changes to command file if needed**
   - Check if plugin/commands/work.md needs same update
   - Verify: Both files consistent
