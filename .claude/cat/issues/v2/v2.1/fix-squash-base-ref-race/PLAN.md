# Plan: fix-squash-base-ref-race

## Goal
Fix race condition in git-squash skill where base branch reference can advance between rebase and commit-tree
operations, causing stale files to leak into squashed commits.

## Problem
The git-squash skill calls `git rev-parse` on the base branch twice: once for `git rebase` and once for
`git commit-tree -p`. If the base branch advances between these two calls (e.g., another session commits to v2.1),
the commit-tree parent is newer than what the tree was rebased onto. Files added to base between the two calls appear
as unintended additions or missing deletions in the squashed commit.

## Satisfies
None - infrastructure bugfix

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Change affects squash workflow used by all /cat:work executions
- **Mitigation:** Pin-once pattern is simpler than current approach. Backup-verify-cleanup safety pattern unchanged.

## Files to Modify
- plugin/skills/git-squash/content.md - Pin base reference before rebase, reuse for commit-tree parent

## Acceptance Criteria
- [ ] Base branch reference is resolved once and reused for both rebase and commit-tree
- [ ] No race window between rebase and commit-tree operations
- [ ] Bug fixed with clear pin-once pattern
- [ ] Regression test scenario documented (base advances between rebase and squash)
- [ ] No new issues introduced
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Update Quick Workflow section in plugin/skills/git-squash/content.md
   - Files: plugin/skills/git-squash/content.md
   - Add a pin step before rebase: `BASE=$(git rev-parse <base-branch>)`
   - Change rebase to use pinned ref: `git rebase $BASE`
   - Change commit-tree to use pinned ref: `git commit-tree $TREE -p $BASE -m "$MESSAGE"`
   - Remove separate `git rev-parse` call in commit-tree step
   - Add comment explaining why pinning is necessary (race condition prevention)
2. **Step 2:** Update Interactive Rebase Workflow section similarly
   - Files: plugin/skills/git-squash/content.md
   - Pin base reference before any operations
   - Ensure BASE_COMMIT uses the pinned value
3. **Step 3:** Add a warning note about the race condition
   - Files: plugin/skills/git-squash/content.md
   - Add to Critical Rules section: "MANDATORY: Pin base branch reference before rebase. Do NOT call git rev-parse
     on the base branch separately for rebase and commit-tree."
4. **Step 4:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test

## Success Criteria
- [ ] Base reference pinned once in Quick Workflow
- [ ] Base reference pinned once in Interactive Rebase Workflow
- [ ] Warning documented in Critical Rules
- [ ] All existing tests pass with no regressions
