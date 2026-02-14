# Plan: reject-generic-squash-messages

## Problem
Merge subagents (especially haiku-model) pass generic messages like "squash commit" to git-squash-quick.sh instead of
proper conventional commit messages. The work-merge skill tells subagents to use `/cat:git-squash`, but subagents cannot
invoke skills. They fall back to calling the script directly with a generic message. Documentation-level prevention
(work-with-issue line 559) already exists but is ignored by subagents.

## Satisfies
None

## Reproduction Code
```
1. /cat:work spawns merge subagent (haiku model)
2. Merge subagent needs to squash commits
3. Subagent cannot invoke /cat:git-squash skill (subagents lack Skill tool)
4. Subagent calls git-squash-quick.sh directly with "squash commit" as message
5. Script accepts the generic message without validation
```

## Expected vs Actual
- **Expected:** Script rejects generic commit messages and fails with clear error
- **Actual:** Script accepts any string as commit message, including "squash commit"

## Root Cause
git-squash-quick.sh has no validation on the COMMIT_MESSAGE argument. It accepts any non-empty string. Subagents that
can't invoke the git-squash skill construct their own messages, defaulting to generic text.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - only adds input validation to existing script
- **Mitigation:** Blocked messages produce clear error with expected format

## Files to Modify
- `plugin/scripts/git-squash-quick.sh` - Add commit message format validation after argument parsing

## Acceptance Criteria
- [ ] git-squash-quick.sh rejects messages that don't match conventional commit format (`type: description`)
- [ ] Rejection error message shows expected format and lists valid types
- [ ] Valid conventional commit messages are accepted without change

## Execution Steps
1. **Step 1:** Add commit message validation to `plugin/scripts/git-squash-quick.sh`
   - After line 16 (COMMIT_MESSAGE assignment), add validation that message matches `^(feature|bugfix|refactor|test|performance|config|planning|docs):` pattern
   - On failure: print error to stderr with expected format and valid types, exit 1
   - Files: `plugin/scripts/git-squash-quick.sh`

2. **Step 2:** Run existing tests to verify no regression
   - Files: `hooks/pom.xml`

## Success Criteria
- [ ] Script exits with error when given "squash commit" as message
- [ ] Script exits with error when given message without type prefix
- [ ] Script accepts "feature: add user authentication" without error
- [ ] All existing tests pass
