# Plan: accept-approve-merge-without-wizard

## Goal

Allow users to type "approve and merge" (or similar phrases) directly in chat to approve the merge gate,
without requiring the AskUserQuestion wizard.

## Satisfies

None (usability improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Accidental approval from user messages that happen to contain "approve" and "merge"
- **Mitigation:** Require both keywords together in a recent user message; only check within the last
  few messages before the merge attempt

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java` - Relax the
  `hasAskQuestion` requirement; accept direct user messages containing "approve and merge" as valid approval
- `plugin/skills/work-with-issue/first-use.md` - Update Step 7 to skip AskUserQuestion if user already
  typed approval (e.g., "approve and merge" appeared in recent conversation)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceApprovalBeforeMergeTest.java` - Add tests
  for direct approval messages

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Update EnforceApprovalBeforeMerge.java** to accept direct user approval messages:
   - Add a `checkDirectApprovalMessage()` method that scans recent user messages for phrases like
     "approve and merge", "approve merge", or "approved"
   - Update `checkApprovalInSession()` so that either AskUserQuestion approval OR direct user message
     approval satisfies the gate
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`

2. **Update work-with-issue/first-use.md Step 7** to detect prior approval:
   - Before presenting AskUserQuestion, check if the user already typed "approve and merge" in the
     current conversation
   - If direct approval detected, skip the AskUserQuestion wizard and proceed to merge
   - If no prior approval, present AskUserQuestion as before
   - Files: `plugin/skills/work-with-issue/first-use.md`

3. **Add tests for direct approval detection**
   - Test that "approve and merge" in a user message is accepted
   - Test that "approve" alone without "merge" is not accepted
   - Test that approval keywords in non-user messages (assistant, system) are not accepted
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceApprovalBeforeMergeTest.java`

## Post-conditions
- [ ] `EnforceApprovalBeforeMerge` accepts direct user messages containing "approve and merge"
- [ ] `work-with-issue/first-use.md` skips AskUserQuestion when user already typed approval
- [ ] AskUserQuestion still works as a fallback when no direct approval is detected
- [ ] All tests pass
