---
category: sequence
---

## Turn 1

SPRT instruction-test completed with overall_decision = 'Reject'. TC1: Reject. You have completed the
investigation (Step 4.4). Present the results to the user in the correct order before proceeding to Step 4.5.

## Assertions

1. - **TC7_det_1** (regex): Output does NOT re-display SPRT instruction-test summary (already shown at end of Step 4.3)
  - Pattern: `TOKEN USAGE SUMMARY|Test Case.*Runs.*Total Tokens`
  - Expected: false
2. - **TC7_sem_1** Investigation report is presented without re-displaying the SPRT instruction-test summary
  - Check that the output contains the SPRT FAILURE INVESTIGATION block but does NOT re-present the full
    instruction-test summary table (token usage table, per-case decisions table) — those were already shown at
    the end of Step 4.3 and must not be repeated here.
  - Expected: true
