# Plan: add-last-request-recap-to-approval-gate

## Problem

When the approval gate is presented to the user, it shows the issue summary and diff but does not
remind the user what their last change request was for that specific issue. After long workflows with
interruptions (learn invocations, unrelated requests), the user loses context about what they last
asked to change in the issue being approved.

## Parent Requirements

None

## Expected vs Actual

- **Expected:** Before the approval gate AskUserQuestion, the agent reminds the user of the last
  change they requested for the issue being approved, and what was done about it.
- **Actual:** The approval gate jumps straight to the diff and approval question without context.

## Root Cause

The `work-merge-agent`'s Step 9 (`### Present Changes Before Approval Gate`) does not include
an instruction to recap the user's last issue-specific change request before invoking
AskUserQuestion. The sequence ends at stakeholder concerns (step 6) and goes straight to the
approval question.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — additive documentation change only.
- **Mitigation:** The instruction is a behavioral hint; if no issue-specific change request exists,
  the agent skips the recap naturally.

## Files to Modify

- `plugin/skills/work-merge-agent/first-use.md` — add step 7 to the
  `### Present Changes Before Approval Gate` section (after stakeholder concerns, before
  AskUserQuestion) that recaps the user's last change request for the issue being approved.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/work-merge-agent/first-use.md`, locate the line:
  ```
  Invoke AskUserQuestion ONLY AFTER all six items above are output in the current turn:
  ```
  Replace it with:
  ```markdown
  7. **Recap last user change request** — scan the conversation for the most recent user message
     that requested a change, revision, or correction **to this specific issue** (e.g., "use the
     simpler approach", "also fix X", "change the test to cover Y"). Exclude unrelated requests
     (other issues, learn invocations, status queries). If found, display:
     ```
     **Last change you requested for this issue:** <brief description of user's request>
     **What was done:** <brief description of action taken>
     ```
     If no issue-specific change request exists in the conversation history, skip this item.

  Invoke AskUserQuestion ONLY AFTER all seven items above are output in the current turn:
  ```
  - Files: `plugin/skills/work-merge-agent/first-use.md`

- Commit: `feature: add last-request recap to approval gate in work-merge-agent`

## Post-conditions

- [ ] `work-merge-agent/first-use.md` `### Present Changes Before Approval Gate` contains
  step 7 "Recap last user change request"
- [ ] Step 7 appears after step 6 (stakeholder concerns) and before the AskUserQuestion call
- [ ] The instruction scopes the recap to the issue being approved (not unrelated requests)
- [ ] The AskUserQuestion line now references "all seven items" instead of "all six items"
- [ ] E2E: Run `/cat:work` on an issue where the user requested a mid-workflow change; the
  approval gate recap appears before the AskUserQuestion
