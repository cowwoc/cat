# Plan: remove-plugin-root-workaround

## Current State
Three plugin files work around anthropics/claude-code#38699, where `CLAUDE_PLUGIN_ROOT` resolved to the source
directory instead of the cache directory in hooks and skill preprocessor directives:

1. `plugin/agents/work-verify.md` and `plugin/concepts/instruction-test-design.md` hardcode the absolute cache path
   `/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin` instead of using `${CLAUDE_PLUGIN_DATA}/client/bin`.
2. `plugin/hooks/session-start.sh` creates a `plugin_root/client -> installPath/client` symlink bridge so that
   `${CLAUDE_PLUGIN_DATA}/client/bin` works from both the hook/skill context and the agent Bash environment (lines 388–408).

## Target State
With the bug fixed, `CLAUDE_PLUGIN_ROOT` consistently points to the cache (installPath) in all contexts, so:
- All files use `${CLAUDE_PLUGIN_DATA}/client/bin` directly.
- The symlink bridge in session-start.sh is removed (the condition `install_path != plugin_root` is never true).

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — behavior is identical; only the path construction changes
- **Mitigation:** The bug is confirmed fixed upstream; all other plugin files already use `${CLAUDE_PLUGIN_ROOT}` successfully

## Files to Modify
- `plugin/agents/work-verify.md` — replace hardcoded `CLIENT_BIN` with `${CLAUDE_PLUGIN_DATA}/client/bin`
- `plugin/concepts/instruction-test-design.md` — replace hardcoded `RUNNER` path with `${CLAUDE_PLUGIN_DATA}/client/bin/empirical-test-runner`
- `plugin/hooks/session-start.sh` — remove the symlink bridge block (lines 388–408) and the now-dead `installed_plugins_json` read; simplify to `local download_target="$jdk_path"`

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- In `plugin/agents/work-verify.md`: replace `CLIENT_BIN="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin"` with `CLIENT_BIN="${CLAUDE_PLUGIN_DATA}/client/bin"`
- In `plugin/concepts/instruction-test-design.md`: replace `RUNNER="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/empirical-test-runner"` with `RUNNER="${CLAUDE_PLUGIN_DATA}/client/bin/empirical-test-runner"`
- In `plugin/hooks/session-start.sh`: remove the `installed_plugins_json` block (lines 388–408) that reads `install_path` and creates the symlink bridge; replace with `local download_target="$jdk_path"` (the comment block at lines 380–384 describing the workaround should also be removed)

## Post-conditions
- [ ] No occurrences of `/home/node/.config/claude/plugins/cache` remain in `plugin/`
- [ ] `plugin/agents/work-verify.md` uses `${CLAUDE_PLUGIN_DATA}/client/bin` for `CLIENT_BIN`
- [ ] `plugin/concepts/instruction-test-design.md` uses `${CLAUDE_PLUGIN_DATA}/client/bin/empirical-test-runner` for `RUNNER`
- [ ] `plugin/hooks/session-start.sh` contains no symlink creation (`ln -sfn`) for the client directory
- [ ] `plugin/hooks/session-start.sh` still correctly downloads the JDK runtime to `$jdk_path`
