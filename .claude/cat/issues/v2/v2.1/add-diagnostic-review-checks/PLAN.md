# Plan: add-diagnostic-review-checks

## Goal
Add review checklist items to the testing and requirements stakeholder agents so they catch bugs
where diagnostic/reporting code paths lack the same validation rigor as core logic. This addresses
a gap discovered when work-prepare's diagnostic gathering reported closed dependencies as active
blockers because it skipped the status check that the core discovery script performs.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Checklist additions could produce false positives if too broad
- **Mitigation:** Items are specific and evidence-based from a real bug

## Files to Modify
- `plugin/agents/stakeholder-testing.md` - Add diagnostic path review items
- `plugin/agents/stakeholder-requirements.md` - Add output contract validation items

## Acceptance Criteria
- [ ] Testing stakeholder config updated with new review items
- [ ] Requirements stakeholder config updated with new review items
- [ ] No regressions in existing stakeholder review functionality
- [ ] All tests pass

## Execution Steps

1. **Step 1:** Edit `plugin/agents/stakeholder-testing.md`
   - Add a new High Priority concern: **Diagnostic Path Coverage** - "Diagnostic, error reporting,
     and fallback code paths are tested with the same rigor as happy paths. Code that gathers
     data for reporting must apply the same validation/filtering as core logic."
   - Add a new High Priority concern: **Comment-Code Consistency** - "Comments that describe
     behavior the code actually implements. Flag comments claiming 'check if X' or 'verify Y'
     where the code does not perform that check."
   - Files: `plugin/agents/stakeholder-testing.md`

2. **Step 2:** Edit `plugin/agents/stakeholder-requirements.md`
   - Add a new High Priority concern: **Output Contract Semantic Correctness** - "Data in output
     contracts matches the contract's semantic meaning, not just its structural format. When a
     contract shows fields like 'blocked_by' with status information, that data must be actually
     fetched and validated, not assumed from raw field values."
   - Add a Verification Checklist row: **Semantically Correct** - "Does reported data reflect
     actual computed state, not just raw field extraction?"
   - Files: `plugin/agents/stakeholder-requirements.md`

3. **Step 3:** Run tests to verify no regressions
   - Run `python3 /workspace/run_tests.py`
   - Files: none (validation only)

## Success Criteria
- [ ] Testing stakeholder has diagnostic path coverage and comment-code consistency items
- [ ] Requirements stakeholder has output contract semantic correctness item and checklist row
- [ ] All existing tests pass
