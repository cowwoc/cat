# Plan: refactor-add-wizard-proposal-ui

## Current State
The cat:add-agent skill uses a multi-step AskUserQuestion wizard (up to 5+ calls) to gather issue
metadata: type selection, description, clarification, smart questioning, type/post-conditions/version,
name selection, dependency selection, and requirements.

## Target State
The agent researches all fields upfront (scans versions, existing issues, requirements, skill deps),
renders a single display box proposal, then asks conversationally for approval. Zero AskUserQuestion
calls on the happy path.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Wizard UI replaced by proposal flow; end result (issue created) unchanged
- **Mitigation:** E2E test verifying issue creation produces correct files

## Files to Modify
- plugin/skills/add-agent/first-use.md - Replace wizard steps with research + proposal + conversational approval

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Replace steps issue_gather_intent, issue_clarify_intent, issue_smart_questioning,
  issue_ask_type_and_criteria, issue_suggest_names, issue_detect_skill_deps,
  issue_discuss_and_requirements with a single research-then-propose flow
  - Files: plugin/skills/add-agent/first-use.md
- Add display box rendering (via cat:get-output-agent) for the proposal
  - Files: plugin/skills/add-agent/first-use.md
- Add conversational approval step: ask "Does this look good? Should I create it?"
  - Files: plugin/skills/add-agent/first-use.md

## Post-conditions
- [ ] cat:add-agent invoked with a description renders a display box proposal with no wizard questions
- [ ] User approves conversationally and issue is created with correct index.json and plan.md
- [ ] All tests pass, no regressions
- [ ] E2E: invoke cat:add-agent with a description, confirm display box appears and conversational approval creates the issue correctly
