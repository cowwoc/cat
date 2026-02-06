# Plan: refactor-compare-docs-thresholds

## Current State
compare-docs skill contains embedded threshold logic (0.95 default, 1.0 for shrink-doc context) and tries to detect
calling context to determine which threshold to apply.

## Target State
compare-docs returns raw scores only. Callers (shrink-doc, etc.) decide what score is acceptable. Clean separation of
concerns.

## Satisfies
None - internal architecture improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - callers already interpret scores
- **Mitigation:** Update callers to include threshold logic they need

## Files to Modify
- plugin/skills/compare-docs/SKILL.md - Remove threshold logic, context detection, PASS/FAIL determination
- plugin/skills/shrink-doc/SKILL.md - Add explicit 1.0 threshold check after receiving score

## Acceptance Criteria
- [ ] Behavior unchanged (same validation outcomes)
- [ ] All tests still pass
- [ ] Code quality improved (clear separation of concerns)

## Execution Steps
1. **Step 1:** Remove threshold tables and context detection from compare-docs
   - Files: plugin/skills/compare-docs/SKILL.md
   - Verify: Skill returns score without PASS/FAIL judgment
2. **Step 2:** Update shrink-doc to check threshold after receiving score
   - Files: plugin/skills/shrink-doc/SKILL.md
   - Verify: shrink-doc still rejects scores < 1.0
3. **Step 3:** Test compression workflow end-to-end
   - Verify: Same validation behavior as before
