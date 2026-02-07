# Plan: session-start-version-check

## Goal
Update session_start.sh to compare the local jlink bundle version against plugin.json version. If they do not match
(upgrade or downgrade), use the GitHub API to find and download the correct bundle for the current plugin version.

## Satisfies
None - infrastructure subtask of add-java-build-to-ci

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** GitHub API rate limits; network latency on version check; offline scenarios
- **Mitigation:** Only check when versions mismatch (fast local comparison first); fail gracefully if API unavailable

## Files to Modify
- `plugin/hooks/session_start.sh` - Rewrite download logic to use version-based comparison and GitHub API
- `plugin/.claude-plugin/plugin.json` - Read version from here (already exists)

## Acceptance Criteria
- [ ] session_start.sh reads plugin.json version on startup
- [ ] session_start.sh reads local bundle VERSION marker file
- [ ] If versions match: skip download (fast path, no network call)
- [ ] If versions mismatch: call GitHub API to find release for plugin.json version
- [ ] Download correct platform-specific bundle from GitHub release
- [ ] After download, verify bundle is functional (java -version check)
- [ ] Handle API failures gracefully (warn but do not block session)
- [ ] Works for both upgrades and downgrades

## Execution Steps
1. **Add version reading logic to session_start.sh**
   - Files: `plugin/hooks/session_start.sh`
   - Read plugin version: `jq -r .version "${CLAUDE_PLUGIN_ROOT}/.claude-plugin/plugin.json"`
   - Read local bundle version: `cat "${jdk_path}/VERSION"` (created by jlink-config.sh in subtask 1)
   - Compare: if equal, export CAT_JAVA_HOME and return success immediately

2. **Add GitHub API download logic**
   - Files: `plugin/hooks/session_start.sh`
   - If versions mismatch, call: `curl -sSf "https://api.github.com/repos/{owner}/{repo}/releases/tags/v${plugin_version}"`
   - Parse response to find platform-specific asset URL (e.g., `cat-jdk-25-linux-x64.tar.gz`)
   - Download and extract the bundle to the expected location
   - Read owner/repo from plugin.json `repository` field

3. **Remove old download/build fallback logic**
   - Files: `plugin/hooks/session_start.sh`
   - Remove the `download_runtime()` function that uses hardcoded DOWNLOAD_BASE_URL
   - Remove the `build_runtime_locally()` fallback (developers handle their own builds)
   - Keep `check_existing_runtime()` for verifying the bundle works after download

4. **Run tests**
   - `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] Version match skips all network calls
- [ ] Version mismatch triggers GitHub API call and download
- [ ] Downloaded bundle passes functionality check
- [ ] API failures produce clear warning without blocking session
