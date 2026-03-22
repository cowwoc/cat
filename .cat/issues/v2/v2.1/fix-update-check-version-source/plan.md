# Plan

## Goal

Change `CheckUpdateAvailable` to determine the latest CAT version by fetching `plugin/.claude-plugin/plugin.json` from the `main` branch on GitHub, instead of using the GitHub Releases API. Also update `getPluginVersion` to read the current version from the local `plugin/.claude-plugin/plugin.json` instead of `client/VERSION`. This eliminates false positives from non-CAT releases like `git-filter-repo-v2.38.0`.

## Pre-conditions

(none)

## Post-conditions

- [ ] `CheckUpdateAvailable` fetches the latest version from `https://raw.githubusercontent.com/cowwoc/cat/main/plugin/.claude-plugin/plugin.json` instead of the GitHub Releases API
- [ ] `getPluginVersion` reads from the local `plugin/.claude-plugin/plugin.json` instead of `client/VERSION`
- [ ] Non-CAT releases (e.g., `git-filter-repo-v2.38.0`) no longer trigger false update notifications
- [ ] Regression test added verifying the new version source behavior
- [ ] All tests passing, no regressions
- [ ] E2E: start a session and confirm the update check correctly detects when a newer version is available via plugin.json
