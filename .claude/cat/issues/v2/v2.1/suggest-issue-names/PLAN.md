# Plan: suggest-issue-names

## Goal
Replace freeform issue name input in /cat:add with AI-suggested name options based on
the task description, improving naming consistency and reducing user effort.

## Satisfies
None - workflow improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Suggestions may not always be appropriate
- **Mitigation:** Always include "Custom name" option for freeform fallback

## Files to Modify
- plugin/skills/add.md - Main /cat:add skill definition

## Execution Steps
1. **Step 1:** Analyze task description to generate name suggestions
   - Files: plugin/skills/add.md
   - Verify: Names derived from key terms in description

2. **Step 2:** Present multiple name options via AskUserQuestion
   - Files: plugin/skills/add.md
   - Verify: 3-4 suggested names presented as options

3. **Step 3:** Add "Custom name" option with freeform fallback
   - Files: plugin/skills/add.md
   - Verify: Selecting "Custom" prompts for text input

4. **Step 4:** Validate name format and uniqueness before accepting
   - Files: plugin/skills/add.md
   - Verify: Invalid/duplicate names rejected with clear error

## Acceptance Criteria
- [ ] Suggested names based on task description
- [ ] Multiple name options presented (3-4 suggestions)
- [ ] Custom name option always available
- [ ] Name validation ensures format compliance (lowercase, hyphens)
- [ ] Name uniqueness verified for assigned version
