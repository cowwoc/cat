# Plan: fix-merge-cleanup-retry

## Problem

The work-merge-agent skill's cleanup step (`git worktree remove ... && git branch -D ...`) can be
executed twice when the first attempt fails midway (e.g., due to a git-rebase ERROR that required
manual investigation before proceeding). The skill does not check whether the worktree already
exists before attempting removal, so when cleanup is retried the command fails noisily or partially
succeeds depending on what the first attempt completed.

In the 2.1-handle-persisted-skill-output session, the merge subagent ran the worktree removal
command twice, detected as a cache candidate by session-analyzer.

## Satisfies

None — robustness fix

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — existence check before removal is purely defensive
- **Mitigation:** Use `git worktree list` to confirm worktree exists before removal

## Files to Modify

- `plugin/skills/work-merge-agent/SKILL.md` — add worktree existence guard before cleanup step

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Add worktree existence check before `git worktree remove`:
  ```bash
  if git worktree list | grep -q "${WORKTREE_PATH}"; then
    git worktree remove "${WORKTREE_PATH}"
  fi
  ```
  - Files: `plugin/skills/work-merge-agent/SKILL.md`
- Add branch existence check before `git branch -D`:
  ```bash
  if git branch --list "${ISSUE_BRANCH}" | grep -q "${ISSUE_BRANCH}"; then
    git branch -D "${ISSUE_BRANCH}"
  fi
  ```
  - Files: `plugin/skills/work-merge-agent/SKILL.md`

## Post-conditions

- [ ] Cleanup step guards both worktree removal and branch deletion with existence checks
- [ ] Re-running cleanup after partial first run completes without error
- [ ] All existing tests pass
