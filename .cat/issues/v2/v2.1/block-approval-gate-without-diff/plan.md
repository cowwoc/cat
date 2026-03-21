# Plan: block-approval-gate-without-diff

## Goal

Upgrade `WarnApprovalWithoutRenderDiff` from warning-only to blocking enforcement: when `cat:get-diff` has
not been invoked in the session, the approval gate is blocked outright rather than merely warned. Git-only
operations (BFG history rewrites, force pushes) that do not change the working tree are exempt.

## Parent Requirements

None (enforcement hardening)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Overly broad blocking could prevent legitimate approval gates (e.g., git-only workflows).
  False positives in git-only detection would degrade experience.
- **Mitigation:** The exemption logic checks for specific git-only signals in recent session output (BFG
  invocation, `git push --force`, `git filter-repo`). The existing warning path is preserved for
  reformatted-diff detection. Tests cover both block and exempt paths.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java` — change
  `Result.withContext(warning)` to `Result.block(warning)` for the no-get-diff case; add git-only
  operation detection to exempt BFG/force-push sessions from blocking
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnApprovalWithoutRenderDiffTest.java` — update
  `missingGetDiffTriggersWarning` to assert `result.blocked()` is true; add two new tests: one verifying
  BFG exemption, one verifying force-push exemption

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Read implementation and test files to understand current structure**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java`
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnApprovalWithoutRenderDiffTest.java`

2. **Invoke `cat:tdd-implementation` before making any changes**
   - Write failing tests first, then update the implementation to make them pass

3. **Add git-only operation detection method to `WarnApprovalWithoutRenderDiff`**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java`
   - Add a private `isGitOnlyOperation(String recentContent)` method that returns `true` when the
     recent session content contains signals of git-only operations that do not change the working tree:
     - BFG Repo-Cleaner invocation (e.g., `"bfg"` or `"BFG"` in content)
     - Force push (`git push --force` or `git push -f`)
     - `git filter-repo` invocations
   - Add a constant `GIT_ONLY_PATTERNS` (`Pattern`) to match these signals in a single pass
   - Example pattern: `Pattern.compile("bfg|BFG|git push.*--force|git push.*-f\\b|filter-repo")`

4. **Change the no-get-diff path from warning to blocking**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java`
   - In `checkSessionForGetDiff()`, before the existing `getDiffCount == 0` check, call
     `isGitOnlyOperation(recentContent)` and return `Result.allow()` if it returns `true`
   - Change `return Result.withContext(warning)` in the `getDiffCount == 0 && boxCharsCount <
     MIN_BOX_CHARS_FOR_RENDER_DIFF` branch to `return Result.block(warning)`
   - Update the Javadoc for `WarnApprovalWithoutRenderDiff` to reflect blocking behavior and exemptions
   - Leave the reformatted-diff path (`getDiffCount > 0 && boxCharsCount < MIN_BOX_CHARS_WITH_INVOCATION`)
     unchanged — it continues to use `Result.withContext()` since it is a weaker signal

5. **Update `missingGetDiffTriggersWarning` test to assert blocking**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnApprovalWithoutRenderDiffTest.java`
   - Add `requireThat(result.blocked(), "blocked").isTrue()` to the existing test
   - Rename the test to `missingGetDiffBlocksApproval` to reflect the new behavior

6. **Add test: BFG history rewrite is exempt from blocking**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnApprovalWithoutRenderDiffTest.java`
   - Test name: `bfgHistoryRewriteIsExemptFromBlock`
   - Session content: includes `"bfg"` and `"Running BFG Repo-Cleaner"` but no `get-diff` and no box chars
   - Assert: `result.blocked()` is `false` and `result.additionalContext()` is empty

7. **Add test: force push is exempt from blocking**
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnApprovalWithoutRenderDiffTest.java`
   - Test name: `forcePushIsExemptFromBlock`
   - Session content: includes `"git push --force"` but no `get-diff` and no box chars
   - Assert: `result.blocked()` is `false` and `result.additionalContext()` is empty

8. **Run all tests**
   - `mvn -f client/pom.xml test`
   - All tests must pass before proceeding

## Post-conditions

- [ ] `WarnApprovalWithoutRenderDiff.checkSessionForGetDiff()` returns `Result.block()` (not
  `Result.withContext()`) when `get-diff` is absent and box chars are insufficient
- [ ] Sessions containing BFG, `git push --force`, or `git filter-repo` signals bypass blocking and
  return `Result.allow()`
- [ ] `WarnApprovalWithoutRenderDiffTest.missingGetDiffBlocksApproval` asserts `result.blocked()` is `true`
- [ ] New test `bfgHistoryRewriteIsExemptFromBlock` passes
- [ ] New test `forcePushIsExemptFromBlock` passes
- [ ] All existing `WarnApprovalWithoutRenderDiffTest` tests pass
- [ ] `mvn -f client/pom.xml test` exits 0
- [ ] E2E: trigger an approval gate without invoking `cat:get-diff`; confirm the gate is blocked (not
  just warned) with the RENDER-DIFF NOT DETECTED message
