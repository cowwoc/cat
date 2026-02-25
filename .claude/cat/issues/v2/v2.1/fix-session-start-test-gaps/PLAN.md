# Plan: fix-session-start-test-gaps

## Goal
Add missing test coverage for session-start.sh identified during stakeholder review of
2.1-session-start-version-check: main() integration paths, download error handling, and failure output formatting.

## Satisfies
None - test coverage improvement from stakeholder review

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None significant; tests are additive
- **Mitigation:** N/A

## Files to Modify
- `tests/hooks/session-start.bats` - Add missing test cases

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Add main() integration tests**
   - Files: `tests/hooks/session-start.bats`
   - Test: main() success path with matching VERSION and working java binary (mock Java dispatcher)
   - Test: main() resolves plugin_root from script location when CLAUDE_PLUGIN_ROOT is unset
   - Test: main() failure output includes platform info and formatted debug trail

2. **Add download_runtime error path tests**
   - Files: `tests/hooks/session-start.bats`
   - Test: mktemp failure returns 1 and cleans up
   - Test: SHA256 download failure (second curl) returns 1 and cleans up temp archive
   - Test: tar extraction failure returns 1 and cleans up both temp files

3. **Add VERSION file write test**
   - Files: `tests/hooks/session-start.bats`
   - Test: after successful download, VERSION file is written with correct content

## Post-conditions
- [ ] main() success path (with mocked Java dispatcher) is tested end-to-end
- [ ] Each download_runtime error branch (mktemp, sha256 curl, tar) has a dedicated test
- [ ] VERSION file creation after download is verified
- [ ] All new tests pass
