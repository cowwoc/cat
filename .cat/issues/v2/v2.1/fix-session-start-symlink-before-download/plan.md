# Plan

## Problem

`session-start.sh` downloads the jlink runtime to `plugin_root/client` (the marketplaces path set by `CLAUDE_PLUGIN_ROOT`)
and then creates a symlink from `installPath/client` → `plugin_root/client`. This ordering causes two failures:

1. **If the download fails** (e.g., 404 from GitHub releases), the symlink bridge code is never reached because it is
   inside the `if try_acquire_runtime` success block. No `client/` directory exists anywhere, and all jlink-dependent
   tools (statusline, hooks, skills) are broken for the session.

2. **If `installPath/client` already has a working runtime** from a previous install or download, it is not reused.
   The code always downloads to `plugin_root/client` first, ignoring the existing cache.

This directly causes the statusline not to display: `statusline-command` is invoked via `${CLAUDE_PLUGIN_ROOT}` (the
marketplaces path), which has no working `client/` directory after a failed download.

## Parent Requirements

None

## Reproduction Steps

1. Uninstall the CAT plugin: `claude plugins uninstall cat@cat`
2. Delete both directories: `rm -rf ~/.config/claude/plugins/marketplaces/cat ~/.config/claude/plugins/cache/cat`
3. Reinstall: `claude plugins install cat@cat`
4. Restart Claude Code — session-start.sh attempts to download the jlink runtime
5. If the GitHub release asset returns 404 (e.g., unreleased version), the download fails
6. No `client/` directory is created at either path
7. The statusline command fails because `${CLAUDE_PLUGIN_ROOT}/client/bin/statusline-command` does not exist

## Expected vs Actual

- **Expected:** If `installPath/client` already has a working jlink runtime, create a symlink from
  `plugin_root/client` → `installPath/client` immediately, making tools available without any download.
  If neither path has a runtime, download to `installPath/client` (the cache) and symlink from `plugin_root/client`.
- **Actual:** Always tries to download to `plugin_root/client` first. On failure, no symlink is created and no
  fallback to existing cache is attempted.

## Root Cause

The symlink bridge logic (lines 388–398) runs only after a successful `try_acquire_runtime`, and the download targets
`plugin_root/client` instead of `installPath/client`. The ordering should be reversed: create the symlink first (so
`plugin_root/client` resolves to the cache), then download to `installPath/client` if needed.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Regression Risk:** Changes the download target directory and symlink direction. All paths through the runtime
  acquisition logic are affected.
- **Mitigation:** Existing bats tests for session-start.sh. Add tests for the new symlink-first behavior.

## Files to Modify

- `plugin/hooks/session-start.sh` — Restructure runtime acquisition to:
  1. Read `installPath` from `installed_plugins.json` early
  2. Create symlink `plugin_root/client` → `installPath/client` before calling `try_acquire_runtime`
  3. Change `try_acquire_runtime` to download to `installPath/client` (the cache path) instead of `plugin_root/client`
  4. Remove the post-download symlink bridge block (lines 383–398) since the symlink is now created upfront
- `tests/hooks/session-start.bats` — Add/update tests for:
  - Symlink created before download attempt
  - Existing runtime at `installPath/client` is reused via symlink without download
  - Download failure still leaves symlink pointing to cache (even if cache is empty)

## Test Cases

- [ ] When `installPath/client` has a working runtime, symlink is created and no download is attempted
- [ ] When neither path has a runtime, download targets `installPath/client` and symlink resolves correctly
- [ ] When download fails, symlink still points to `installPath/client` (dangling but correct direction)
- [ ] `${CLAUDE_PLUGIN_ROOT}/client/bin/statusline-command` resolves through symlink to working binary

## Pre-conditions

(none)

## Sub-Agent Waves

### Wave 1

- In `session-start.sh`, restructure the `main()` function:
  1. After reading `plugin_version` and computing `jdk_path="${plugin_root}/${JDK_SUBDIR}"`, read `installPath` from
     `installed_plugins.json` (reuse existing grep/sed logic from lines 391–392)
  2. If `install_path` is non-empty and differs from `plugin_root`, compute `install_jdk_path="${install_path}/${JDK_SUBDIR}"`
  3. Create symlink: if `jdk_path` is not a directory and not a symlink, run `ln -sfn "$install_jdk_path" "$jdk_path"`
     so `plugin_root/client` → `installPath/client`
  4. Change `try_acquire_runtime` call to use `install_jdk_path` as the download target (not `jdk_path`), so the
     runtime is downloaded to the cache path
  5. Remove the old symlink bridge block (lines 383–398) since the symlink is now created upfront
  6. If `install_path` equals `plugin_root` or is empty, fall back to current behavior (download to `jdk_path` directly)
- Update `tests/hooks/session-start.bats` to test the new ordering
- Run tests: `bats tests/hooks/session-start.bats`
- Commit type: `bugfix:`
- Update index.json: status to "closed"

## Post-conditions

- [ ] Bug fixed: symlink `plugin_root/client` → `installPath/client` is created before any download attempt
- [ ] Existing runtime at `installPath/client` is reused without re-downloading
- [ ] Download targets `installPath/client` (cache), not `plugin_root/client` (marketplaces)
- [ ] `${CLAUDE_PLUGIN_ROOT}/client/bin/statusline-command` resolves through symlink to the working jlink binary
- [ ] All existing session-start.bats tests pass
- [ ] New tests cover symlink-first behavior
