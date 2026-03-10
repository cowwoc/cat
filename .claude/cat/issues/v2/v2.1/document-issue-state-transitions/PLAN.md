# Plan: document-issue-state-transitions

## Problem

The work-merge and work-with-issue skills do not explicitly define the three distinct issue states,
causing agents to confuse "implementation complete" with "issue fully merged and closed." M527:
an agent refused to squash commits on an active branch because STATE.md showed `closed`, incorrectly
inferring the issue was already merged and cleaned up.

## Parent Requirements

None

## Root Cause

No explicit documentation in skill files defines the three lifecycle states an issue passes through
during the merge workflow. Agents infer "merged" from STATE.md status alone, which is incorrect —
STATE.md `closed` means implementation finished, not that the merge-and-cleanup tool ran.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None (documentation-only change)
- **Mitigation:** Verify skill files read correctly by running skill-builder review after changes

## Files to Modify

- `plugin/skills/work-merge-agent/SKILL.md` — add explicit issue state transition documentation
- `plugin/skills/work-with-issue-agent/SKILL.md` — add lifecycle state reference

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add "Issue Lifecycle States" section to `work-merge-agent/SKILL.md` defining:
  - State 1: Implementation running — confirm/review/merge phases active, worktree exists, lock held
  - State 2: Merge complete — merge-and-cleanup tool ran, squashed commit on TARGET_BRANCH, but worktree may still exist briefly
  - State 3: Issue closed — worktree removed, lock released, branch deleted, STATE.md: closed
  - Files: `plugin/skills/work-merge-agent/SKILL.md`
- Add warning note to `work-with-issue-agent/SKILL.md`: "STATE.md status: closed means implementation done,
  NOT that the issue was merged. Verify merge via git branch state or merge-and-cleanup tool result."
  - Files: `plugin/skills/work-with-issue-agent/SKILL.md`
- Add to `work-merge-agent/SKILL.md` Step 7 (Squash): explicit note that squash/rebase can be performed
  on branches where STATE.md shows `closed` — this is expected during the merge preparation phase.
  - Files: `plugin/skills/work-merge-agent/SKILL.md`

## Post-conditions

- [ ] `work-merge-agent/SKILL.md` contains a section explicitly defining the three issue lifecycle states
- [ ] `work-with-issue-agent/SKILL.md` warns agents not to infer "merged" from STATE.md status alone
- [ ] `work-merge-agent/SKILL.md` Step 7 clarifies that squash on a "closed" STATE.md branch is expected
- [ ] E2E: Running skill-builder review on both modified files produces no priming concerns about state confusion
