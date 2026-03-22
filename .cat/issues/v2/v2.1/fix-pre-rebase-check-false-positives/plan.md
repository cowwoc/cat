# Plan

## Goal

Fix the pre-rebase path consistency check (content-references category) to only flag files that were
modified by the issue's commits, not all files differing from the target branch that happen to contain
old path references. The current implementation produces false positives when the target branch has
advanced after the worktree was forked: files that v2.1 updated (e.g., `.cat/issues/` plan.md files
with old skill paths) appear different from the target and contain old paths, but the issue's commits
never touched them. During rebase, git would naturally take v2.1's version of these untouched files,
resolving the old-path references automatically.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: the pre-rebase check no longer blocks rebase when the flagged files were not modified
  by the issue's commits
- [ ] Regression test added: a worktree whose issue commits don't touch any files containing old paths
  rebases successfully, even when the worktree has stale files (from before v2.1 advanced) that contain
  old path references
- [ ] All existing pre-rebase check tests pass (true positives — files the issue DID modify with old
  paths — are still correctly blocked)
- [ ] No regressions: `mvn -f client/pom.xml test` exits with code 0
- [ ] E2E: reproduce the exact false-positive scenario (issue commits touching only Java source, worktree
  has stale plan.md files with old paths) and confirm the rebase proceeds without blocking
