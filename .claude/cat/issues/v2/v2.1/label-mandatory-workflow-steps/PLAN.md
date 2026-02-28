# Plan: label-mandatory-workflow-steps

## Goal
Explicitly label all mandatory workflow steps as MANDATORY in work-with-issue and other orchestration
skills so agents do not treat them as optional or skip them without user permission.

## Satisfies
None - action item A024 from retrospective (PATTERN-020)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Wording changes must be clear without being verbose; checklist must be easy to scan
- **Mitigation:** Use consistent MANDATORY label syntax; keep checklist at top for visibility

## Files to Modify
- `plugin/skills/work-with-issue/first-use.md` - add MANDATORY STEPS checklist at the top; label
  stakeholder-review (Step 5), skill-builder review, and squash-before-approval-gate (Step 6) as
  MANDATORY with explicit note that mandatory steps do not require user permission and must not be
  skipped

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Read work-with-issue/first-use.md:** Examine current step structure and identify the steps that
   must be labeled MANDATORY
   - Files: `plugin/skills/work-with-issue/first-use.md`
2. **Add MANDATORY STEPS checklist:** Insert a clearly visible checklist near the top of first-use.md
   listing all mandatory steps and the rule that mandatory steps do not require user permission
   - Files: `plugin/skills/work-with-issue/first-use.md`
3. **Label individual steps:** For each mandatory step (stakeholder-review, skill-builder review before
   approval gate, squash before approval gate), add a **MANDATORY** marker in the step heading or
   description with the rule that it must not be skipped
   - Files: `plugin/skills/work-with-issue/first-use.md`
4. **Run tests:** Execute `mvn -f client/pom.xml verify` to verify no regressions
   - Files: None (validation step)
5. **Commit:** Commit the changes
   - Files: `plugin/skills/work-with-issue/first-use.md`

## Post-conditions
- [ ] `work-with-issue/first-use.md` contains a MANDATORY STEPS checklist near the top
- [ ] The checklist states that mandatory steps do not require user permission and must not be skipped
- [ ] Stakeholder-review step is labeled MANDATORY
- [ ] Skill-builder review step (before approval gate) is labeled MANDATORY
- [ ] Squash-before-approval-gate step is labeled MANDATORY
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
