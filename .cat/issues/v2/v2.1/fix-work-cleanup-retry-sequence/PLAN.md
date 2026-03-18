# Plan: fix-work-cleanup-retry-sequence

## Goal
When the work skill's `work-prepare` returns an ERROR due to an existing worktree, and the user selects
"clean up and retry" via AskUserQuestion, the orchestrator must retry `work-prepare` immediately after
cleanup-agent completes — not branch to other skills or workflows.

## Background
Learning M469 identified a control-flow error: after cleanup-agent completed, the agent invoked
`cat:extract-investigation-context-agent` instead of retrying `work-prepare`. The prescribed
cleanup → retry workflow was not followed.

## Satisfies
- None (prevention issue from M469)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must not change the existing ERROR → cleanup → retry sequence for other error types
- **Mitigation:** Narrow change scope to the existing worktree ERROR path only

## Files to Modify
- `plugin/skills/work/first-use.md` — add explicit instruction: after cleanup-agent succeeds, retry
  work-prepare immediately before any other action

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Read the current work first-use.md ERROR handling section
  - Files: `plugin/skills/work/first-use.md`
- Add explicit instruction in the ERROR handling table: when ERROR is "existing worktree" and
  user selects cleanup, the ONLY next action after cleanup-agent returns is to retry work-prepare
  - Files: `plugin/skills/work/first-use.md`

## Post-conditions
- [ ] `plugin/skills/work/first-use.md` ERROR handling section explicitly states: after cleanup-agent
  completes, the next step must be retrying work-prepare, with no intervening skill invocations
- [ ] The cleanup-and-retry workflow is unambiguous from the skill documentation alone
