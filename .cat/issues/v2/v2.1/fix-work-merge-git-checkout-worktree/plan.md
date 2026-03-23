# Plan

## Goal

Fix the merge step in the work-merge-agent so it uses `git -C "${CLAUDE_PROJECT_DIR}" merge --ff-only`
instead of relying on `cd /workspace` before running `git merge --ff-only`. The root cause is that
`plugin/concepts/merge-and-cleanup.md` Section 7 shows a `git merge --ff-only` pattern
which fails when the agent runs `git checkout <target-branch>` inside a worktree (the target branch is
already checked out in the main workspace, causing a fatal error that is silently ignored, and the
subsequent merge lands on the wrong branch).

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/concepts/merge-and-cleanup.md` Section 7 no longer shows `cd /workspace` before `git merge --ff-only`
- [ ] Section 7 uses `git -C "${CLAUDE_PROJECT_DIR}" merge --ff-only {source-branch}` as the correct pattern
- [ ] No hardcoded `/workspace` path references remain in the merge workflow sections of the conceptual doc
- [ ] E2E verification: review the updated conceptual doc and confirm it no longer guides an agent toward `git checkout <target-branch>` before merging
