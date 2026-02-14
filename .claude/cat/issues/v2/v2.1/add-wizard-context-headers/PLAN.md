# Plan: add-wizard-context-headers

## Current State
When AskUserQuestion prompts appear during wizard flows (approval gates, review decisions, task selection), the
question text and header don't include the issue ID or goal. The user sees generic questions like "Ready to merge?"
without knowing which issue they're approving. This is confusing when returning to a session or when multiple
operations are in progress.

## Target State
All AskUserQuestion calls in issue-aware wizard flows include the issue ID AND a brief goal description in the
question text. Users always know which issue and what goal they are responding to.

Example - before: `"Ready to merge 2.1-fix-bug?"`
Example - after: `"Ready to merge 2.1-fix-bug? (Goal: Fix UTF-8 encoding in all JVM launchers)"`

## Satisfies
None - UX improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - only changes question text/headers, no behavioral changes
- **Mitigation:** Changes are cosmetic; test that wizard flows still function correctly

## Files to Modify
Skill files that contain AskUserQuestion instructions where issue context is available at runtime:
- plugin/skills/work-with-issue/content.md - Approval gate, review decisions (has ISSUE_ID and TASK_GOAL)
- plugin/skills/work/content.md - Potentially complete handling, next task selection
- plugin/skills/work/SKILL.md - Potentially complete handling, next task (has issue_id and goal)
- plugin/skills/learn/content.md - Learning wizard (has mistake context, not issue-specific)
- plugin/skills/remove/content.md - Removal confirmation (has issue/version context)

Note: Only modify files where (1) AskUserQuestion is an active instruction for the agent to execute AND
(2) the issue ID or goal is available as a variable at runtime. Files like config, init, research, cleanup,
and skill-builder are workflow wizards without issue context - use workflow name only if helpful.

## Acceptance Criteria
- [ ] AskUserQuestion calls in /cat:work approval gate include issue ID and goal in question text
- [ ] AskUserQuestion calls in /cat:work potentially-complete handling include issue ID
- [ ] AskUserQuestion calls in /cat:work-with-issue include issue ID and goal
- [ ] Question text format: includes `(Goal: <brief goal>)` where goal is available
- [ ] No behavioral changes to wizard flows
- [ ] Tests passing (mvn -f hooks/pom.xml verify)

## Execution Steps
1. **Step 1:** Audit each file in "Files to Modify" to identify AskUserQuestion instructions where the agent
   has access to ISSUE_ID and TASK_GOAL variables at runtime. List the exact AskUserQuestion calls to modify.
2. **Step 2:** For each identified call, update the question text to include the issue ID and goal.
   Pattern: append `(Goal: ${TASK_GOAL})` or similar to the question string where the goal variable is
   available. For the header field, include the issue ID if space permits (max 12 chars).
3. **Step 3:** Run existing tests to verify no regressions: `mvn -f hooks/pom.xml verify`

## Success Criteria
- [ ] Users can identify which issue AND its goal when answering wizard questions
- [ ] All existing tests pass with no regressions