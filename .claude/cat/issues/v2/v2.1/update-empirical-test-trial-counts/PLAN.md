# Plan: update-empirical-test-trial-counts

## Current State
The empirical-test skill hardcodes `--trials 5` for baseline and isolation steps, and `--trials 15` for
final validation. This wastes tokens when a single trial already shows clear pass/fail.

## Target State
Progressive trial counts: start with 1 trial for quick signal, expand to 5 on success, then 10 for
final validation. This reduces cost for obvious failures while maintaining statistical confidence for
ambiguous results.

## Satisfies
- None (workflow optimization)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None ��� only changes default trial counts in skill documentation
- **Mitigation:** Empirical testing of the skill itself after changes

## Files to Modify
- `plugin/skills/empirical-test/first-use.md` ��� Update `--trials` values in Steps 3, 5, and 7

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Read `plugin/skills/empirical-test/first-use.md`** to identify all `--trials` occurrences
   - Files: `plugin/skills/empirical-test/first-use.md`
2. **Update Step 3 (Baseline)** to use `--trials 1` instead of `--trials 5`
   - Files: `plugin/skills/empirical-test/first-use.md`
3. **Update Step 5 (Isolation)** to use `--trials 5` (keep as-is, this is the expansion step)
   - Files: `plugin/skills/empirical-test/first-use.md`
4. **Update Step 7 (Final validation)** to use `--trials 10` instead of `--trials 15`
   - Files: `plugin/skills/empirical-test/first-use.md`
5. **Update interpretation tables** to reflect new trial counts
   - Files: `plugin/skills/empirical-test/first-use.md`
6. **Update Tips section** to reflect new default progression (1 ��� 5 ��� 10)
   - Files: `plugin/skills/empirical-test/first-use.md`

## Post-conditions
- [ ] All `--trials` values in the skill reflect the 1 ��� 5 ��� 10 progression
- [ ] Interpretation tables are updated for the new trial counts
- [ ] Tips section documents the new progression
- [ ] No regressions in related functionality
- [ ] E2E: The skill document reads coherently with the new trial progression
