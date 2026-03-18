# Plan: add-giving-up-detection-phrases

## Goal

Add two new giving-up detection phrases to the `detectConstraintRationalization()` method in `DetectGivingUp.java`,
and add corresponding tests in `DetectGivingUpTest.java` to verify each phrase is detected.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** New phrases could overlap with existing composable keyword detection, causing duplicate triggering
  or test fragility.
- **Mitigation:** Both new phrases are full-string literals added to the direct-match list (not the composable path),
  so they don't interact with `hasConstraintKeyword()` / `hasAbandonmentAction()`. Existing test coverage remains
  unaffected.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java` — add two phrases to
  `detectConstraintRationalization()` in the direct-match return block (the `return textLower.contains(...)` chain)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectGivingUpTest.java` — add two new `@Test` methods,
  one per phrase

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `DetectGivingUp.java`, add the following two entries to the `return` statement inside
  `detectConstraintRationalization()` (after the existing last entry
  `(textLower.contains("given the") && textLower.contains("complexity and") && textLower.contains("token budget"))`):
  ```
  textLower.contains("due to token constraints and the need to complete this workflow, i'll summarize the remaining steps") ||
  textLower.contains("given the extensive work already completed")
  ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java`

- In `DetectGivingUpTest.java`, add two test methods after the existing `detectsMvpRationalization()` test:

  **Test 1 — phrase "due to token constraints...":**
  ```java
  /**
   * Verifies that the token-constraints workflow summary phrase is detected as constraint rationalization.
   */
  @Test
  public void detectsTokenConstraintsWorkflowSummaryPhrase()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Due to token constraints and the need to complete this workflow, " +
        "I'll summarize the remaining steps.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }
  ```

  **Test 2 — phrase "given the extensive work already completed":**
  ```java
  /**
   * Verifies that the extensive-work-completed rationalization phrase is detected.
   */
  @Test
  public void detectsExtensiveWorkCompletedPhrase()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Given the extensive work already completed, " +
        "I'll skip the remaining edge cases.";
    String result = handler.check(prompt, "test-session");
    requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
  }
  ```
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectGivingUpTest.java`

- Run all tests to confirm no regressions:
  ```bash
  mvn -f client/pom.xml test
  ```

- Update `STATE.md` to set Status: closed, Progress: 100%
  - Files: `STATE.md` in the issue directory

## Post-conditions

- [ ] `detectConstraintRationalization()` returns `true` for
  `"due to token constraints and the need to complete this workflow, i'll summarize the remaining steps"`
- [ ] `detectConstraintRationalization()` returns `true` for `"given the extensive work already completed"`
- [ ] `DetectGivingUpTest.detectsTokenConstraintsWorkflowSummaryPhrase()` passes
- [ ] `DetectGivingUpTest.detectsExtensiveWorkCompletedPhrase()` passes
- [ ] All pre-existing `DetectGivingUpTest` tests still pass
- [ ] E2E: `mvn -f client/pom.xml test` exits 0 and `DetectGivingUpTest` reports all tests passed
