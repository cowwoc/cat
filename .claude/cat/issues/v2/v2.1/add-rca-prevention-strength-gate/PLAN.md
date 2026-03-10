# Plan: add-rca-prevention-strength-gate

## Problem

When a compliance failure recurs and a prior rule already existed, the `/cat:learn` skill does not enforce
minimum prevention barrier levels — allowing weaker prevention (e.g., documentation-only) to be recorded
even when the prior prevention demonstrably failed. Additionally, `/cat:learn` may record prevention as
"applied" in the current session even when no file was actually modified, creating false confidence.

## Parent Requirements

None

## Reproduction Code

```
# Scenario 1: Recurrence with unenforced prior prevention
/cat:learn
# Cause: compliance failure, rule already exists in CLAUDE.md
# Result: agent records documentation-level prevention again (no gate blocks it)
# Expected: gate blocks it, requires escalation to hook level

# Scenario 2: False "prevention applied" without file modification
/cat:learn
# Agent reads a rule, describes a prevention, but does not edit any file
# Result: prevention_implemented: true recorded
# Expected: gate requires at least one file modified before considering prevention applied
```

## Expected vs Actual

- **Expected:** Prevention Strength Gate activates on recurrences, classifies cause type, and requires
  escalation or RCA pipeline review. No prevention is considered applied unless at least one file was
  actually modified in the current session (exception: prior unvalidated prevention already exists).
- **Actual:** No gate enforces minimum prevention level on recurrences. Prevention can be marked applied
  without file modification.

## Root Cause

The `rca-methods.md` file documents three RCA methods (A, B, C) but includes no post-analysis gate that
enforces prevention strength requirements when a failure recurs. The `phase-prevent.md` Step 8 ("Check If
Prevention Already Exists") is close in intent but does not encode the cause-aware decision tree
(unenforced vs. biased RCA vs. too-weak-by-design) or the file-modification requirement.

## Approaches

### A: Soft Prompt Gate in rca-methods.md (Selected)
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Add a `## Prevention Strength Gate` section to `rca-methods.md` that applies after all
  three RCA methods. Implemented as a structured decision tree prompt instruction. Does not require code
  changes or hook changes; operates as a mandatory step agents execute after completing any RCA method.

### B: Add Gate as a New Section in phase-prevent.md
- **Risk:** MEDIUM
- **Scope:** 1 file (minimal, but wrong location)
- **Description:** Add the gate logic to `phase-prevent.md` Step 8. Rejected: `phase-prevent.md` Step 8
  already addresses the escalation principle but without cause-aware classification. Mixing the cause
  classification with prevention level selection creates two competing escalation frameworks in the same
  step, increasing confusion. The RCA methods file is the correct location because the gate is a direct
  output of the RCA analysis step — it should follow immediately after the method completes, not later.

### C: Java-based Enforcement in phase-prevent Handler
- **Risk:** HIGH
- **Scope:** 3+ files (Java handler, tests, hook registration)
- **Description:** Implement a structured data check in the Java phase-prevent handler that validates
  cause type classification before allowing prevention to be recorded. Rejected: the prevention pipeline
  uses LLM judgment for cause classification, which cannot be deterministically validated by a Java parser
  against free-text fields. The gate must be a prompt instruction, not a Java assertion.

**Rationale for Approach A:** The gate is LLM-judgment-requiring (classifying cause type, identifying
prior RCA compromise) and therefore belongs as a skill instruction, not a script. Placing it in
`rca-methods.md` ensures it runs immediately after method completion, before the agent enters
`phase-prevent.md`, closing the gap where agents transition from RCA to prevention without
cause-classification.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Existing RCA workflows (Methods A, B, C) unchanged; only a new post-method section
  is added. No behavior change on first-time occurrences (gate explicitly exempts them).
- **Mitigation:** Gate section includes explicit exemption conditions. Regression test confirms existing
  workflows unaffected.

## Files to Modify

- `plugin/skills/learn/rca-methods.md` — Add `## Prevention Strength Gate` section after the three method
  sections and before `## Recording Format`. Gate covers all cause-aware cases, file-modification
  requirement, and unknown cause type handling.

## Test Cases

- [ ] Gate activates on recurrence with prior documented rule (compliance failure, rule in CLAUDE.md)
- [ ] Gate does NOT activate on first-time occurrence (no prior rule)
- [ ] Case 1: previous prevention unenforced → gate requires hook-level escalation
- [ ] Case 2: previous RCA biased/primed → gate requires fixing analysis pipeline
- [ ] Case 3: previous prevention too weak by design → gate requires level escalation
- [ ] Unknown cause type → gate halts and requires explicit classification
- [ ] No file modified in session → gate blocks marking prevention as applied
- [ ] Prior unvalidated prevention exists → gate exempts from file-modification requirement

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add `## Prevention Strength Gate` section to `plugin/skills/learn/rca-methods.md`
  - Insert the section after `## Recording Format` comment section at line 128 (between the Recording
    Format section and end of file), positioned as a standalone `## Prevention Strength Gate` heading.
  - The section must include:
    1. **Trigger condition:** Gate activates ONLY when `recurrence_of` is non-null (a prior rule existed).
       Explicitly state: first-time occurrences are exempt.
    2. **Cause Classification step:** Agent must classify the cause of the recurrence into one of three
       types before the gate applies. Unknown cause type halts the gate and requires explicit
       classification — no default tier fallback.
    3. **Cause-aware decision tree (all three cases):**
       - Case 1 — "Previous prevention correct but unenforced": prior rule/doc existed and was correct,
         but agent did not follow it. Required action: escalate to hook-level enforcement (level 2 or
         stronger). Documentation-only prevention is blocked.
       - Case 2 — "Previous RCA was biased or primed": prior RCA was compromised (e.g., by a priming
         document that led to wrong root cause analysis). Required action: fix the analysis pipeline
         (correct the priming source or RCA methodology). Fixing the analysis pipeline counts as a
         valid high-strength prevention even if it targets a documentation file.
       - Case 3 — "Previous prevention too weak by design": prior prevention was weaker than needed
         (e.g., documentation when hook was required). Required action: escalate prevention level
         (e.g., documentation → hook, hook → code_fix).
    4. **File-modification requirement:** Prevention is NOT considered applied in the current session
       unless at least one file was actually modified (Edit or Write tool call confirmed). Exception:
       if a prior unvalidated prevention from a previous session already exists and is awaiting cache
       update/install, no new prevention is required until the old one is installed and evaluated.
    5. **Cache-not-updated exception:** If the prior prevention was implemented in a previous session
       but the plugin cache has not yet been updated (e.g., plugin not reinstalled), no new prevention
       is required. The gate should note this exception explicitly.
  - Use positive, actionable language throughout (per `common.md` § Documentation Style)
  - Each line must be ≤ 120 characters
  - The section must NOT include concrete expected values or outcome scores (no priming)
  - After adding the section, verify it is present by reading the file
  - Verify all added lines are ≤ 120 characters

## Post-conditions

- [ ] Gate implemented in `plugin/skills/learn/rca-methods.md` covering all three RCA methods (A, B, C)
      as a required post-analysis step
- [ ] Gate logic correctly handles all three cause-aware cases: (1) previous prevention unenforced →
      escalate to hook level, (2) previous RCA was biased/primed → fix analysis pipeline, (3) previous
      prevention too weak by design → escalate level
- [ ] Unknown cause types require explicit classification before gate applies; gate halts rather than
      defaulting to any tier
- [ ] `/cat:learn` requires at least one file modified in the current session to consider prevention
      applied (exception: prior unvalidated prevention from a previous session exists)
- [ ] Gate does not activate on first-time occurrences — only recurrences trigger escalation requirements
- [ ] Regression test: trigger `/cat:learn` with a compliance failure that has a prior documented rule —
      confirm gate activates and requires escalation or RCA pipeline review
- [ ] No regressions to existing RCA workflows (Methods A, B, C structure unchanged)
- [ ] All lines in modified file are ≤ 120 characters
