# Task Plan: update-check-startup

## Objective
Add a CAT update check on startup with "update available" notice when new version exists.

## Tasks
- [ ] Implement version check against GitHub API (releases endpoint)
- [ ] Compare current version with latest available
- [ ] Display update notice if newer version available
- [ ] Make check non-blocking (async, don't delay startup)
- [ ] Cache check result to avoid repeated network calls

## Technical Approach
Use GitHub API to fetch latest release from `cowwoc/cat` repository. The plugin is installed via GitHub, not npm, so the version check must use the GitHub releases API:

```
GET https://api.github.com/repos/cowwoc/cat/releases/latest
```

This returns JSON with a `tag_name` field containing the version (e.g., "v2.1.0" or "2.1.0").

Cache result for 24 hours to avoid excessive network requests and respect GitHub API rate limits.

## Verification
- [ ] Update notice displays when newer version exists
- [ ] No notice when current version is latest
- [ ] Check doesn't block/slow startup
- [ ] Works offline (graceful failure)
