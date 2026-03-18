# Plan: prevent-git-user-changes

## Goal
Prevent agents from silently changing the git commit user name or email. Any modification to
`git config user.name` or `git config user.email` must only happen when the user explicitly
requests it.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Agents may set git user config as part of worktree setup or commit workflows
  without the user noticing, overwriting their personal identity.
- **Mitigation:** Add a convention rule and/or a PreToolUse Bash hook that blocks `git config
  user.name` / `git config user.email` writes unless the user has explicitly requested it in the
  current turn.

## Files to Modify
- `plugin/concepts/rules-audience.md` or `InjectSessionInstructions.java` - add end-user
  behavioral rule prohibiting agents from changing git user config without explicit user request
- Optionally: a PreToolUse hook that intercepts `git config user` writes

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Investigate where and why agents currently set git user config (worktree creation, commit
  helpers, skill files).
  - Files: `plugin/skills/`, `plugin/hooks/`, `plugin/concepts/`
- Add a behavioral rule to `InjectSessionInstructions.java` prohibiting agents from modifying
  git user.name / user.email without explicit user instruction.
  - Files: `client/src/main/java/.../InjectSessionInstructions.java`
- Optionally add a PreToolUse Bash hook to enforce this at the tool level.
  - Files: `plugin/hooks/`

## Post-conditions
- [ ] A documented rule exists that prohibits agents from changing git user.name or user.email
  without explicit user request.
- [ ] The rule is injected into every CAT session via SessionStart.
- [ ] No skill or hook sets git user config unconditionally.
- [ ] All tests pass.
