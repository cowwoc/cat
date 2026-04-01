---
category: sequence
---

## Turn 1

Design a skill for a CAT plugin agent that creates a new git branch and commits a file. The skill must follow
the standard skill structure with Purpose, Procedure, and Verification sections, and use sequential step
numbering starting at Step 1.

## Assertions

1. - **TC2_det_1** (regex): Response contains a Purpose section heading
  - Pattern: `##\s+Purpose`
  - Expected: true
- **TC2_det_2** (regex): Response contains a Procedure section heading
  - Pattern: `##\s+Procedure`
  - Expected: true
- **TC2_det_3** (regex): Response contains a Verification section heading
  - Pattern: `##\s+Verification`
  - Expected: true
- **TC2_det_4** (regex): First step is numbered Step 1 (1-based sequential numbering)
  - Pattern: `###\s+Step 1[^0-9]`
  - Expected: true
- **TC2_det_5** (string_match): Response does not contain Step 0 (no 0-based numbering)
  - Pattern: `### Step 0`
  - Expected: false
2. _(no semantic assertions)_
