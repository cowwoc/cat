# Plan: research-new-issues

## Goal
Enhance the /cat:add workflow to gather better requirements through clarifying questions,
support for unknowns, automatic research, and reordered version selection.

## Satisfies
None - workflow improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Workflow changes may confuse users familiar with current order
- **Mitigation:** Clear prompts explaining the new flow

## Files to Modify
- plugin/skills/add.md - Main /cat:add skill definition

## Execution Steps
1. **Step 1:** Add clarifying questions for vague requirements
   - Files: plugin/skills/add.md
   - Verify: Wizard asks follow-up questions when description is vague

2. **Step 2:** Add "Unsure/Unknown" option to relevant questions
   - Files: plugin/skills/add.md
   - Verify: User can select "Unsure" for scope, dependencies, etc.

3. **Step 3:** Collect unknowns and trigger /cat:research
   - Files: plugin/skills/add.md
   - Verify: Research runs automatically when unknowns accumulated

4. **Step 4:** Reorder workflow - version selection after scope understood
   - Files: plugin/skills/add.md
   - Verify: Version question appears after requirements are clarified

5. **Step 5:** Generate more comprehensive PLAN.md from gathered info
   - Files: plugin/skills/add.md
   - Verify: Created PLAN.md includes research findings

## Acceptance Criteria
- [ ] /cat:add asks clarifying questions for vague requirements
- [ ] "Unsure/Unknown" option available during wizard
- [ ] /cat:research auto-triggers when unknowns identified
- [ ] Version selection happens after scope is understood
