# Plan: optimize-add-wizard-flow

## Goal
Reduce round-trips in the /cat:add wizard by combining independent questions into single
AskUserQuestion calls (up to 4 questions per call supported).

## Satisfies
None - infrastructure/performance task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Combining questions loses conditional follow-up capability
- **Mitigation:** Only combine questions that don't need conditional follow-ups

## Files to Modify
- plugin/commands/add.md - combine Scope + Dependencies + Blocks into single question step

## Acceptance Criteria
- [ ] Scope, Dependencies, and Blocks questions combined into single wizard step
- [ ] Conditional flows still work (e.g., "Yes, select dependencies" still shows task list)
- [ ] Wizard completes with fewer user interactions

## Execution Steps
1. **Step 1:** Identify combinable questions in add.md
   - Questions that are independent and don't need conditional follow-ups
   - Verify: List questions that can be combined

2. **Step 2:** Update task_discuss step to use multiQuestion AskUserQuestion
   - Combine Scope + Dependencies + Blocks
   - Verify: Wizard asks 3 questions in 1 step

3. **Step 3:** Preserve conditional logic
   - If user selects "Yes" for dependencies/blocks, show follow-up
   - Verify: Follow-up questions still work correctly
