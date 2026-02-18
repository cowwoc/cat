# Plan: fix-status-instruction-ordering

## Problem
The `/cat:status` skill instruction ordering in `status-first-use/SKILL.md` places the NEXT STEPS table between the instruction text and the `<output skill="status">` tag. This causes the agent to identify NEXT STEPS as the output target and skip echoing the status box content entirely (M354, recurrence of M353).

## Satisfies
None - infrastructure/display fix

## Expected vs Actual
- **Expected:** Agent echoes the full status box content verbatim, then appends NEXT STEPS table
- **Actual:** Agent outputs only the NEXT STEPS table, skipping the status box entirely

## Root Cause
Document content ordering in SKILL.md: instruction → NEXT STEPS table → `<output>` tag. Agent reads instruction, sees NEXT STEPS as the most prominent structured content, treats it as the response, skips the `<output>` tag content.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could reintroduce M353 (meta-instruction leakage) if instruction text is poorly structured
- **Mitigation:** Verify agent does not output meta-instructions after fix

## Files to Modify
- `plugin/skills/status-first-use/SKILL.md` - Restructure so `<output>` tag appears before NEXT STEPS table

## Test Cases
- [ ] Agent echoes full status box content verbatim
- [ ] NEXT STEPS table appears after box content
- [ ] No meta-instructions leak into output (M353 check)

## Acceptance Criteria
- [ ] `<output skill="status">` tag appears before NEXT STEPS table in SKILL.md
- [ ] Agent echoes full status box content verbatim before NEXT STEPS table
- [ ] No M353 regression: agent does not output meta-instructions
- [ ] No new regressions introduced

## Execution Steps
1. **Restructure SKILL.md:** Reorder the file so the instruction references numbered steps, the `<output skill="status">` tag comes next, and the NEXT STEPS table comes last. The instruction should say: echo the contents of the `<output>` tag verbatim, then append the NEXT STEPS table below.
   - Files: `plugin/skills/status-first-use/SKILL.md`

## Success Criteria
- [ ] SKILL.md content order: instruction → `<output>` tag → NEXT STEPS table
- [ ] Haiku model echoes status box verbatim when given the restructured skill