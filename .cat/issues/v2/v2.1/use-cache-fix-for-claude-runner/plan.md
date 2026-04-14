# Plan: use-cache-fix-for-claude-runner

## Goal

Update the `claude-runner` skill to invoke the `claude-code-cache-fix` build (from https://github.com/cnighswonger/claude-code-cache-fix) instead of the default `claude` binary when launching nested Claude instances. This resolves cache-related issues that occur in nested Claude Code invocations.

## Parent Requirements

None — infrastructure improvement for nested Claude instances

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** If `claude-code-cache-fix` is not installed, nested invocations would fail silently
- **Mitigation:** Check for binary at runtime; output a warning and fall back to unpatched `claude` if not found

## Files to Modify

- `plugin/skills/claude-runner/SKILL.md` — update to use `claude-code-cache-fix` with fallback to `claude`
- `plugin/skills/claude-runner/first-use.md` — update to document the cache-fix behavior and fallback logic (if it exists)

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Jobs

<!-- Optional: skills that require main-agent-level execution -->
<!-- Remove this section if the issue has no pre-delegation skills -->

## Jobs

### Job 1: Update claude-runner SKILL.md

- Modify `plugin/skills/claude-runner/SKILL.md` to invoke `claude-code-cache-fix` instead of `claude` when launching nested instances
- The binary is expected to already be installed; check using `command -v claude-code-cache-fix`
- If not found, print a warning to stderr and fall back to unpatched `claude`
- Ensure all arguments are passed through unchanged to whichever binary is selected
  - Files: `plugin/skills/claude-runner/SKILL.md`

### Job 2: Add empirical test for warning propagation

- Write an empirical test that simulates `claude-code-cache-fix` being absent
- Verify that the parent agent receives and reports the warning message
  - Files: as appropriate under `plugin/tests/` or alongside the skill

### Job 3: Update or create first-use.md (if needed)

- If `plugin/skills/claude-runner/first-use.md` exists, update it to document that the skill uses the cache-fix build and explain the fallback behavior
- If it does not exist and documentation is needed, create it with license header
  - Files: `plugin/skills/claude-runner/first-use.md`

## Post-conditions

- [ ] SKILL.md invokes `claude-code-cache-fix` when available; falls back to `claude` with a warning when not
- [ ] Warning is emitted to stderr when falling back
- [ ] Empirical test verifies that the parent agent reports the warning when cache-fix is unavailable
- [ ] Tests passing: `mvn -f client/pom.xml verify -e` exits 0
