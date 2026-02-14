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
- `plugin/agents/work-merge.md` - Replace `/cat:git-squash` skill reference with direct script invocation instructions
- `plugin/skills/work-merge/first-use.md` - Replace `/cat:git-squash` reference with direct `git-squash-quick.sh` call
- `plugin/concepts/subagent-delegation.md` - Add standard fail-fast preamble for impossible instructions

## Acceptance Criteria
- [ ] git-squash-quick.sh rejects messages that don't match conventional commit format (`type: description`)
- [ ] Rejection error message shows expected format and lists valid types
- [ ] Valid conventional commit messages are accepted without change
- [ ] work-merge skill/agent no longer references `/cat:git-squash` skill invocation
- [ ] work-merge instructs subagent to call `git-squash-quick.sh` directly with commit message from COMMITS data
- [ ] subagent-delegation.md includes standard fail-fast preamble for capability limitations

## Execution Steps
1. **Step 1:** Add commit message validation to `plugin/scripts/git-squash-quick.sh`
   - After line 16 (COMMIT_MESSAGE assignment), add validation that message matches
     `^(feature|bugfix|refactor|test|performance|config|planning|docs):` pattern
   - On failure: print error to stderr with expected format and valid types, exit 1
   - Files: `plugin/scripts/git-squash-quick.sh`

2. **Step 2:** Update work-merge skill to use direct script invocation
   - In `plugin/skills/work-merge/first-use.md` Step 1, replace "using `/cat:git-squash`" with direct
     `git-squash-quick.sh` invocation including explicit commit message construction from COMMITS input
   - Show how to derive the commit message: use the primary commit's message from the COMMITS JSON
   - Files: `plugin/skills/work-merge/first-use.md`

3. **Step 3:** Update work-merge agent definition
   - In `plugin/agents/work-merge.md`, remove `git-squash` from preloaded skills if it only served as
     a Skill tool reference (keep if its content is still useful as context)
   - Update any instructions that say "use git-squash skill" to say "call git-squash-quick.sh directly"
   - Files: `plugin/agents/work-merge.md`

4. **Step 4:** Add fail-fast preamble for capability limitations to subagent-delegation.md
   - Add a "Capability Limitations" section with standard instruction: "If any step requires a tool or
     capability you don't have access to (e.g., Skill tool, spawning subagents), return BLOCKED status
     immediately. Do NOT silently substitute or work around missing capabilities."
   - Files: `plugin/concepts/subagent-delegation.md`

5. **Step 5:** Run existing tests to verify no regression
   - Files: `hooks/pom.xml`

## Success Criteria
- [ ] Script exits with error when given "squash commit" as message
- [ ] Script exits with error when given message without type prefix
- [ ] Script accepts "feature: add user authentication" without error
- [ ] work-merge/first-use.md contains `git-squash-quick.sh` direct invocation (not `/cat:git-squash`)
- [ ] subagent-delegation.md contains capability limitation fail-fast instruction
- [ ] All existing tests pass
