# Plan: holistic-review-skill

## Goal
Update the stakeholder-review skill to pass full file context to stakeholders instead of just diffs, enabling holistic project-wide evaluation.

## Satisfies
None - process improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Increased token usage from passing full files; potential performance impact
- **Mitigation:** Consider file size limits; allow stakeholders to request diffs when needed

## Files to Modify
- plugin/skills/stakeholder-review/SKILL.md - Update context preparation and spawning instructions

## Acceptance Criteria
- [ ] Skill passes full file context to stakeholders, not just diffs
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions in existing functionality

## Execution Steps
1. **Update prepare step:** Modify context preparation to read full files instead of just diffs
   - Files: plugin/skills/stakeholder-review/SKILL.md
   - Verify: Read file shows full content being prepared for review
2. **Update spawn instructions:** Modify stakeholder spawn prompt to include holistic review guidance
   - Files: plugin/skills/stakeholder-review/SKILL.md
   - Verify: Spawn prompt includes "evaluate impact on entire project"
3. **Add documentation:** Document the holistic review approach
   - Files: plugin/skills/stakeholder-review/SKILL.md
   - Verify: Purpose section reflects holistic approach
4. **Run tests:** Verify no regressions
   - Verify: python3 /workspace/run_tests.py passes
