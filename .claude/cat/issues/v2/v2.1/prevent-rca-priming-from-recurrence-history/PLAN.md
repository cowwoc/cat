# Plan: prevent-rca-priming-from-recurrence-history

## Goal

Restructure `/cat:learn` so recurrence history is never in the subagent's context before independent RCA is complete.
Currently SKILL.md Step 1 checks mistakes.json for recurrence and passes that context to the subagent prompt, which
primes the RCA toward the historical narrative before the Step 4b-R independence gate is ever reached.

## Satisfies

- None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The independence gate (Step 4b-R, M416) already exists in phase-analyze.md but is structurally
  defeated because recurrence information arrives before it via the subagent prompt.
- **Mitigation:** The fix is additive — read independently first, then check history — so no existing logic is
  removed, only reordered.

## Files to Modify

- `plugin/skills/learn/SKILL.md` — Step 1: remove "Check mistakes.json for recurrence information (pass to
  phase-analyze)" instruction; remove recurrence context from subagent prompt templates
- `plugin/skills/learn/phase-analyze.md` — restructure Step 4 so the subagent checks mistakes.json for recurrence
  only AFTER completing independent RCA at Step 4b-R step_1

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Read current files**:
   - `plugin/skills/learn/SKILL.md` (Step 1 and Step 2 prompt templates)
   - `plugin/skills/learn/phase-analyze.md` (Step 4, Step 4b-R)

2. **Update `SKILL.md`**:
   - Remove point 3 from Step 1 ("Check mistakes.json for recurrence information (pass to phase-analyze but don't
     use tier classification)")
   - In both prompt templates (quick-tier and deep-tier), ensure the mistake description passed to the subagent
     contains ONLY the raw description of what happened — no recurrence chain, no past mistake IDs

3. **Update `phase-analyze.md`**:
   - Move the recurrence check (Question 4 in Step 4b) to a new step AFTER Step 4b-R step_1 is complete
   - Step 4b-R step_1 must be completed using only direct source file evidence (no mistakes.json lookup)
   - Step 4b-R step_2 (compare against past findings) is where the subagent reads mistakes.json for the first time
   - Update the blocking condition to make explicit that mistakes.json must not be read before step_1 is filled

4. **Commit** with message:
   `refactor: defer recurrence lookup in /cat:learn until after independent RCA`

## Post-conditions

- [ ] `SKILL.md` Step 1 contains no instruction to check mistakes.json before spawning the subagent
- [ ] Subagent prompt templates contain only the raw mistake description (no recurrence chain or past IDs)
- [ ] `phase-analyze.md` Step 4b-R step_1 must be completed before mistakes.json is consulted
- [ ] The independence gate is structurally enforced by ordering, not just by documentation instruction
