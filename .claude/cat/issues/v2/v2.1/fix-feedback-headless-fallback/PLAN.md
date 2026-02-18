# Plan: fix-feedback-headless-fallback

## Goal
When `GitHubFeedback.openIssue()` fails to open a browser (headless environment), return the pre-filled GitHub URL in
the JSON response instead of failing, so the agent can display it to the user.

## Satisfies
None - infrastructure/reliability fix

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Changing return contract of `openIssue()` could affect callers
- **Mitigation:** Only change behavior on failure path; success path unchanged

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitHubFeedback.java` - Catch browser-open failure in
  `openIssue()`, return JSON with `status: "url_only"` and `url` field
- `plugin/skills/feedback/SKILL.md` - Handle `url_only` status by displaying URL to user

## Acceptance Criteria
- [ ] `openIssue()` returns JSON with `url` field when browser open fails
- [ ] JSON includes `status: "url_only"` to distinguish from successful browser open
- [ ] Feedback skill displays URL to user when browser unavailable
- [ ] No regression when browser IS available (success path unchanged)
- [ ] Tests cover both browser-available and browser-unavailable paths

## Execution Steps
1. **Modify `openIssue()` in GitHubFeedback.java:** Catch `IOException` from `openInBrowser()`. On failure, build
   JSON response with `{"status": "url_only", "url": "...", "message": "Browser unavailable: ..."}` instead of
   propagating the exception. On success, include `{"status": "opened", "url": "..."}`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitHubFeedback.java`
2. **Update `runOpen()` in GitHubFeedback.java:** Remove the IOException catch around `openIssue()` since it no longer
   throws on browser failure. The method now always returns valid JSON.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitHubFeedback.java`
3. **Update feedback skill:** Add handling for `status: "url_only"` in the Step 4 instructions. When the JSON response
   has `status: "url_only"`, display the URL to the user instead of saying "opened in browser".
   - Files: `plugin/skills/feedback/SKILL.md`
4. **Run tests:** Verify all existing tests pass and add test for headless fallback path.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/util/GitHubFeedbackTest.java`

## Success Criteria
- [ ] `mvn -f client/pom.xml test` passes
- [ ] `openIssue()` returns valid JSON with `url` field in both browser-available and browser-unavailable paths
- [ ] Feedback skill instructions handle `url_only` status
