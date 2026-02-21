# Plan: port-reset-failure-counter

## Goal

Port `reset-failure-counter.sh` into Java by adding a `ResetFailureCounter` handler to the existing
`PostToolUseHook` handler pipeline. Then delete the shell script and remove its hooks.json registration.

## Satisfies

None (migration/cleanup)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Must ensure the reset runs on success only (PostToolUse, not PostToolUseFailure)
- **Mitigation:** `PostToolUseHook` already dispatches only on success; the new handler inherits this behavior

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/failure/ResetFailureCounter.java` - New handler: delete
  the tracking file for the session on successful tool use
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ResetFailureCounterTest.java` - Tests for the new handler
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java` - Register `ResetFailureCounter` in
  the handler list
- `plugin/hooks/hooks.json` - Remove the `reset-failure-counter.sh` entry from PostToolUse
- `plugin/hooks/reset-failure-counter.sh` - Delete

## Acceptance Criteria

- [ ] `ResetFailureCounter` implements `PostToolHandler` and deletes the tracking file on success
- [ ] Handler registered in `PostToolUseHook` handler pipeline
- [ ] Tests verify: file deleted on success, no error if file doesn't exist, no error if delete fails
- [ ] `reset-failure-counter.sh` deleted and removed from hooks.json
- [ ] E2E: `grep -r 'reset-failure-counter' plugin/` returns no results
- [ ] All tests pass (`mvn -f client/pom.xml test`)

## Execution Steps

1. **Create ResetFailureCounter handler:** Implement `PostToolHandler` in the `failure` package. On `check()`,
   delete the tracking file (`cat-failure-tracking-<sessionId>.count`) and return `Result.allow()`. Use same
   `trackingDirectory` pattern as `DetectRepeatedFailures`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/failure/ResetFailureCounter.java`
2. **Write tests:** Verify file deletion, missing file tolerance, and IO error graceful handling.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/ResetFailureCounterTest.java`
3. **Register handler in PostToolUseHook:** Add `ResetFailureCounter` to the handler list.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java`
4. **Remove shell script:** Delete `reset-failure-counter.sh` and its hooks.json entry.
   - Files: `plugin/hooks/reset-failure-counter.sh`, `plugin/hooks/hooks.json`
5. **Run tests:** `mvn -f client/pom.xml test`
