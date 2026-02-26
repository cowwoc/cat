# Plan: fix-learn-skill-priming-and-silent-failures

## Problem

The `learn` skill has multiple priming and silent failure issues identified via skill-builder analysis
that cause agents to systematically skip investigation, use fabricated data, and bypass blocking gates.

## Root Cause

Ten distinct findings across five phase files, falling into three categories:
1. **Tier classification priming** — protocol_violation auto-routes to quick-tier, bypassing investigation
   even when investigation is required (e.g., M415 required JSONL to discover false `prevention_implemented: true`)
2. **`jq` silent failures** — `jq` is unavailable per `.claude/rules/common.md`, causing context metric
   scripts in phase-analyze.md Step 3, phase-prevent.md Step 5, and phase-analyze.md Step 4d to silently
   fail; agents fabricate values from the concrete example templates
3. **Structural gate bypass** — Step 4b-R independence gate is positioned after the agent has already
   seen recurrence chain from the mistake description; Step 4d immediately provides jq tooling that
   teaches the exact bypass behavior the gate was designed to block

## Risk Assessment

- **Risk Level:** HIGH
- **Regression Risk:** Changes to phase files affect all future learn sessions and subagents
- **Mitigation:** Changes are framing/ordering only; no behavioral logic removed

## Files to Modify

- `plugin/skills/learn/first-use.md` — tier classification, token savings note, PRE_EXTRACTED_CONTEXT
  positioning, user-reported category instruction
- `plugin/skills/learn/phase-analyze.md` — jq scripts (Steps 3 and 4d), Step 4b-R gate position,
  Step 4d recurrence tooling, concrete output examples in Steps 2 and 4d
- `plugin/skills/learn/phase-investigate.md` — early termination rule (pre-extracted context counts
  toward limit), pre-extracted context section positioning
- `plugin/skills/learn/phase-prevent.md` — concrete pre-filled example values in Step 5

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Fix tier classification in `first-use.md`:**
   - Replace unconditional `protocol_violation → quick` rule with evidence-based criteria: quick tier
     requires the agent to be able to describe the event sequence without JSONL investigation
   - Remove the token savings note (lines 256-258) that creates cost pressure toward quick-tier
   - Change "Ask user for category" to "Derive observable failure from description; category is an
     RCA output, not an input"
   - Files: `plugin/skills/learn/first-use.md`

2. **Fix PRE_EXTRACTED_CONTEXT positioning in `first-use.md`:**
   - Move `PRE_EXTRACTED_CONTEXT` injection to appear after the phase-investigate.md instructions,
     not before
   - Add explicit framing: "This context is a starting-point index only. JSONL is the authoritative source."
   - Files: `plugin/skills/learn/first-use.md`

3. **Fix jq scripts in `phase-analyze.md`:**
   - Replace the `jq`-based bash script in Step 3 (context metrics) with a `session-analyzer` call
     (available via jlink) or remove and instruct to use Phase 1 investigation output
   - Replace the `jq` recurrence chain query in Step 4d with a note that jq is unavailable and
     to use past mistake entries from Phase 1 investigation
   - Files: `plugin/skills/learn/phase-analyze.md`

4. **Fix Step 4b-R gate position and Step 4d in `phase-analyze.md`:**
   - Move the Step 4b-R recurrence independence gate to appear as a blocking conditional branch
     INSIDE Step 4b directly after Question 4 (recurring_pattern), before Step 4d is visible
   - Add a hard blocking header to Step 4d: "BLOCKED until Step 4b-R is complete"
   - Replace concrete example values in Step 2 document block with `{placeholder}` syntax
   - Add a Quick-Tier Notice at the top noting that quick-tier has no Phase 1 input
   - Files: `plugin/skills/learn/phase-analyze.md`

5. **Fix early termination rule in `phase-investigate.md`:**
   - Change early termination rule to count only direct JSONL confirmations, not pre-extracted context
   - Add explicit note that pre-extracted context is a starting-point index, not evidence
   - Files: `plugin/skills/learn/phase-investigate.md`

6. **Fix concrete example values in `phase-prevent.md`:**
   - Replace all pre-filled concrete values in Step 5 `context_degradation_analysis` block with
     `{placeholder}` syntax referencing Phase 2 context metrics output
   - Files: `plugin/skills/learn/phase-prevent.md`

7. **Run tests to verify no regressions:**
   - `mvn -f client/pom.xml test`

## Post-conditions

- [ ] `first-use.md`: No unconditional `protocol_violation → quick` mapping exists; tier decision
  requires evidence-based justification
- [ ] `first-use.md`: No token savings language that creates cost pressure toward quick-tier
- [ ] `phase-analyze.md`: No `jq` commands remain; all scripts use available tools or Phase 1 output
- [ ] `phase-analyze.md`: Step 4b-R gate appears before Step 4d content in the document
- [ ] `phase-analyze.md`: Step 4d opens with a blocking gate header referencing Step 4b-R
- [ ] `phase-investigate.md`: Early termination rule explicitly excludes pre-extracted context from match count
- [ ] `phase-prevent.md`: No pre-filled concrete values in Step 5 example block
- [ ] All tests pass: `mvn -f client/pom.xml test` exits 0
