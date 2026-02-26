# Plan: sync-local-base-branch-before-merge

## Goal
Prevent local base branch divergence from origin by fetching and fast-forwarding the local base branch before merging
issue branches.

## Satisfies
- None (infrastructure bugfix)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** `git push . origin/X:X` from worktree when base branch is checked out in main worktree; network failures
  during fetch
- **Mitigation:** `git push .` updates refs regardless of checkout state; fetch failure is fatal (fail-fast, no silent
  fallback)

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` - Add fetch + fast-forward of local base
  branch before divergence check
- `plugin/skills/work-merge/first-use.md` - Remove redundant/ineffective fetch from Step 3 (Java now handles it)

## Post-conditions
- [ ] MergeAndCleanup fetches `origin/${baseBranch}` before `getDivergenceCount`
- [ ] MergeAndCleanup fast-forwards local base branch to match `origin/${baseBranch}` via
  `git push . origin/${baseBranch}:${baseBranch}`
- [ ] If fast-forward fails (local has unpushed divergent commits), throw a clear error
- [ ] If fetch fails (no network), throw a clear error (fail-fast, no fallback)
- [ ] work-merge Step 3 no longer contains the ineffective `git fetch` (Java handles it)
- [ ] All existing tests pass
- [ ] E2E: MergeAndCleanup with a stale local base branch successfully incorporates origin's commits before merging

- `mvn -f client/pom.xml test` passes with exit code 0
- MergeAndCleanup.java contains syncBaseBranchWithOrigin called before getDivergenceCount
- work-merge Step 3 no longer contains redundant fetch

## Execution Steps
1. **Add `syncBaseBranchWithOrigin` method to MergeAndCleanup.java:**
   - `git fetch origin ${baseBranch}` — update remote tracking ref
   - `git push . origin/${baseBranch}:${baseBranch}` — fast-forward local to match origin
   - On fetch failure: throw IOException with message about network/remote availability
   - On push failure (non-fast-forward): throw IOException explaining local branch has diverged from origin
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`

2. **Call `syncBaseBranchWithOrigin` before `getDivergenceCount` in `execute()`:**
   - Insert call between dirty-check and divergence-check
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`

3. **Remove redundant fetch from work-merge Step 3:**
   - Remove the `git fetch origin ${BASE_BRANCH}` line
   - Update comment to note that MergeAndCleanup handles origin sync
   - Files: `plugin/skills/work-merge/first-use.md`

4. **Add unit test for syncBaseBranchWithOrigin:**
   - Test: local behind origin → fast-forward succeeds
   - Test: local diverged from origin → throws clear error
   - Test: fetch fails (invalid remote) → throws clear error
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanupTest.java`

5. **Run all tests:**
   - `mvn -f client/pom.xml test`

