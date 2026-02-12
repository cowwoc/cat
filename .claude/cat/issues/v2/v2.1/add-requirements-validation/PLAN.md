# Plan: add-requirements-validation

## Goal
Update /cat:add to run a requirements stakeholder review of acceptance criteria before PLAN.md creation, validating
that acceptance criteria fully capture the task requirements, goal, and parent version requirements. This addresses the
known gap documented in M462 where wrong acceptance criteria primed wrong implementations.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Adding a review step increases issue creation time and token usage. Must not block simple issues with
  unnecessary overhead.
- **Mitigation:** Requirements review can be lightweight (single subagent, not full stakeholder review). Only validates
  criteria completeness, not implementation.

## Files to Modify
- plugin/skills/add/content.md - Add requirements validation step before task_create (after task_select_requirements)

## Acceptance Criteria
- [ ] /cat:add runs a requirements validation step before task_create (right before PLAN.md generation)
- [ ] Validation checks that acceptance criteria cover ALL aspects of the task description
- [ ] Validation checks that no requirements from the task goal are missing from acceptance criteria
- [ ] Validation cross-checks against parent version REQ-xxx requirements (from task_select_requirements)
- [ ] If issue satisfies REQ-xxx, acceptance criteria must address that requirement's intent
- [ ] Validation spawns a requirements stakeholder subagent to review criteria against goal
- [ ] If validation finds gaps, missing criteria are auto-added to ACCEPTANCE_CRITERIA
- [ ] If contradictions found (e.g., M462 pattern), flag to user for resolution
- [ ] Existing /cat:add workflow still functions correctly for all issue types
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Add a new step `task_validate_criteria` to plugin/skills/add/content.md
   - Insert after `task_select_requirements` step and before `task_create`
   - This placement ensures all inputs are available: TASK_DESCRIPTION, TASK_TYPE, ACCEPTANCE_CRITERIA,
     and selected parent version requirements (REQ-xxx)
   - Spawn a requirements stakeholder subagent (cat:stakeholder-requirements) with:
     - Task description (TASK_DESCRIPTION)
     - Task type (TASK_TYPE)
     - Generated acceptance criteria (ACCEPTANCE_CRITERIA)
     - Selected parent version requirements (from task_select_requirements step)
   - Subagent validates three aspects:
     - **Completeness:** Every aspect of TASK_DESCRIPTION is covered by at least one acceptance criterion.
       Break the description into discrete requirements and verify each has a corresponding criterion.
     - **Version requirements cross-check:** If the issue satisfies any REQ-xxx from the parent version,
       verify that the acceptance criteria address the intent of those requirements. Flag any satisfied
       requirement that has no corresponding criterion.
     - **Contradiction check:** No criterion contradicts the task goal or established principles
       (e.g., M462: fail-fast criteria requiring recovery instructions)
   - If gaps found: auto-add missing criteria to ACCEPTANCE_CRITERIA and report additions to user
   - If contradictions found: flag to user with explanation for resolution
   - If all criteria pass: proceed silently (no user interaction needed)
2. **Step 2:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml verify

## Success Criteria
- [ ] Requirements validation step exists in /cat:add workflow, positioned before task_create
- [ ] Validation catches missing criteria (test with known-gap scenario)
- [ ] Validation cross-checks against parent version REQ-xxx requirements
- [ ] All existing tests pass with no regressions