# Plan: fix-approval-gate-empty-answers-handling

## Problem

The work-with-issue Step 8 Approval Gate does not specify how to handle
`toolUseResult.answers = {}` (empty object) from `AskUserQuestion`. When this occurs, the response
text reads "User has answered your questions: ." with nothing after the colon.

Without explicit guidance for the empty-answers case, the agent defaulted to completion-bias
assumption ("the tool succeeded so the user must have approved") and proceeded with an irreversible
git merge without verified user consent. This was recorded as M459.

## Root Cause

Missing documentation: Step 8 specifies "wait for explicit user selection" and lists cases NOT to
proceed on (silence, system reminders, conversational responses), but does not specify that an empty
`answers: {}` object is also a no-consent signal.

## Satisfies

None — bugfix for work-with-issue approval gate methodology (M459 prevention).

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Documentation-only change affecting approval gate protocol
- **Mitigation:** Additive change only — new explicit case added, no existing content removed

## Files to Modify

- `plugin/skills/work-with-issue/first-use.md` — add explicit guidance to Step 8 Approval Gate:
  when `toolUseResult.answers` is an empty object `{}`, treat as no consent and re-present the gate

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Add empty-answers handling to Step 8 Approval Gate

- In Step 8 of `plugin/skills/work-with-issue/first-use.md`, after the existing CRITICAL block
  listing cases not to proceed on, add a new paragraph:
  ```
  **Empty answers detection:** If `toolUseResult.answers` is an empty object `{}`, no selection
  was recorded. The visible signal is "User has answered your questions: ." with nothing after
  the colon. Treat this identically to no response — re-present the approval gate. Unknown
  consent = No consent = STOP.
  ```
  - Files: `plugin/skills/work-with-issue/first-use.md`

- Run `mvn -f client/pom.xml test` to verify no regressions
  - Files: `client/pom.xml`

## Post-conditions

- [ ] `first-use.md` Step 8 explicitly states that `answers: {}` means no selection was recorded
- [ ] Guidance includes the visible signal: "User has answered your questions: ." with nothing after
  the colon
- [ ] Guidance states the correct action: re-present the approval gate
- [ ] E2E: No approval gate proceeds when `AskUserQuestion` returns empty `answers: {}`
- [ ] All tests pass: `mvn -f client/pom.xml test` exits 0
