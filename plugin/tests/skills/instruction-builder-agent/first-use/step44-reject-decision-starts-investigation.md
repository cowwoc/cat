---
category: conditional
---
## Turn 1

You are executing Step 4.4. The SPRT benchmark just completed with overall_decision = 'Reject'. TC1: Reject
(log_ratio -2.944, pass 1, fail 4), TC2: Accept (log_ratio 2.944). SESSION_ANALYZER is available at
/opt/cat/client/bin/session-analyzer. CLAUDE_SESSION_ID = 'sess-abc123'. What do you do?

## Assertions
1. - **TC3_det_1** (string_match): Agent runs session-analyzer analyze to discover subagent IDs
  - Pattern: `session-analyzer`
  - Expected: true
- **TC3_det_2** (string_match): Agent invokes cat:get-history-agent to retrieve transcripts
  - Pattern: `cat:get-history-agent`
  - Expected: true
- **TC3_det_3** (string_match): Investigation report contains SPRT FAILURE INVESTIGATION header
  - Pattern: `SPRT FAILURE INVESTIGATION`
  - Expected: true
- **TC3_det_4** (string_match): Investigation report identifies the rejected test case TC1
  - Pattern: `TC1`
  - Expected: true
- **TC3_det_5** (regex): Compliance failure search uses correct regex pattern
  - Pattern: `Would you like\|What would you\|follow.up`
  - Expected: true
2. _(no semantic assertions)_
