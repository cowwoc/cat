# Plan: optimize-add-wizard-calls

## Goal
Reduce the number of separate AskUserQuestion calls in /cat:add by combining related questions into single
multi-question calls where possible.

## Satisfies
None - UX improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** May change user experience flow
- **Mitigation:** Ensure same information is gathered, just more efficiently

## Acceptance Criteria
- [ ] Fewer round-trips (measurably fewer AskUserQuestion calls)
- [ ] Same information gathered (no loss of functionality)
- [ ] Behavior unchanged (same outcomes for users)

## Files to Modify
- plugin/skills/add/SKILL.md - Consolidate sequential AskUserQuestion calls

## Current State Analysis
The skill currently has multiple separate AskUserQuestion calls:
1. select_type (Add What?)
2. task_ask_type (Issue Type)
3. task_suggest_version (Version selection)
4. task_suggest_names (Name selection)
5. task_discuss (Scope, Dependencies, Blocks - already combined)
6. task_ask_acceptance_criteria (Acceptance criteria)
7. task_select_requirements (Requirements)

## Target State
Combine questions where they are independent and can be answered together:
- Combine issue type + acceptance criteria (both type-related)
- Combine scope + dependencies + blocks (already done in task_discuss)
- Consider combining version selection with name suggestions where feasible

## Execution Steps
1. **Step 1:** Analyze which questions can be combined
   - Review current question flow
   - Identify independent questions that can be batched
2. **Step 2:** Update SKILL.md to use multi-question AskUserQuestion calls
   - Files: plugin/skills/add/SKILL.md
   - Verify: Skill still gathers same information
3. **Step 3:** Test the updated wizard flow
   - Verify: Fewer round-trips, same outcomes
