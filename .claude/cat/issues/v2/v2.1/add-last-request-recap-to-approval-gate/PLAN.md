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

The work-with-issue-agent's Step 9 (Approval Gate) does not include an instruction to recap the
user's last issue-specific change request before presenting the approval question.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — additive documentation change only.
- **Mitigation:** The instruction is a behavioral hint; if no user change request exists, the agent
  skips the recap naturally.

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` — add instruction to Step 9 (Approval Gate)
  to recap the user's last change request for the issue being approved before presenting
  AskUserQuestion.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/work-with-issue-agent/first-use.md`, locate Step 9 (Approval Gate), specifically
  the section `### If trust == "low" or trust == "medium"`. Add a new subsection immediately before
  the `### Check for Prior Direct Approval` subsection:

  ```markdown
  ### Recap Last User Change Request

  Before presenting the approval gate, remind the user of the last change they requested **for this
  specific issue** and what was done about it. This provides context after long workflows with
  interruptions.

  - Scan the conversation for the most recent user message that requested a change, revision, or
    correction to the issue being approved (e.g., "use the simpler approach", "also fix X",
    "change the test to cover Y").
  - Exclude unrelated requests (e.g., requests about other issues, learn invocations, status queries).
  - Display before the approval summary:
    ```
    **Last change you requested for this issue:** <brief description of user's request>
    **What was done:** <brief description of action taken>
    ```
  - If no issue-specific change request exists in the conversation, skip this recap.
  ```

  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

- Commit: `feature: add last-request recap to approval gate in work-with-issue-agent`

## Post-conditions

- [ ] `first-use.md` Step 9 contains a "Recap Last User Change Request" subsection
- [ ] The subsection appears before the "Check for Prior Direct Approval" subsection
- [ ] The instruction scopes the recap to the issue being approved (not unrelated requests)
- [ ] E2E: Run `/cat:work` on an issue where the user requested a mid-workflow change; the
  approval gate includes the recap
