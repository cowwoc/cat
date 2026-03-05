# Plan: fix-work-with-issue-concern-handling-compliance

## Problem
During issue 2.1-prefetch-investigation-context-to-subagent-prompts, when stakeholder review returned CONCERNS status
(1 MEDIUM, 2 LOW), the agent asked the user how they wanted to handle the concerns instead of automatically
applying the patience matrix workflow to defer low-cost concerns and proceed to the approval gate. The work-with-issue-agent
documentation includes the patience matrix algorithm but lacks explicit enforcement that it MUST execute before Step 8
(approval gate), leading agents to treat concern handling as optional user-directed logic.

## Root Cause
The work-with-issue-agent/first-use.md documentation presents the patience matrix workflow (lines 802-1107) and the
approval gate logic (lines 1349-1387) as separate sections without explicit enforcement that they are sequential, not
alternatives. No MANDATORY REQUIREMENT blocks exist to prevent agents from asking users about concerns.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — changes are documentation only; no behavior change to the workflow
- **Mitigation:** Review documentation for clarity and consistency; test understanding with a manual trace-through

## Files to Modify
- `plugin/skills/work-with-issue-agent/first-use.md` - add MANDATORY REQUIREMENT block before approval gate to
  enforce patience matrix execution

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Read `plugin/skills/work-with-issue-agent/first-use.md` to understand current structure of patience matrix
  (lines 802-1107) and approval gate sections (lines 1349-1387)
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
- Add MANDATORY REQUIREMENT block before line 1349 to explicitly state:
  1. Patience matrix MUST execute before approval gate
  2. Do NOT ask users how to handle concerns — the workflow is automatic
  3. If about to present approval gate without having run patience matrix, STOP
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions
- [ ] MANDATORY REQUIREMENT block is present and clearly visible before the approval gate section
- [ ] Text explicitly states that patience matrix must execute before Step 8
- [ ] Text clarifies that concern handling is not optional or user-directed
- [ ] Documentation accurately reflects the sequential nature of patience matrix → approval gate
- [ ] Changes are additive only; no existing content is removed
