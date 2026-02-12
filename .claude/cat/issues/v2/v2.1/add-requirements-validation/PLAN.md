# Plan: add-requirements-validation

## Goal
Update /cat:add to run a requirements stakeholder review of PLAN.md acceptance criteria before issue creation, validating that acceptance criteria fully capture the task requirements and goal. This addresses the known gap documented in M462.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Adding a review step increases issue creation time and token usage. Must not block simple issues with unnecessary overhead.
- **Mitigation:** Requirements review can be lightweight (single subagent, not full stakeholder review). Only validates criteria completeness, not implementation.

## Files to Modify
- plugin/skills/add/content.md - Add requirements validation step after task_ask_type_and_criteria

## Acceptance Criteria
- [ ] /cat:add runs a requirements validation step before creating PLAN.md
- [ ] Validation checks that acceptance criteria cover all aspects of the task description
- [ ] Validation checks that no requirements from the task goal are missing from acceptance criteria
- [ ] Validation spawns a requirements stakeholder subagent to review criteria against goal
- [ ] If validation finds gaps, criteria are updated before PLAN.md creation
- [ ] Existing /cat:add workflow still functions correctly for all issue types
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Add a new step `task_validate_criteria` to plugin/skills/add/content.md
   - Insert after `task_ask_type_and_criteria` step and before `task_analyze_versions`
   - Spawn a requirements stakeholder subagent (cat:stakeholder-requirements) with:
     - Task description (TASK_DESCRIPTION)
     - Task type (TASK_TYPE)
     - Generated acceptance criteria (ACCEPTANCE_CRITERIA)
   - Subagent validates:
     - All aspects of TASK_DESCRIPTION are covered by at least one acceptance criterion
     - No criterion contradicts the task goal (e.g., M462: fail-fast criteria requiring recovery instructions)
     - Criteria are specific and measurable
   - If gaps found: auto-add missing criteria to ACCEPTANCE_CRITERIA
   - If contradictions found: flag to user for resolution
2. **Step 2:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml verify

## Success Criteria
- [ ] Requirements validation step exists in /cat:add workflow
- [ ] Validation catches missing criteria (test with known-gap scenario)
- [ ] All existing tests pass with no regressions