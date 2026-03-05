/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.tool.post.AutoLearnMistakes;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Tests for {@link AutoLearnMistakes} Pattern 2 (test_failure detection) and
 * Pattern 11 (critical_self_acknowledgment detection).
 * <p>
 * Verifies that severity-table vocabulary and non-test FAIL prefixes do not trigger false positives,
 * while genuine test failure output and first-person critical mistake acknowledgments are correctly detected.
 */
public final class AutoLearnMistakesTest
{
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final String SESSION_ID = "00000000-0000-0000-0000-000000000000";

  /**
   * Builds a tool result JSON node with the given stdout content.
   *
   * @param stdout the stdout content to place in the tool result
   * @return a JsonNode representing the tool result
   * @throws IOException if JSON parsing fails
   */
  private static JsonNode toolResult(String stdout) throws IOException
  {
    String json = """
      {"stdout": %s, "stderr": "", "exit_code": 0}
      """.formatted(MAPPER.writeValueAsString(stdout));
    return MAPPER.readTree(json);
  }

  /**
   * Builds a hook data JSON node for the given session ID.
   *
   * @param sessionId the session ID
   * @return a JsonNode representing the hook data
   * @throws IOException if JSON parsing fails
   */
  private static JsonNode hookData(String sessionId) throws IOException
  {
    return MAPPER.readTree("""
      {"tool_input": {}, "tool_result": {}, "session_id": "%s"}
      """.formatted(sessionId));
  }

  /**
   * Verifies that "FAIL: progress-banner launcher failed" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void failPrefixProgressBannerIsNotTestFailure() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("FAIL: progress-banner launcher failed");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      doesNotContain("test_failure");
  }

  /**
   * Verifies that "FAIL: some phase error" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void failPrefixPhaseErrorIsNotTestFailure() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("FAIL: some phase error");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      doesNotContain("test_failure");
  }

  /**
   * Verifies that Maven Surefire output "Tests run: 5, Failures: 2" triggers Pattern 2 (test_failure).
   */
  @Test
  public void mavenSurefireTestFailureIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("Tests run: 5, Failures: 2, Errors: 0, Skipped: 0");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("test_failure");
  }

  /**
   * Verifies that "3 tests failed" triggers Pattern 2 (test_failure).
   */
  @Test
  public void multipleTestsFailedIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("3 tests failed");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("test_failure");
  }

  /**
   * Verifies that "FAIL: some documentation error message" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void failPrefixDocumentationErrorIsNotTestFailure() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("FAIL: some documentation error message");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      doesNotContain("test_failure");
  }

  /**
   * Verifies that "MyTest.testMethod ... FAILED" triggers Pattern 2 (test_failure).
   */
  @Test
  public void testMethodFailedIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("MyTest.testMethod ... FAILED");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("test_failure");
  }

  /**
   * Verifies that "5 failures" triggers Pattern 2 (test_failure).
   */
  @Test
  public void failuresCountIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("5 failures");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("test_failure");
  }

  /**
   * Verifies that "1 test failed" triggers Pattern 2 (test_failure).
   */
  @Test
  public void singleTestFailedIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("1 test failed");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("test_failure");
  }

  /**
   * Verifies that "Tests run: 5, Failures: 0" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void zeroFailuresDoesNotTriggerTestFailure() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("Tests run: 5, Failures: 0, Errors: 0, Skipped: 0");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      doesNotContain("test_failure");
  }

  /**
   * Verifies that uppercase Maven Surefire output triggers Pattern 2 (test_failure).
   */
  @Test
  public void uppercaseMavenSurefireIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("TESTS RUN: 5, FAILURES: 2, ERRORS: 0, SKIPPED: 0");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("test_failure");
  }

  /**
   * Verifies that a test method failure with leading whitespace triggers Pattern 2 (test_failure).
   */
  @Test
  public void indentedTestMethodFailedIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("  MyTest.testMethod ... FAILED");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("test_failure");
  }

  /**
   * Verifies that severity-table content "Must fix critical issues" does not trigger Pattern 11.
   */
  @Test
  public void severityTableMustFixCriticalIssuesIsNotTriggered() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("Must fix critical issues");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      doesNotContain("critical_self_acknowledgment");
  }

  /**
   * Verifies that severity-table content "critical error in severity table" does not trigger Pattern 11.
   */
  @Test
  public void severityTableCriticalErrorDescriptionIsNotTriggered() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("critical error in severity table");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      doesNotContain("critical_self_acknowledgment");
  }

  /**
   * Verifies that severity-table content "CRITICAL | Blocks release" does not trigger Pattern 11.
   */
  @Test
  public void severityTableCriticalBlocksReleaseIsNotTriggered() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("CRITICAL | Blocks release");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      doesNotContain("critical_self_acknowledgment");
  }

  /**
   * Verifies that "I made a critical error" triggers Pattern 11.
   */
  @Test
  public void firstPersonCriticalErrorIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("I made a critical error");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("critical_self_acknowledgment");
  }

  /**
   * Verifies that "I caused a critical mistake" triggers Pattern 11.
   */
  @Test
  public void firstPersonCriticalMistakeIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("I caused a critical mistake");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("critical_self_acknowledgment");
  }

  /**
   * Verifies that "this was a critical failure" triggers Pattern 11.
   */
  @Test
  public void thisWasACriticalFailureIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("this was a critical failure");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("critical_self_acknowledgment");
  }

  /**
   * Verifies that "catastrophic mistake" triggers Pattern 11.
   */
  @Test
  public void catastrophicMistakeIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("catastrophic mistake");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("critical_self_acknowledgment");
  }

  /**
   * Verifies that "devastating bug" triggers Pattern 11.
   */
  @Test
  public void devastatingBugIsDetected() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("devastating bug");
    JsonNode hook = hookData(SESSION_ID);

    PostToolHandler.Result detection = handler.check("Bash", result, SESSION_ID, hook);

    requireThat(detection.additionalContext(), "additionalContext").
      contains("critical_self_acknowledgment");
  }

  /**
   * Verifies that an invalid sessionId (non-UUID) triggers an IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid sessionId format.*")
  public void invalidSessionIdThrowsException() throws IOException
  {
    AutoLearnMistakes handler = new AutoLearnMistakes();
    JsonNode result = toolResult("some output");
    JsonNode hook = hookData("not-a-valid-uuid");

    handler.check("Bash", result, "not-a-valid-uuid", hook);
  }
}
