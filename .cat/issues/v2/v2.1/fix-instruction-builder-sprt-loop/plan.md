# Plan

## Goal

Fix instruction-builder-agent SPRT test loop steps 4.1-4.3 to generate and use .md transcript format
scenario files instead of the old test-cases.json format. Also update empirical-test-agent and
skill-grader-agent if they still reference the old JSON format.

## Pre-conditions

(none)

## Post-conditions

- [ ] Steps 4.1-4.3 in plugin/skills/instruction-builder-agent/first-use.md no longer skip or fail due to
  test-cases.json format mismatch
- [ ] Step 4.1 generates .md scenario files in plugin/tests/<skill>/<scope>/ using the new transcript format
  (Turn sections + Assertions numbered list)
- [ ] Steps 4.2-4.3 read and use .md scenario files for SPRT execution
- [ ] empirical-test-agent and skill-grader-agent updated wherever they reference the old test-cases.json format
- [ ] Regression test added verifying steps 4.1-4.3 execute against .md scenario files
- [ ] No regressions in existing test infrastructure
- [ ] E2E verification: run instruction-builder on a real skill and confirm steps 4.1-4.3 execute without
  skipping
