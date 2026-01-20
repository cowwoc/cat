# Plan: stakeholder-recommendations

## Goal
When stakeholder review returns concerns, stakeholders should provide specific recommendations for how
to fix each concern. The main agent should then ask the user whether to apply these recommendations.

## Satisfies
- None (infrastructure/workflow improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Changes to stakeholder review workflow may affect existing review behavior
- **Mitigation:** Recommendations are optional - user chooses whether to apply

## Files to Modify
- plugin/skills/stakeholder-review/SKILL.md - Add recommendation output format
- plugin/commands/work.md - Add user choice after stakeholder concerns
- Potentially: stakeholder prompts to request recommendations

## Acceptance Criteria
- [ ] Stakeholders provide specific recommendations for each concern
- [ ] Recommendations include concrete code/config changes when applicable
- [ ] Main agent presents recommendations to user with apply/skip choice
- [ ] User can selectively apply recommendations
- [ ] Applied recommendations are committed before approval gate

## Execution Steps
1. **Step 1:** Update stakeholder review skill to include recommendation output format
   - Files: plugin/skills/stakeholder-review/SKILL.md
   - Verify: Review skill documentation includes recommendation section

2. **Step 2:** Update work.md to handle stakeholder recommendations
   - Files: plugin/commands/work.md
   - Verify: Workflow includes user choice for applying recommendations

3. **Step 3:** Test with a task that generates stakeholder concerns
   - Verify: Recommendations are generated and user can apply them
