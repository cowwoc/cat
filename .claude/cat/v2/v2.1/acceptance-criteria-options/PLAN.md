# Plan: acceptance-criteria-options

## Goal
Replace freeform acceptance criteria input in /cat:add with context-aware selectable options,
improving consistency and reducing user effort.

## Satisfies
None - workflow improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Options may not cover all use cases
- **Mitigation:** Always include "Custom" option for freeform fallback

## Files to Modify
- plugin/skills/add.md - Main /cat:add skill definition

## Execution Steps
1. **Step 1:** Define acceptance criteria option sets by task type
   - Files: plugin/skills/add.md
   - Verify: Feature/Bugfix/Refactor have distinct relevant options

2. **Step 2:** Replace freeform question with AskUserQuestion
   - Files: plugin/skills/add.md
   - Verify: Options presented instead of text prompt

3. **Step 3:** Enable multi-select for acceptance criteria
   - Files: plugin/skills/add.md
   - Verify: User can select multiple criteria

4. **Step 4:** Add "Custom" option with freeform fallback
   - Files: plugin/skills/add.md
   - Verify: Selecting "Custom" prompts for text input

## Acceptance Criteria
- [ ] Options provided for all acceptance criteria questions
- [ ] Context-aware options based on task type (Feature/Bugfix/Refactor)
- [ ] Custom option always available for freeform input
- [ ] Multi-select supported for combining criteria
