# Plan

## Goal

Fix SPRT test-run agents seeing assertion outputs — extract only Turn 1 prompt section before spawning
test-run agents, delete all existing test-results.json files.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: test-run agents no longer see assertion content when executing test scenarios
- [ ] Regression test added: test verifies that test-run agent prompts contain only Turn 1 content
- [ ] All existing test-results.json files deleted to force re-validation with fixed approach
- [ ] No new issues introduced
- [ ] E2E verification: run a sample SPRT test and verify the test-run agent receives only the Turn 1 prompt
