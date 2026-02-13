# Plan: add-wizard-context-headers

## Current State
Skills that use AskUserQuestion in wizard flows do not consistently indicate which issue or workflow the question
relates to. When multiple operations are in progress or the user returns to a session, it can be unclear which
task a question belongs to.

## Target State
All AskUserQuestion calls in wizard flows include context about which issue or workflow is active, either in the
header field or the question text. Users always know what they are responding to.

## Satisfies
None - UX improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - only changes question text/headers, no behavioral changes
- **Mitigation:** Changes are cosmetic; test that wizard flows still function correctly

## Files to Modify
Skills using AskUserQuestion that operate in issue/task context (24 files across skills):
- plugin/skills/add/content.md - Issue creation wizard questions
- plugin/skills/work-with-issue/content.md - Work phase questions (approval gate, review)
- plugin/skills/work/content.md - Task selection questions
- plugin/skills/work/SKILL.md - Work orchestration questions
- plugin/skills/work/phase-prepare.md - Preparation phase questions
- plugin/skills/work/phase-review.md - Review phase questions
- plugin/skills/work/phase-execute.md - Execution phase questions
- plugin/skills/work/phase-merge.md - Merge phase questions
- plugin/skills/work/deviation-rules.md - Deviation handling questions
- plugin/skills/learn/content.md - Learning wizard questions
- plugin/skills/learn/phase-prevent.md - Prevention phase questions
- plugin/skills/learn/phase-record.md - Record phase questions
- plugin/skills/remove/content.md - Removal confirmation questions
- plugin/skills/research/content.md - Research workflow questions
- plugin/skills/config/content.md - Configuration wizard questions
- plugin/skills/init/content.md - Initialization wizard questions
- plugin/skills/cleanup/content.md - Cleanup confirmation questions
- plugin/skills/skill-builder/content.md - Skill builder questions

Note: Some files (SKILL.md variants) may have AskUserQuestion in documentation context rather than
active wizard flows. Only modify files where AskUserQuestion is used in active user-facing wizards.

## Acceptance Criteria
- [ ] All AskUserQuestion calls in issue-context wizards include the issue ID or workflow name
- [ ] Headers or question text clearly identify which task/workflow the question belongs to
- [ ] Context is added consistently across all affected skills
- [ ] No behavioral changes to wizard flows
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Audit all 24 files to identify which AskUserQuestion calls are in active wizard flows
   vs documentation/examples. Create a list of actual changes needed.
2. **Step 2:** For each file with active wizard AskUserQuestion calls, add context to the header
   or question text. Pattern: include issue ID (e.g., "Approval [2.1-issue-name]") or workflow
   name (e.g., "Config Wizard") in the header field.
3. **Step 3:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml verify

## Success Criteria
- [ ] All wizard AskUserQuestion calls include contextual information
- [ ] Users can identify which workflow they are responding to
- [ ] All existing tests pass with no regressions