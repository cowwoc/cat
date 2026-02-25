# Plan: refactor-add-wizard-batching

## Current State
The `/cat:add` issue workflow presents questions across multiple separate AskUserQuestion wizard calls, requiring more user interactions than necessary. Some questions that don't depend on each other's answers are asked in separate rounds.

## Target State
Batch independent questions into fewer AskUserQuestion calls (up to 4 questions per call) to reduce user interaction friction while preserving correctness for questions that depend on prior answers.

## Satisfies
None (internal UX improvement)

## Analysis of Batchable Steps

The issue workflow has these AskUserQuestion calls:

| Step | Questions | Depends On |
|---|---|---|
| issue_ask_type_and_criteria | Issue type + custom post-conditions | ISSUE_DESCRIPTION only |
| issue_suggest_version | Version selection | ISSUE_DESCRIPTION only |
| issue_suggest_names | Name selection | ISSUE_TYPE (from issue_ask_type_and_criteria) |
| issue_discuss | Dependencies + Blocks | Version selection |
| issue_select_requirements | Requirements satisfaction | Version selection |

### Batch 1: issue_ask_type_and_criteria + issue_suggest_names
Issue type + custom post-conditions + name selection can be combined when the conversation context already makes the type clear (e.g., user said "refactor X"). Name prefix conventions (`refactor-`, `add-`, `fix-`) are predictable from context. When type is ambiguous, fall back to sequential.

### Batch 2: issue_discuss + issue_select_requirements
Dependency/blocks questions and requirements satisfaction both depend only on the selected version, not on each other. These can be combined into a single call.

Note: issue_suggest_version cannot be batched with issue_suggest_names because name generation depends on ISSUE_TYPE which comes from the same call as version suggestion's prerequisite.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None ��� only changes wizard interaction flow
- **Mitigation:** The skill is agent instructions, not compiled code. Changes are immediately testable.

## Files to Modify
- `plugin/skills/add/first-use.md` ��� Restructure wizard steps to batch independent questions

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

### Step 1: Modify issue_ask_type_and_criteria step
Update the step to also include name suggestions when the conversation context makes the type predictable. Add conditional logic: if the description clearly indicates a type (e.g., contains "refactor", "fix", "add"), batch type confirmation + post-conditions + name selection into one call.

### Step 2: Merge issue_discuss and issue_select_requirements
Combine the dependency/blocks questions with the requirements satisfaction question into a single AskUserQuestion call. Both depend only on the selected version.

### Step 3: Add guidance for the agent
Add a note in the skill that the agent should prefer combining questions when context makes answers predictable, and fall back to sequential when ambiguity requires it.

### Step 4: Test the workflow
Run `/cat:add` with a description argument and verify the reduced number of wizard interactions.

## Post-conditions
- [ ] User-visible behavior unchanged (same questions asked, same information gathered)
- [ ] Tests passing
- [ ] Code quality improved (fewer round-trips in typical `/cat:add` flow)
- [ ] E2E: Running `/cat:add some description` presents fewer separate wizard interactions than before while gathering the same information