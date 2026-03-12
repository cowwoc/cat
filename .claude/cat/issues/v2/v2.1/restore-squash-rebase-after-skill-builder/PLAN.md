# Plan: Restore re-squash and re-rebase requirement after skill-builder review in work-merge-agent

## Problem

Commit 40162bf2 inadvertently removed the MANDATORY "Re-squash after skill-builder" instruction that M538 had
added to `work-merge-agent/first-use.md`. When skill-builder review adds commits to the branch, the branch must
be re-squashed by topic AND re-rebased onto the target branch before the approval gate. Without this instruction,
agents skip these mandatory steps and present the approval gate on an un-squashed, un-rebased state — making user
approval unreliable since the actual merge state differs from what was shown.

Recorded as M538 (recurrence) via commit 81dc31bd.

## Parent Requirements

None

## Root Cause

Commit 40162bf2 modified the Pre-Gate Skill-Builder Review section in `work-merge-agent/first-use.md` to add
"Post-Skill-Builder Artifact Cleanup" but dropped the "Re-squash after skill-builder (MANDATORY)" block that
M538 (commit e9583c87) had previously added. The instruction deletion meant agents had no guidance to re-squash
and re-rebase after skill-builder completed.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — restoring a previously existing instruction
- **Mitigation:** Read current file before editing to confirm exact location of skill-builder section

## Files to Modify

- `plugin/skills/work-merge-agent/first-use.md` — restore "Re-squash and Re-rebase after skill-builder
  (MANDATORY)" instruction in Step 9 Pre-Gate Skill-Builder Review section, after the instruction to invoke
  /cat:skill-builder and before the "If no skill or command files were modified" line

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/work-merge-agent/first-use.md` Step 9 Pre-Gate Skill-Builder Review section, after
  "Invoke `/cat:skill-builder` with the path to each modified skill or command. Review the output and address
  any priming issues or structural problems it identifies." — add a mandatory re-squash and re-rebase block:
  - The block must check for new commits after skill-builder (`git log --oneline "${TARGET_BRANCH}..HEAD"`)
  - If new commits exist, re-invoke `cat:git-squash-agent` with same primary commit message from Step 7
  - After re-squashing, re-run Step 8 (cat:git-rebase-agent) to rebase onto target branch
  - Mark as MANDATORY and BLOCKING: do NOT present approval gate until squash AND rebase complete
  - Files: `plugin/skills/work-merge-agent/first-use.md`

## Post-conditions

- [ ] `work-merge-agent/first-use.md` Step 9 Pre-Gate Skill-Builder Review section contains explicit instruction
  to check for new commits after skill-builder completes
- [ ] Instruction requires re-invoking `cat:git-squash-agent` if new commits were added by skill-builder
- [ ] Instruction requires re-running Step 8 (rebase) after re-squashing
- [ ] Instruction explicitly states: do NOT present approval gate until squash AND rebase complete after
  skill-builder
- [ ] Instruction is marked MANDATORY and BLOCKING
