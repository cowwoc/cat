# Plan: Fix Stakeholder Review Preprocessing

## Problem
The stakeholder-review skill expects `SKILL OUTPUT STAKEHOLDER BOXES` to be injected by preprocessing, but
`stakeholder-review-first-use/SKILL.md` has no preprocessor command to invoke `GetStakeholderOutput.java`. The Java
class exists with box-rendering methods (`getSelectionBox`, `getReviewBox`, `getCriticalConcernBox`, `getHighConcernBox`)
but is not wired into the preprocessing pipeline. This causes the skill to hit its fail-fast every time.

## Satisfies
None (infrastructure fix)

## Root Cause
`stakeholder-review-first-use/SKILL.md` contains only the full skill instructions but no `!` backtick preprocessor
directive to invoke `GetStakeholderOutput`. The skill documentation references `stakeholder_review_handler.py` which
was replaced by the Java class but never wired up.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Stakeholder review boxes could render incorrectly
- **Mitigation:** Test box output matches expected format

## Files to Modify
- `plugin/skills/stakeholder-review-first-use/SKILL.md` - Add preprocessor command to invoke GetStakeholderOutput
- `plugin/skills/stakeholder-review/SKILL.md` - Update error message to reference Java class instead of Python handler
- `client/src/main/java/...` - May need CLI entry point for GetStakeholderOutput (like progress-banner has)

## Acceptance Criteria
- [ ] `stakeholder-review-first-use/SKILL.md` includes preprocessor command that generates STAKEHOLDER BOXES
- [ ] Stakeholder review skill no longer hits fail-fast for missing boxes
- [ ] Error messages reference correct Java class instead of `stakeholder_review_handler.py`

## Execution Steps
1. **Research:** Determine how to invoke `GetStakeholderOutput` from SKILL.md preprocessing (CLI entry point needed?)
2. **Add CLI entry point:** Create a CLI launcher for `GetStakeholderOutput` if one doesn't exist
3. **Update SKILL.md:** Add preprocessor directive to `stakeholder-review-first-use/SKILL.md`
4. **Update error messages:** Replace `stakeholder_review_handler.py` references with correct Java class name
5. **Test:** Verify stakeholder review produces boxes correctly
