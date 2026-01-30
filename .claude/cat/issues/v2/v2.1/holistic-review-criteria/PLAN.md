# Plan: holistic-review-criteria

## Goal
Update all stakeholder review criteria to evaluate changes from a holistic project perspective, ensuring the codebase as a whole improves with each change.

## Satisfies
None - process improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Stakeholder reviews may become more stringent; potential for more rejections
- **Mitigation:** Clear criteria guidance; focus on preventing accumulated technical debt

## Files to Modify
- plugin/stakeholders/architect.md - Add holistic architecture criteria
- plugin/stakeholders/design.md - Add holistic design criteria
- plugin/stakeholders/security.md - Add holistic security criteria
- plugin/stakeholders/testing.md - Add holistic testing criteria
- plugin/stakeholders/performance.md - Add holistic performance criteria
- plugin/stakeholders/requirements.md - Add holistic requirements criteria
- plugin/stakeholders/ux.md - Add holistic UX criteria
- plugin/stakeholders/sales.md - Add holistic sales criteria
- plugin/stakeholders/marketing.md - Add holistic marketing criteria
- plugin/stakeholders/legal.md - Add holistic legal criteria

## Acceptance Criteria
- [ ] All 10 stakeholder files have holistic review instructions
- [ ] Consistent format across all stakeholders
- [ ] Criteria explicitly require evaluating impact on entire project
- [ ] Anti-accumulation checks prevent death by a thousand cuts

## Execution Steps
1. **Define holistic template:** Create standard holistic review section for all stakeholders
   - Verify: Template includes project-wide evaluation guidance
2. **Update technical stakeholders:** Add holistic criteria to architect, design, security, testing, performance
   - Files: plugin/stakeholders/{architect,design,security,testing,performance}.md
   - Verify: Each file has holistic review section
3. **Update business stakeholders:** Add holistic criteria to requirements, ux, sales, marketing, legal
   - Files: plugin/stakeholders/{requirements,ux,sales,marketing,legal}.md
   - Verify: Each file has holistic review section
4. **Verify consistency:** Ensure all stakeholders use consistent holistic format
   - Verify: grep -l "Holistic Review" plugin/stakeholders/*.md returns all 10 files
