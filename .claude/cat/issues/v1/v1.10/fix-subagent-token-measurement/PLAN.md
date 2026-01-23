# Plan: fix-subagent-token-measurement

## Problem

The current token-report skill measures tokens from individual API messages (`input_tokens` +
`output_tokens` from assistant entries), which only captures response tokens (~12k total for a
session). The correct metric for subagent execution is `totalTokens` from the `toolUseResult`
object in Task tool completions, which measures full context processed (~212k total) and matches
what Claude Code CLI displays.

## Satisfies

None - infrastructure/measurement fix

## Reproduction Code

```bash
# Current (wrong) - sums individual message tokens
jq -s '[.[] | select(.type == "assistant") | .message.usage.input_tokens] | add' session.jsonl
# Returns ~12k (only output tokens)

# Correct - extracts totalTokens from Task completions
grep '"toolUseResult"' session.jsonl | grep -o '"totalTokens":[0-9]*'
# Returns actual context usage matching CLI display (e.g., 14.0k per subagent)
```

## Expected vs Actual

- **Expected:** Token report shows ~66k-85k tokens per subagent (matching CLI "Done" messages)
- **Actual:** Token report shows ~12k total across all subagents (only counting output tokens)

## Root Cause

The token-report skill was designed to measure main session tokens (input + output per turn), but
subagent token usage is reported differently in the session file. Task tool completions include a
`toolUseResult` object with `totalTokens` that represents the full context the subagent processed.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None - this fixes incorrect measurements, doesn't break working functionality
- **Mitigation:** Verify output matches CLI-displayed token values

## Files to Modify

- `skills/token-report/SKILL.md` - Update extraction method to use totalTokens from toolUseResult
- `skills/collect-results/SKILL.md` - Update to reference token-report skill for subagent metrics
- `skills/monitor-subagents/SKILL.md` - Update to use token-report for token-based decisions
- `.claude/cat/workflows/token-warning.md` - Update to use token-report skill

## Test Cases

- [ ] Token report shows correct totalTokens for each subagent (matching CLI display)
- [ ] Task descriptions are correlated with their token usage via tool_use_id
- [ ] Output formatted as table with X.Xk format for thousands
- [ ] Total row sums all subagent usage correctly
- [ ] Error handling: "No session ID" when not in active session
- [ ] Error handling: session file path shown when file missing
- [ ] "No subagent executions found" when no Task tools in session

## Execution Steps

1. **Update token-report skill extraction method**
   - Replace jq-based message token summing with toolUseResult extraction
   - Add correlation logic to match tool_use_id between Task invocations and completions
   - Format output as markdown table with description, tokens (X.Xk), duration
   - Verify: Run skill in session with subagents, compare to CLI "Done" messages

2. **Update collect-results to use token-report**
   - Replace inline token parsing with skill invocation
   - Verify: Collect results shows correct token metrics

3. **Update monitor-subagents to reference token-report**
   - Ensure monitoring uses same metric source
   - Verify: Monitoring warnings trigger at correct thresholds

4. **Update token-warning workflow**
   - Reference token-report skill for metrics
   - Verify: Warnings based on accurate totalTokens values
