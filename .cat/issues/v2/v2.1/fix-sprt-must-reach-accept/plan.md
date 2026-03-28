# Plan: fix-sprt-must-reach-accept

## Problem

Skills that use SPRT for compliance testing stop at "Inconclusive" status without reaching the
formal Accept boundary (log_ratio ≥ A ≈ 2.944). This leaves committed test-results.json files
in an unresolved state and misleads reviewers into thinking testing is incomplete when all runs
passed.

## Parent Requirements

None

## Reproduction

In `cat:instruction-builder-agent`, after 12 consecutive passing SPRT runs (log_ratio ≈ 1.33),
the workflow stopped and committed test-results.json with status "Inconclusive (trending Accept)"
instead of continuing to run until log_ratio ≥ 2.944 (Accept).

## Expected vs Actual

- **Expected:** SPRT waves continue running until the Accept boundary is formally crossed
  (log_ratio ≥ A), regardless of how many waves that requires
- **Actual:** SPRT waves stop after a subjective number of waves when status is "Inconclusive",
  even at 100% pass rate

## Root Cause

No explicit rule in SPRT-using skills requires running until the Accept boundary is crossed.
The stopping condition is left to the agent's discretion, which led to early termination.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — tightening the stopping condition only adds more test runs
- **Mitigation:** Update skill instructions; no code changes required

## Files to Modify

- `plugin/skills/instruction-builder-agent/first-use.md` — add requirement that SPRT must
  continue until Accept boundary is crossed; remove any language permitting Inconclusive as a
  stopping state
- Any other skill files that document or use SPRT stopping conditions

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- Search for all SPRT-related stopping condition language in plugin/skills/:
  - Files: `plugin/skills/`
- For each skill that references SPRT stopping conditions, update the stopping rule to require
  Accept boundary crossing before stopping:
  - Replace any language permitting "Inconclusive" as a final state with a requirement to
    continue running until log_ratio ≥ A (Accept)
  - Files: `plugin/skills/instruction-builder-agent/first-use.md` (and any others found)

## Post-conditions

- [ ] All SPRT-using skills require running until the Accept boundary (log_ratio ≥ A) is crossed
- [ ] No skill documents stopping at Inconclusive as an acceptable final state
- [ ] test-results.json is only committed after Accept status is reached
- [ ] E2E: run the instruction-builder-agent SPRT loop and confirm it reaches Accept before stopping
