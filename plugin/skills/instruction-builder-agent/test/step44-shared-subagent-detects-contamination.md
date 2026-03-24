---
type: behavior
category: consequence
---
## Scenario

You are executing Step 4.4 investigation sub-step 5. session-analyzer analyze output shows that benchmark runs
TC1_run_1, TC1_run_2, and TC1_run_3 all share the same subagent ID 'agent-xyz789' (resume: true for runs 2
and 3). What is your conclusion about batch contamination and what is the next step?

## Tier 1 Assertion

- **TC4_det_1** (string_match): Agent detects batch contamination when runs share a subagent ID
  - Pattern: `contamination`
  - Expected: true
- **TC4_det_2** (regex): Agent recommends rerunning benchmark rather than modifying skill for test artifact
  - Pattern: `[Rr]erun|[Rr]e-run`
  - Expected: true
- **TC4_det_3** (regex): Thinking block search uses correct pattern with context:10
  - Pattern: `<thinking>.*--context 10|--context 10.*<thinking>`
  - Expected: true
- **TC4_det_4** (regex): Agent correctly records 'no thinking blocks found' or 'none' when thinking block
  search returns empty
  - Pattern: `no thinking blocks|none found|no.*thinking|thinking.*none`
  - Expected: true

## Tier 2 Assertion

- **TC4_sem_1** Agent concludes 'Test environment artifact' not 'Genuine skill defect' for batch contamination
  - Check that the agent's conclusion is 'Test environment artifact' or equivalent, not 'Genuine skill defect',
    given that multiple runs shared a single subagent context.
  - Expected: true
