# Plan: Verify Subagent Commit Messages (M484 Prevention)

## Goal
Add commit message verification to the work-with-issue orchestrator so that the main agent detects when a subagent
uses a different commit message than what was specified in the delegation prompt.

## Satisfies
- None (infrastructure/prevention issue from M484 learning)

## Problem
The work-with-issue skill delegates implementation to a subagent and asks it to return JSON including
`commits[].message`. The orchestrator trusts this self-reported value without verification. When a subagent uses a
wrong commit message (e.g., "squash commit" instead of the specified descriptive message), the orchestrator has no way
to detect it before the squash/merge phase propagates the bad message.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Minimal - adds a verification step to existing workflow
- **Mitigation:** Verification is advisory (warns orchestrator) not blocking

## Files to Modify
- `plugin/skills/work-with-issue/first-use.md` - Add verification step after execution phase

## Acceptance Criteria
- [ ] After execution subagent returns, orchestrator runs `git log` on the worktree to get actual commit messages
- [ ] Orchestrator compares actual messages against subagent-reported messages
- [ ] Mismatch triggers mandatory fix (amend commit message before proceeding to review)

## Execution Steps
1. **Add commit message verification after execution result parsing:** In `plugin/skills/work-with-issue/first-use.md`,
   after the "Handle Execution Result" section (around line 253), add instructions for the orchestrator to verify
   commit messages by running `git -C ${WORKTREE_PATH} log --format="%H %s" ${BASE_BRANCH}..HEAD` and comparing against
   the subagent's reported `commits[].message` values.
   - Files: `plugin/skills/work-with-issue/first-use.md`

2. **Add mismatch handling:** When a mismatch is detected, the orchestrator must amend the commit message using
   `git -C ${WORKTREE_PATH} commit --amend -m "correct message"` (if single commit) or interactive rebase for multiple
   commits.
   - Files: `plugin/skills/work-with-issue/first-use.md`

## Success Criteria
- [ ] work-with-issue first-use.md includes commit message verification step
- [ ] Verification compares git log output against subagent-reported messages
- [ ] Mismatch triggers mandatory amend operation to fix commit messages
