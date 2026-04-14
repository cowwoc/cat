# Plan: use-cache-fix-for-claude-runner

## Goal

Update the `claude-runner` skill to invoke the `claude-code-cache-fix` build (from https://github.com/cnighswonger/claude-code-cache-fix) instead of the default `claude` binary when launching nested Claude instances. This resolves cache-related issues that occur in nested Claude Code invocations.

## Parent Requirements

None — infrastructure improvement for nested Claude instances

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Dependency on external github.com/cnighswonger/claude-code-cache-fix repository; need to verify the binary is available at runtime and fallback strategy if unavailable
- **Mitigation:** Graceful fallback to default `claude` if cache-fix binary not found; clear error messages if invocation fails

## Files to Modify

- `plugin/skills/claude-runner/SKILL.md` — update preprocessor directive to use cache-fix binary instead of default claude
- `plugin/skills/claude-runner/first-use.md` — update instructions to document the cache-fix behavior and fallback logic (if a first-use.md exists)

## Pre-conditions

- [ ] All dependent issues are closed
- [ ] claude-code-cache-fix binary is available in the runtime environment (or the skill handles its absence gracefully)

## Main Agent Jobs

<!-- Optional: skills that require main-agent-level execution -->
<!-- Remove this section if the issue has no pre-delegation skills -->

## Jobs

### Job 1: Update claude-runner SKILL.md

- Modify the preprocessor directive in `plugin/skills/claude-runner/SKILL.md` to invoke the `claude-code-cache-fix` binary instead of the default `claude` binary
- Ensure the invocation includes proper argument passing to the nested instance (e.g., the issue ID and any other required parameters)
- Add fallback logic: if `claude-code-cache-fix` is not available, output a warning and gracefully fall back to the default `claude` binary (continue executing without the cache fix)
  - Files: `plugin/skills/claude-runner/SKILL.md`

### Job 2: Update or create first-use.md (if needed)

- If `plugin/skills/claude-runner/first-use.md` exists, update it to document that the skill now uses the cache-fix build and explain the fallback behavior
- If it does not exist and instructions are needed, create it with license header
  - Files: `plugin/skills/claude-runner/first-use.md`

## Post-conditions

- [ ] SKILL.md updated to reference cache-fix binary (claude-code-cache-fix instead of claude)
- [ ] Preprocessor directive properly passes arguments to nested Claude instance
- [ ] Fallback to default claude is implemented with warning message when cache-fix binary or claude's JS is not found
- [ ] Empirical test verifies that parent agent properly reports the warning when cache-fix is unavailable
- [ ] Tests passing: `mvn -f client/pom.xml verify -e` exits 0
- [ ] claude-runner skill behavior verified end-to-end with a test nested invocation
