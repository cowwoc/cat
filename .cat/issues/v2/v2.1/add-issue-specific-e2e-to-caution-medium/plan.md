# Plan

## Type

feature

## Goal

Change caution config from high to medium. Update CautionLevel enum semantics: medium should additionally run
issue-specific E2E tests (not just compile and unit tests), while high runs all E2E tests. For issues that touch
skills, E2E tests means running the skill's benchmark/e2e tests. Update all places where caution level descriptions
appear (Java enum Javadoc, config wizard, skill docs) to reflect the new semantics.

## Pre-conditions

(none)

## Post-conditions

- [ ] CautionLevel.MEDIUM updated to enable issue-specific E2E tests (not just compile and unit tests)
- [ ] CautionLevel.HIGH updated to run all E2E tests (full test suite)
- [ ] .cat/config.json caution value changed from "high" to "medium"
- [ ] All caution level descriptions consistent across CautionLevel.java Javadoc, config wizard, and skill documentation
- [ ] CautionLevelTest updated to reflect new semantics
- [ ] All tests pass (mvn verify)
- [ ] No regressions in existing functionality
- [ ] E2E: Confirm caution medium triggers issue-specific E2E tests during /cat:work verify phase
