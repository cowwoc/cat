# Plan

## Goal

Split CAT merge and cleanup workflow so the issue branch is merged from the stable workspace runtime first, then cleanup runs from the workspace only after the merge is verified. Cleanup must be retryable and must refuse to run from inside the worktree it is deleting.

## Pre-conditions

(none)

## Post-conditions

- [ ] The merge workflow fast-forwards the target branch from a stable workspace/runtime context before deleting any worktree.
- [ ] Worktree, branch, and lock cleanup runs only after the target branch tip is verified at the expected merged commit.
- [ ] Cleanup is idempotent and can finish safely when merge already succeeded but worktree, branch, or lock artifacts remain.
- [ ] Cleanup refuses unsafe invocation when cwd, plugin root, Java runtime, or executable location is inside the worktree being removed.
- [ ] Merge success and cleanup status are reported distinctly so partial cleanup failure is not mistaken for merge failure.
- [ ] Regression tests cover the merge-complete cleanup-retry path and unsafe self-deleting runtime guard.
- [ ] Full verification passes with `mvn -f client/pom.xml verify -e`.
