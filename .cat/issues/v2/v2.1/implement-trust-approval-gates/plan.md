# Plan

## Goal

Implement the trust level approval gate model so each level maps to a qualitatively distinct number of
checkpoints. Currently all three levels only differ in whether the merge gate is shown; this issue
introduces a pre-implementation gate for `low` and removes all gates for `high`.

## Trust Level Definitions

### low — 2 checkpoints (maximum control)

**Gate 1: Pre-implementation review**

Before the implementation subagent is spawned, present the user with:
- Issue goal (from plan.md `## Goal`)
- Post-conditions (from plan.md `## Post-conditions`)
- Estimated token cost

Options: `Approve and start`, `Request changes`, `Abort`

Only on `Approve and start` does implementation proceed. On `Request changes`, pause for user to
revise the plan. On `Abort`, release lock and exit.

**Gate 2: Pre-merge review (standard approval gate)**

After implementation, confirm, and review phases: present the diff, stakeholder concerns, and commit
summary, then ask for merge approval as today.

### medium — 1 checkpoint (current default behavior)

No pre-implementation gate. Only the standard pre-merge approval gate after implementation completes.
Behavior is identical to the current `trust=medium` workflow.

### high — 0 checkpoints (auto-merge on clean review)

No user approval gates at all. After stakeholder review:

| Stakeholder verdict | Action |
|---------------------|--------|
| APPROVED (no concerns) | Auto-merge immediately |
| CONCERNS (medium/low only) | Auto-merge immediately |
| CONCERNS (high severity) | Pause — present concerns to user, ask: `Approve and merge` / `Fix concerns` / `Abort` |
| REJECTED | Pause — present rejection reasons, ask: `Fix issues` / `Abort` |

The auto-merge path still runs squash and rebase onto the target branch before merging.

## Pre-conditions

(none)

## Post-conditions

- [ ] trust=low: pre-implementation gate shown with issue goal, post-conditions, and estimated tokens
- [ ] trust=low: implementation does not start until user selects `Approve and start`
- [ ] trust=low: `Request changes` at pre-implementation gate returns control to user without starting work
- [ ] trust=low: standard pre-merge gate shown after implementation (unchanged)
- [ ] trust=medium: no pre-implementation gate; only standard pre-merge gate (current behavior preserved)
- [ ] trust=high: no approval gates when stakeholder verdict is APPROVED or CONCERNS (low/medium severity only)
- [ ] trust=high with HIGH severity CONCERNS: pauses and presents concerns before proceeding
- [ ] trust=high with REJECTED verdict: pauses and presents rejection reasons before proceeding
- [ ] trust=high auto-merge path: squash and rebase onto target branch execute before merge
- [ ] Unit tests covering gate routing logic for each trust level
- [ ] No regressions in existing trust=medium workflows
- [ ] E2E: run /cat:work at trust=low and verify two gates appear; trust=medium verify one gate; trust=high verify auto-merge on APPROVED
