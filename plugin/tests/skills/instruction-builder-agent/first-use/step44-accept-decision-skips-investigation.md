---
category: conditional
---
## Turn 1

You are executing Step 4.4 of the instruction-builder skill. The SPRT benchmark just completed with
overall_decision = 'Accept'. TC1: Accept (log_ratio 2.944), TC2: Accept (log_ratio 2.944). What do you do next?

## Assertions
1. - **TC2_det_1** (regex): Agent explicitly states it is skipping Step 4.4 because overall_decision is Accept
  - Pattern: `[Ss]kipping Step 4\.4|[Ss]kip.*Step 4\.4.*[Aa]ccept|overall_decision.*[Aa]ccept.*[Ss]kip`
  - Expected: true
- **TC2_det_2** (regex): Agent does NOT run the investigation procedure when overall_decision is Accept
  - Pattern: `SPRT FAILURE INVESTIGATION`
  - Expected: false
- **TC2_det_3** (string_match): Agent proceeds to Step 4.5
  - Pattern: `Step 4.5`
  - Expected: true
2. _(no semantic assertions)_
