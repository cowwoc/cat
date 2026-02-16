# Plan: planning-commits-on-issue-branch

## Problem

When creating new issues (via `/cat:add`) while an issue branch/worktree is active, planning
commits (STATE.md, PLAN.md) are created on the base branch (e.g., v2.1) instead of the current
issue branch. This causes problems:

1. The base branch accumulates planning commits unrelated to the current work
2. Other worktrees see these commits when they rebase, creating noise
3. Planning commits for issues discovered during implementation logically belong with that work
4. Squash-by-topic must skip planning commits on the base branch that were created mid-issue

## Satisfies

None (workflow improvement)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Must handle the case where no issue branch exists (fall back to base branch);
  planning commits on issue branches get squashed or need special handling during merge
- **Mitigation:** Only change behavior when an active issue worktree is detected (cat-base file
  exists); planning commits are excluded from squash since they're a different topic

## Files to Modify

- `plugin/skills/add/first-use.md` — Detect active issue worktree and commit to it instead of
  base branch
- `plugin/skills/git-squash/first-use.md` — Document that planning commits on issue branches
  should be kept separate during squash-by-topic (different topic)

## Acceptance Criteria

- [ ] `/cat:add` creates planning commits on the current issue branch when inside an active
  worktree (cat-base file exists)
- [ ] `/cat:add` falls back to base branch when no issue worktree is active
- [ ] Planning commits on issue branches are preserved as separate commits during squash-by-topic
- [ ] New issues created mid-work are visible in the issue branch history
- [ ] Merge to base branch includes the planning commits

## Execution Steps

1. **Detect active worktree in /cat:add:** Check for `.git/cat-base` file to determine if
   running inside an issue worktree. If so, commit planning files to the current branch.
   - Files: `plugin/skills/add/first-use.md`

2. **Update squash-by-topic guidance:** Document that planning commits discovered on issue
   branches are a separate topic and should not be squashed with implementation commits.
   - Files: `plugin/skills/git-squash/first-use.md`

3. **Test both paths:** Verify planning commits go to issue branch when active, and to base
   branch when not in a worktree.
