# Plan: Auto-rebase diverged branches using rebase --onto before merge

## Goal
When MergeAndCleanup detects branch divergence (base has commits not in issue branch), automatically run
`git rebase --onto <base> <merge-base> <task-branch>` instead of failing with an error. This replays only the
issue-specific commits onto the current base, avoiding the "120 skipped previously applied commit" problem from naive
`git rebase <base>`.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Rebase can have conflicts that need resolution
- **Mitigation:** Keep existing backup/restore pattern; only auto-rebase, still fail on conflicts

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` - Replace divergence error with
  `rebase --onto` logic
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java` - Add test for divergence handling

## Acceptance Criteria
- [ ] MergeAndCleanup auto-rebases when base branch has diverged
- [ ] Uses `rebase --onto` to replay only issue commits (not shared history)
- [ ] Falls back to error with clear message if rebase has conflicts
- [ ] All existing tests still pass

## Execution Steps
1. **Step 1:** In MergeAndCleanup.java, replace the divergence error throw (lines 94-99) with a `rebaseOnto` method
   that runs `git rebase --onto <baseBranch> <merge-base> HEAD` in the worktree
   - Files: MergeAndCleanup.java
2. **Step 2:** The `rebaseOnto` method computes merge-base via `git merge-base HEAD <baseBranch>`, then runs
   `git -C <worktree> rebase --onto <baseBranch> <merge-base> HEAD`
   - Files: MergeAndCleanup.java
3. **Step 3:** If rebase fails (exit code != 0), abort the rebase and throw IOException with conflict details
   - Files: MergeAndCleanup.java
4. **Step 4:** Add test that creates diverged branches and verifies auto-rebase succeeds
   - Files: MergeAndCleanupTest.java

## Success Criteria
- [ ] `mvn -f client/pom.xml test` passes with exit code 0
- [ ] Diverged branch scenario results in successful merge (not error)
