# Plan: remove-plugin-root-workaround

## Current State
Two plugin files hardcode the absolute cache path `/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin`
as a workaround for anthropics/claude-code#38699, where `CLAUDE_PLUGIN_ROOT` resolved to the source directory
instead of the cache directory in agent Bash environments.

## Target State
Both files use `${CLAUDE_PLUGIN_ROOT}/client/bin` consistently, matching every other plugin file.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — behavior is identical after the fix; only the path construction changes
- **Mitigation:** The bug is confirmed fixed upstream; all other plugin files already use `${CLAUDE_PLUGIN_ROOT}` successfully

## Files to Modify
- `plugin/agents/work-verify.md` — replace hardcoded `CLIENT_BIN` assignment with `${CLAUDE_PLUGIN_ROOT}/client/bin`
- `plugin/concepts/instruction-test-design.md` — replace hardcoded `RUNNER` path with `${CLAUDE_PLUGIN_ROOT}/client/bin/empirical-test-runner`

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- In `plugin/agents/work-verify.md` line 163: replace `CLIENT_BIN="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin"` with `CLIENT_BIN="${CLAUDE_PLUGIN_ROOT}/client/bin"`
- In `plugin/concepts/instruction-test-design.md` line 303: replace `RUNNER="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/empirical-test-runner"` with `RUNNER="${CLAUDE_PLUGIN_ROOT}/client/bin/empirical-test-runner"`

## Post-conditions
- [ ] No occurrences of `/home/node/.config/claude/plugins/cache` remain in `plugin/`
- [ ] `plugin/agents/work-verify.md` uses `${CLAUDE_PLUGIN_ROOT}/client/bin` for `CLIENT_BIN`
- [ ] `plugin/concepts/instruction-test-design.md` uses `${CLAUDE_PLUGIN_ROOT}/client/bin/empirical-test-runner` for `RUNNER`
