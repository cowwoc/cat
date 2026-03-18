# Plan: fix-learn-prioritize-remove-priming

## Problem

When the learn skill identifies priming as the root cause of a mistake, the prevention phase
recommends adding enforcement hooks (level 2) rather than first removing the priming source.
This is backwards: the priming source is the cause, and the hook would only address the symptom.
Removing the priming is cheaper, more durable, and prevents the mistake at the source.

The prevention-hierarchy.md currently lacks guidance for the "priming found" case, causing
agents to default to escalation paths (hook > documentation) without considering that the
priming document should be modified or rewritten.

## Parent Requirements

None

## Root Cause

`prevention-hierarchy.md` does not distinguish between "documentation that failed to prevent a
mistake" (escalate to hook) and "documentation that actively caused a mistake via priming"
(remove or rewrite the priming source first). The `phase-prevent.md` also lacks an explicit
check: "if priming was found, can the priming source be removed or rewritten?"

## Expected vs Actual

- **Expected:** When `priming_found: true`, prevention first evaluates removing/rewriting the
  priming source. Hooks or enforcement are only considered if the priming cannot be removed.
- **Actual:** Prevention defaults to hook-based enforcement even when the root cause is a
  documentation source that could simply be corrected.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Changing the prevention guidance may affect how future learnings choose
  prevention type; verify existing prevention examples still produce correct outputs.
- **Mitigation:** Add the priming check as a new first step before the escalation path, so
  existing non-priming cases are unaffected.

## Files to Modify

- `plugin/skills/learn/prevention-hierarchy.md` — add a "Priming Root Cause" section that
  states: when priming is identified as root cause, the first prevention action is to remove
  or correct the priming source. Enforcement hooks are only appropriate if the priming source
  cannot be modified (e.g., it is an external or read-only document).
- `plugin/skills/learn/phase-prevent.md` — add an explicit check at the start of prevention
  planning: if `priming_found: true` from the investigate phase, evaluate whether the priming
  source can be removed or rewritten before considering hooks.

## Test Cases

- [ ] `prevention-hierarchy.md` contains explicit guidance for the "priming found" case
- [ ] `phase-prevent.md` checks `priming_found` before defaulting to hook-based prevention
- [ ] The guidance prioritizes source removal over enforcement for priming root causes
- [ ] Non-priming cases (protocol violations, context degradation) are unaffected

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `plugin/skills/learn/prevention-hierarchy.md`:
  - Add a new "Priming Root Cause" section after the Escalation Rules table
  - Content: when `priming_found: true`, prevention MUST first evaluate whether the priming
    source can be removed or corrected. If yes, the prevention type is `documentation` targeting
    the priming source (not the affected behavior). Hook/enforcement is only appropriate when
    the priming source is external, read-only, or required for legitimate reasons.
  - Files: `plugin/skills/learn/prevention-hierarchy.md`

- Update `plugin/skills/learn/phase-prevent.md`:
  - Add an explicit priming check at the start of prevention planning, before the escalation
    path: "If `investigate.priming_analysis.priming_found` is true, first ask: can the priming
    source be removed or rewritten? If yes, that is the prevention — not a hook."
  - Files: `plugin/skills/learn/phase-prevent.md`

- Update STATE.md: status closed, progress 100%, resolution implemented
  - Files: `.cat/issues/v2/v2.1/fix-learn-prioritize-remove-priming/STATE.md`

## Post-conditions

- [ ] `prevention-hierarchy.md` has a "Priming Root Cause" section with clear guidance
- [ ] `phase-prevent.md` checks `priming_found` before recommending hooks
- [ ] When priming is the root cause, prevention evaluates source removal first
- [ ] All test cases pass
- [ ] E2E: Read both modified files and confirm the priming prioritization guidance is present
      and clearly stated
