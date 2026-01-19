# Task Plan: update-check-startup

## Objective
Add a CAT update check on startup with "update available" notice when new version exists.

## Tasks
- [ ] Implement version check against remote source (npm registry, GitHub releases, etc.)
- [ ] Compare current version with latest available
- [ ] Display update notice if newer version available
- [ ] Make check non-blocking (async, don't delay startup)
- [ ] Cache check result to avoid repeated network calls

## Technical Approach
Async check on startup, compare semver versions, display notice if update available. Cache result for 24 hours to avoid excessive network requests.

## Verification
- [ ] Update notice displays when newer version exists
- [ ] No notice when current version is latest
- [ ] Check doesn't block/slow startup
- [ ] Works offline (graceful failure)
