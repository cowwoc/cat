# Plan

## Goal

Prevent the approval gate from being presented to the user until all background stakeholder reviewer
subagents have fully completed. Currently, reviewer agents may still be running when the approval gate
is shown, resulting in stale or missing review output. If any reviewer subagent fails, the gate must
be blocked until the failure is resolved — no partial review results are acceptable.

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] The approval gate is only shown after all spawned reviewer subagents have returned a result
- [ ] If any reviewer subagent fails (error or no output), the gate is blocked and the user is informed
- [ ] The orchestrating agent waits for reviewer completion before transitioning to the merge phase
- [ ] The fix is verified end-to-end: spawn reviewers, confirm gate does not appear until all return
- [ ] All existing tests pass
