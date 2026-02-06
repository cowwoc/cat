# Plan: fix-squash-workflow

## Goal
Fix squash workflow to require rebase-before-squash and clarify STATE.md file grouping. Addresses A018/PATTERN-016
(squash commit corruption). Current squash skill allows git reset --soft without verifying branch is rebased, causing
absorption of base branch changes. Also clarify that STATE.md status changes belong with implementation commits, not as
separate config commits.

## Satisfies
None - infrastructure/retrospective action item

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Changing squash behavior could affect ongoing work
- **Mitigation:** Clear documentation of new requirements, test with sample branches

## Files to Modify
- plugin/skills/work-with-issue/SKILL.md - Update squash step instructions
- plugin/skills/git-squash/SKILL.md - Add rebase-first requirement

## Acceptance Criteria
- [ ] Squash skill requires rebase on base branch HEAD before squashing
- [ ] STATE.md grouped with implementation files, not as separate config commit
- [ ] Instructions are unambiguous for haiku-level model
- [ ] No regressions

## Execution Steps
1. **Step 1:** Read current squash skill and work-with-issue squash instructions
   - Files: plugin/skills/git-squash/SKILL.md, plugin/skills/work-with-issue/SKILL.md
2. **Step 2:** Update git-squash skill to verify rebase before reset --soft
   - Files: plugin/skills/git-squash/SKILL.md
3. **Step 3:** Update work-with-issue squash step to clarify STATE.md grouping
   - Files: plugin/skills/work-with-issue/SKILL.md
4. **Step 4:** Run all tests
   - Command: python3 /workspace/run_tests.py

## Success Criteria
- [ ] Squash instructions include mandatory rebase-first check
- [ ] STATE.md file grouping rules are explicit and unambiguous
- [ ] All tests pass
