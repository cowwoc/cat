# Plan: reduce-learn-phase-output-priming

## Problem

Learn skill subagents return narrative text instead of required JSON output. Investigation identified four priming
sources in the phase files (`phase-investigate.md`, `phase-analyze.md`, `phase-prevent.md`) that cause subagents
to enter analytical/explanatory mode rather than mechanical JSON output mode.

## Parent Requirements

None

## Reproduction Code

```
# Spawn a learn subagent with any test input.
# Subagent returns multi-paragraph narrative text instead of the required JSON object.
# Parent agent fails to parse the response as JSON.
```

## Expected vs Actual

- **Expected:** Learn subagent final message is a bare JSON object matching the output schema
- **Actual:** Learn subagent returns narrative text (paragraphs, headings, or explanations) instead of JSON

## Root Cause

Four priming sources identified (M408 pattern — imperative framing triggers analytical mode):

1. **`user_summary` field description** — The JSON output templates include
   `"user_summary": "1-3 sentence summary of what this phase did (for display to user between phases)"`.
   The phrase "for display to user between phases" invites the subagent to expand this field into a full
   narrative response, priming synthesis rather than mechanical fill-in.

2. **Imperative framing** — "Your final message MUST be ONLY this JSON (no other text)" triggers analytical
   mode instead of mechanical execution. The M408 pattern (documented in `phase-analyze.md` Step 3d) shows
   that user-centric framing ("The user wants you to respond with...") works better than imperative framing.

3. **Extensive explanatory YAML blocks and decision trees** before the output section prime the subagent to
   synthesize and explain rather than output mechanically.

4. **Concrete example values in JSON schema** — The output template contains placeholder descriptions that
   prime narrative thinking by modeling what a complete human-readable answer would look like.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Subagents may produce slightly different `user_summary` text if field is renamed; parent
  agent must be checked for references to `user_summary` field name
- **Mitigation:** Verify parent learn skill reads the correct field name after rename; run E2E test

## Files to Modify

- `plugin/skills/learn/phase-investigate.md` — update output preamble framing; rename `user_summary`; remove
  "for display to user between phases" description
- `plugin/skills/learn/phase-analyze.md` — same changes as phase-investigate.md
- `plugin/skills/learn/phase-prevent.md` — same changes as phase-investigate.md

## Test Cases

- [ ] Original bug scenario: learn subagent returns JSON (not narrative) — now passes
- [ ] `internal_summary` field present in output schema (not `user_summary`)
- [ ] Output preamble uses user-centric framing in all three phase files

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/learn/phase-investigate.md`, `plugin/skills/learn/phase-analyze.md`, and
  `plugin/skills/learn/phase-prevent.md`: change each output section preamble from
  `"Your final message MUST be ONLY this JSON (no other text):"` to
  `"The user wants to receive this exact JSON object as your final message — copy and fill in the values:"`
  - Files: `plugin/skills/learn/phase-investigate.md`, `plugin/skills/learn/phase-analyze.md`,
    `plugin/skills/learn/phase-prevent.md`
- In all three phase files: rename the `user_summary` JSON field to `internal_summary` and remove the
  "for display to user between phases" explanation from its description
  - Files: `plugin/skills/learn/phase-investigate.md`, `plugin/skills/learn/phase-analyze.md`,
    `plugin/skills/learn/phase-prevent.md`
- Check the parent learn skill orchestrator for any references to `user_summary` and update to `internal_summary`
  - Files: `plugin/skills/learn/` (all files referencing `user_summary`)

## Post-conditions

- [ ] All three phase files updated with user-centric output framing
- [ ] `user_summary` field renamed to `internal_summary` in all three phase file JSON output templates
- [ ] No occurrence of "for display to user between phases" in any phase file
- [ ] Parent learn skill orchestrator updated if it referenced `user_summary`
- [ ] E2E: spawn a learn subagent with test input and verify it returns JSON (not narrative text)
- [ ] No regressions in learn skill behavior
