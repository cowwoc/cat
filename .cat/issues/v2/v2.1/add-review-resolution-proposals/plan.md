# Plan: add-review-resolution-proposals

## Goal

Update the stakeholder review skill so that whenever it lists stakeholder concerns to the user, it also explains how
the main agent proposes resolving each concern in the same output. These proposals are informational only and do not
change whether concerns are fixed automatically, deferred, skipped, or escalated by the existing workflow.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The new explanation could be mistaken for an automatic fix decision or approval-gate outcome.
- **Mitigation:** Skill text must explicitly state that proposed resolutions are informational and do not alter the
  existing auto-fix, concern decision, or user approval behavior.

## Files to Modify

- `plugin/skills/stakeholder-review/first-use.md` - require proposed resolution text when rendering concern output
- `client/plugin/tests/skills/stakeholder-review/first-use/` - add or update skill behavior scenarios if stakeholder
  review skill tests exist for concern output

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Jobs

- Run `cat:instruction-builder` for `plugin/skills/stakeholder-review/first-use.md` to design the skill instruction
  change.

## Jobs

### Job 1

- Update the stakeholder review reporting instructions so each listed concern includes a concise proposed resolution.
  The proposal should describe the main agent's recommended fix or next step for that concern.
  - Files: `plugin/skills/stakeholder-review/first-use.md`

- Make the skill state that proposed resolutions are informational only. They must not affect whether the concern is
  automatically fixed, deferred, skipped, escalated, or left for user approval under the existing workflow.
  - Files: `plugin/skills/stakeholder-review/first-use.md`

- Ensure the proposed resolution is included in the same user-facing concern output as the concern itself, rather than
  in a separate follow-up message.
  - Files: `plugin/skills/stakeholder-review/first-use.md`

- Add or update stakeholder-review skill tests, if the repository contains tests for stakeholder concern output, so the
  scenario verifies that concern listings include proposed resolutions and that the text remains informational.
  - Files: `client/plugin/tests/skills/stakeholder-review/first-use/`

## Post-conditions

- [ ] Stakeholder review concern output includes a proposed resolution for each listed concern.
- [ ] The skill explicitly states that proposed resolutions are informational and do not change concern handling
  behavior.
- [ ] Existing auto-fix, decision-gate, escalation, and user approval rules remain unchanged.
- [ ] Any existing stakeholder-review skill tests for concern output are updated or new coverage is added for proposed
  resolutions.
- [ ] E2E: When stakeholder review returns multiple concerns, the user-facing output presents each concern together
  with the main agent's proposed resolution for that same concern.
