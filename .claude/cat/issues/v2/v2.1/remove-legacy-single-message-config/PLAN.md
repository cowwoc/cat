# Plan: remove-legacy-single-message-config

## Goal

Remove the legacy single-message string config format from EmpiricalTestRunner. The multi-message format (with
`"messages"` array) supports single-message tests, making the string format redundant.

## Satisfies

None (code cleanup)

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java`
  - Remove `buildInput(List<PrimingMessage>, String)` 2-param overload
  - Remove `buildInput(List<PrimingMessage>, String, List<String>)` 3-param overload
  - Remove `runConfig()` method (legacy single-message path)
  - Remove `runTrial()` method (used by runConfig)
  - In `runTests()`: remove `instanceof String` branch, only accept Map with `messages` key
  - Remove top-level `success_criteria` parsing (each message has its own)
  - Update help text to remove string config format
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerTest.java`
  - Update tests that use `buildInput(priming, "prompt")` to use multi-message format
  - Remove/update tests for the removed overloads

## Acceptance Criteria

- [ ] Only multi-message config format accepted (Map with `messages` key)
- [ ] Single-message tests work via 1-element `messages` array
- [ ] All existing tests updated and passing
- [ ] Help text documents only the multi-message format

## Execution Steps

1. Remove legacy `buildInput` overloads and `runConfig`/`runTrial` methods
2. Update `runTests()` to only handle Map config values
3. Update all tests to use multi-message format
4. Update CLI help text
5. Run tests
