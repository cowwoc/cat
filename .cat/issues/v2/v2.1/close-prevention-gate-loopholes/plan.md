# Plan: close-prevention-gate-loopholes

## Problem
The Prevention Strength Gate in `rca-methods.md` contains three loopholes that allow agents to
circumvent escalation requirements for recurring mistakes:

1. `pending_unloaded_cross_session_reuse` (CRITICAL): The "once per mistake" limit for
   `pending_unloaded` is ambiguous across sessions. New sessions can re-classify the same mistake
   as `pending_unloaded` indefinitely, bypassing escalation.

2. `file_modification_waiver_for_pending_unloaded` (CRITICAL): The gate explicitly waives the
   file-modification requirement for `pending_unloaded`, allowing `prevention_implemented: true`
   with an empty `files_modified: []`. No actual prevention artifact is produced.

3. `uncertainty_escalation_auto_default` (HIGH): When uncertainty is declared twice for the same
   mistake, the gate auto-defaults to `unenforced` without verifying which cause type is actually
   most consistent with evidence. Wrong cause leads to wrong prevention.

## Parent Requirements
- None

## Root Cause
Gate rules lack precision on cross-session scope (loophole 1), permit a zero-action deferral
path (loophole 2), and substitute a fixed default for evidence-based reasoning (loophole 3).

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Tightened rules may require more work per recurrence, but all changes are
  additive constraints on an existing gate.
- **Mitigation:** Changes are targeted and minimal; no behavioral path for first-time occurrences
  is affected.

## Files to Modify
- `plugin/skills/learn/rca-methods.md` - Prevention Strength Gate section only

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Edit `plugin/skills/learn/rca-methods.md` § Prevention Strength Gate:
  - Step 1: Replace the uncertainty paragraph with an expanded path that (a) halts on first
    uncertainty, (b) on second uncertainty enumerates the most-consistent cause type from
    evidence rather than defaulting to `unenforced`, and documents the selection basis.
  - Case 4 (`pending_unloaded`): Add explicit rule that the "once per mistake" limit is global
    across all sessions; require checking the learning record for a prior `pending_unloaded`
    classification before allowing it.
  - Step 3 exception: Remove the blanket file-modification waiver for `pending_unloaded`;
    replace with a requirement to update the learning record as the minimum artifact
    (the deferral record IS the required file modification).
  - Update the Gate Summary Table to reflect the new `pending_unloaded` row.
  - Files: `plugin/skills/learn/rca-methods.md`

## Post-conditions
- [ ] `pending_unloaded` cause type states it is global across all sessions and requires checking
  the learning record before selection.
- [ ] Step 3 no longer contains a blanket waiver for `pending_unloaded`; instead requires a
  learning record update as the minimum file modification.
- [ ] Uncertainty escalation path (Step 1) enumerates the most-consistent cause type from evidence
  rather than auto-defaulting to `unenforced`.
- [ ] Gate Summary Table updated to reflect new `pending_unloaded` row behavior.
- [ ] No other gate behavior altered.
