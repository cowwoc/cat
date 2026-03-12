# Plan: add-parent-requirements-e2e-test

## Problem
No integration test verifies that `/cat:add` produces a PLAN.md with `## Parent Requirements` (not
`## Satisfies`). Template files were updated but the templating pipeline is not end-to-end tested.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None (test only)

## Files to Modify
- `plugin/tests/skills/test-add-skill-template.bats` — new integration test

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Create test verifying that invoking the add skill creates a PLAN.md containing `## Parent Requirements`
  (not `## Satisfies`)
  - Files: `plugin/tests/skills/test-add-skill-template.bats`

## Post-conditions
- [ ] Test exists and passes: generated PLAN.md contains `## Parent Requirements`
- [ ] Test runs in isolated temp directory with no side effects
