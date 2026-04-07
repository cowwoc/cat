# Plan: enforce-production-sequence-format-in-sprt-tests

## Problem

instruction-builder-agent generates SPRT test cases whose Turn 1 prompts ask evaluative questions
("Is this prohibition well-formed?", "Does this follow the four-component structure?") instead of
presenting production-sequence input. This violates the mandatory rule in first-use.md § Step 3:

> Prompts MUST NOT use Q&A format (posing a direct question to test knowledge recall).

The tests pass Step 6's existing sanity checks and reach SPRT without being caught.

## Why the Mistake Happens

There are two compounding causes:

1. **Rule placement:** The Q&A prohibition is buried mid-paragraph in Step 3 between the per-unit
   scenario type list and the action-based assertions rule. An agent generating tests linearly works
   through the scenario type list, writes a Turn 1 prompt, then encounters the assertion rules — it
   has already committed to the Q&A format before reaching or re-reading the prohibition.

2. **Organic rule conflation:** The prohibition is adjacent to the organic/unprimed requirement ("no
   hints about the correct answer"). Reviewers — human and agent alike — check whether Turn 1 reveals
   test intent (organic rule) and conclude it passes, without separately checking whether the format
   is production-sequence vs. Q&A. A question like "Is this sufficient?" does not hint at the answer,
   so it passes the organic check but fails the format check. The two rules look similar but are
   orthogonal: a prompt can be organic (no answer hint) yet still be Q&A format.

## Root Cause

No validation gate checks for Q&A format. Step 6's sanity checks verify sub-sub-agent assertions and
multi-turn prompts for narration, but do not check Turn 1 format. The prohibition exists only as prose
in Step 3, with no enforcement point.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** The Q&A pattern heuristic (question mark, interrogative opener) may produce false
  positives for legitimate multi-turn scenarios where later turns ask questions.
- **Mitigation:** Apply the check only to Turn 1 (the initial organic prompt), not to subsequent turns.

## Files to Modify
- `plugin/skills/instruction-builder-agent/first-use.md` — two changes:
  1. Add a **Check: No Q&A format in Turn 1** sub-step to Step 6's sanity check loop (alongside the
     existing sub-sub-agent assertion check and narration check). The check scans each test case's
     Turn 1 for question marks and interrogative openers (Is, Does, Should, Can, Would, Are, Has,
     Have, Was, Were, Do, Will, Could) and halts with a descriptive error if found.
  2. Add a **prohibited example** inline in the scenario file format block (§ Step 3) directly below
     the format template, mirroring the existing prohibited/correct pattern used for action-based
     assertions.

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Add inline prohibited/correct example to the scenario file format block in Step 3
  - Files: `plugin/skills/instruction-builder-agent/first-use.md`
- Add Check: No Q&A format in Turn 1 sub-step to the Step 6 sanity check loop
  - Files: `plugin/skills/instruction-builder-agent/first-use.md`

## Post-conditions
- [ ] Step 3 scenario file format block contains an explicit prohibited Q&A example adjacent to the
  format template
- [ ] Step 6 sanity check loop contains a Turn 1 Q&A format check with a HALT message identifying
  the offending test case and its Turn 1 content
- [ ] The check applies only to Turn 1, not to subsequent turns in multi-turn test cases
- [ ] Existing positive test cases (production-sequence format) are not affected by the new check
- [ ] E2E: instruction-builder-agent generates a SPRT test case whose Turn 1 uses production-sequence
  format, not Q&A format, for the compliance rules tested in 2.1-enforce-production-sequence-format-in-sprt-tests
