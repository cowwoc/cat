# Plan: port-skill-preprocessor-failure

## Goal

Port `skill-preprocessor-failure.sh` into Java by adding a `DetectPreprocessorFailure` handler to the existing
`PostToolUseFailureHook` handler pipeline. Then delete the shell script, its test, and remove its hooks.json
registration.

## Satisfies

None (migration/cleanup)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The shell script uses `jq` which is not in the runtime tool set — it may already be silently failing
  in environments without jq
- **Mitigation:** The Java port uses Jackson (already available in the runtime) and is more reliable

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/failure/DetectPreprocessorFailure.java` - New handler: check if
  the error contains `Bash command failed for pattern "!` and return additionalContext suggesting `/cat:feedback`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectPreprocessorFailureTest.java` - Tests for the new
  handler
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseFailureHook.java` - Register
  `DetectPreprocessorFailure` in the handler list
- `plugin/hooks/hooks.json` - Remove the `skill-preprocessor-failure.sh` entry from PostToolUseFailure
- `plugin/hooks/skill-preprocessor-failure.sh` - Delete
- `plugin/hooks/test/test-skill-preprocessor-failure.sh` - Delete

## Post-conditions

- [ ] `DetectPreprocessorFailure` implements `PostToolHandler` and detects preprocessor failures in the error field
- [ ] Handler returns additionalContext suggesting `/cat:feedback` when pattern matches
- [ ] Handler returns `Result.allow()` when pattern does not match
- [ ] Handler registered in `PostToolUseFailureHook` handler pipeline
- [ ] Tests verify: match detected, non-match ignored, missing error field handled
- [ ] `skill-preprocessor-failure.sh` and its test deleted, removed from hooks.json
- [ ] E2E: `grep -r 'skill-preprocessor-failure' plugin/hooks/` returns no results (except README if applicable)
- [ ] All tests pass (`mvn -f client/pom.xml test`)

## Execution Steps

1. **Create DetectPreprocessorFailure handler:** Implement `PostToolHandler` in the `failure` package. In `check()`,
   extract the `error` field from hookData. If it contains `Bash command failed for pattern "!`, return
   `Result.context(...)` with the feedback suggestion message. Otherwise return `Result.allow()`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/failure/DetectPreprocessorFailure.java`
2. **Write tests:** Verify pattern matching, non-matching errors, and missing error field.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectPreprocessorFailureTest.java`
3. **Register handler in PostToolUseFailureHook:** Add `DetectPreprocessorFailure` to the handler list.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseFailureHook.java`
4. **Remove shell script and test:** Delete both files and the hooks.json entry.
   - Files: `plugin/hooks/skill-preprocessor-failure.sh`, `plugin/hooks/test/test-skill-preprocessor-failure.sh`,
     `plugin/hooks/hooks.json`
5. **Run tests:** `mvn -f client/pom.xml test`
