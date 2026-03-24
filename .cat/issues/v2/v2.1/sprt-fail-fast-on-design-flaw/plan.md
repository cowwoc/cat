## Goal

When main agent's independent verification of a deterministic assertion finds a FAIL that results
from an assertion design flaw (the pattern fires on correct behavior, e.g., matching "requirements: APPROVED"
in a negation context like "I will NOT write requirements: APPROVED"), SPRT should halt immediately and
route to Step 4.4 investigation rather than continuing to accumulate invalid signal toward the reject boundary.

## Problem

Currently, the Result Inspection Checklist feeds every main-agent-verified FAIL directly into the SPRT
log_ratio update without distinguishing genuine skill failures from test design artifacts. When an assertion
regex is too broad (expected: false, but pattern matches in negation contexts where the agent is
demonstrating correct behavior), every subsequent run will also fail — wasting tokens on meaningless results.

Observed during `2.1-update-instruction-builder-benchmark-approach`:
- TC1_det_2: pattern `requirements.*APPROVED|architecture.*APPROVED|design.*APPROVED`, expected: false
  Fires when agent writes "I will NOT write requirements: APPROVED" (correct behavior)
- TC8_det_2: pattern `decision.*REJECTED|result.*REJECTED|overall.*REJECTED`, expected: false
  Fires when agent writes "the decision does NOT escalate to REJECTED" (correct behavior)

## Post-conditions

- [ ] `plugin/skills/instruction-builder-agent/first-use.md` Step 4.3 Result Inspection Checklist
  includes a design-flaw detection step: after main agent overrides subagent PASS→FAIL, evaluate
  whether the failure is a design flaw (assertion fires on correct behavior) vs. genuine skill failure
- [ ] When a design flaw is detected, SPRT halts immediately (fail-fast) without updating log_ratio
- [ ] The design flaw and evidence are recorded and passed to Step 4.4 investigation
- [ ] Step 4.4 investigation protocol covers design-flaw classification in addition to genuine failures
- [ ] The detection heuristic is documented: if the agent's response demonstrates the intended
  correct behavior described by the semantic_unit_id but a det assertion still fires, classify as
  design flaw

## Type

refactor

## Sub-Agent Waves

### Wave 1

- Read `plugin/skills/instruction-builder-agent/first-use.md` Step 4.3 Result Inspection Checklist
- Add after "Check 1 — Structural contamination check" and "Check 2 — Prohibition verification":
  **Check 3 — Design-flaw detection:** After main agent independently verifies a det assertion and
  finds FAIL (overriding subagent PASS), evaluate: does the agent's response in the output file
  demonstrate correct skill behavior for this semantic unit, despite the assertion firing?
  If yes (design flaw confirmed): immediately halt SPRT, record flawed assertion ID and evidence,
  route directly to Step 4.4 with design_flaw=true classification. Do NOT update log_ratio.
- Update Step 4.4 to cover design-flaw root cause (fix the assertion, not the skill)
- Commit with message: `refactor: add fail-fast design-flaw detection to SPRT Result Inspection Checklist`
