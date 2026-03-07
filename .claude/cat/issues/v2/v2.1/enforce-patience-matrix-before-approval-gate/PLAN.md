# Plan: enforce-patience-matrix-before-approval-gate

## Problem

The work-with-issue-agent skill presents the patience matrix concern-handling workflow (Steps 4-5) and
the approval gate (Step 9) as separate sections without explicit enforcement of sequential execution.
This allows agents to skip concern handling or ask users optional questions about concern disposition,
treating it as optional interactive logic instead of mandatory automatic workflow.

Root cause (M498): Documentation teaches the algorithm but lacks explicit MANDATORY enforcement that the
patience matrix MUST run before the approval gate.

## Parent Requirements

None — workflow correctness fix

## Expected vs Actual

- **Expected:** Patience matrix executes automatically before approval gate; agents never ask users how to
  handle concerns
- **Actual:** Agents present optional AskUserQuestion about deferred concerns, recommending 'Skip all'

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Additive change only; no existing behavior removed
- **Mitigation:** Established MANDATORY step pattern (used in Steps 5, 7, 8, 9) is consistent

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` — add MANDATORY REQUIREMENT block before Step 9

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Add MANDATORY REQUIREMENT block before Step 9 (Approval Gate) in work-with-issue-agent/first-use.md:
  - Block states patience matrix concern-handling workflow MUST execute before approval gate
  - Block clarifies concern handling is automatic, not user-directed (no AskUserQuestion for concern decisions)
  - Block instructs agents to STOP and return to Step 5 if about to present approval gate without running
    the patience matrix
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions

- [ ] MANDATORY REQUIREMENT block is added before Step 9 in plugin/skills/work-with-issue-agent/first-use.md
- [ ] Block explicitly states patience matrix MUST execute before approval gate
- [ ] Block clarifies concern handling is automatic, not user-directed
- [ ] Block instructs agents to STOP and return to Step 5 if patience matrix was not yet run
- [ ] E2E: Run /cat:work on an issue with stakeholder concerns; verify agent automatically applies patience
  matrix without asking user how to handle deferred concerns
