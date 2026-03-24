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
 * Tests for {@link AutoLearnMistakes} detection patterns.
 * <p>
 * Verifies that Bash commands with exit_code 0 never trigger failure patterns regardless of output
 * content, while genuine failures (exit_code != 0) from Bash commands are correctly detected.
 * Also verifies edit_failure, skill_step_failure, and assistant message patterns.
 */
public final class AutoLearnMistakesTest
{
  /**
   * Builds a tool result JSON node with the given stdout content and exit code 0.
   *
   * @param mapper the JSON mapper to use
   * @param stdout the stdout content to place in the tool result
   * @return a JsonNode representing the tool result
   * @throws IOException if JSON parsing fails
   */
  private static JsonNode toolResult(JsonMapper mapper, String stdout) throws IOException
  {
    return toolResult(mapper, stdout, 0);
  }

  /**
   * Builds a tool result JSON node with the given stdout content and exit code.
   *
   * @param mapper the JSON mapper to use
   * @param stdout the stdout content to place in the tool result
   * @param exitCode the exit code to set in the tool result
   * @return a JsonNode representing the tool result
   * @throws IOException if JSON parsing fails
   */
  private static JsonNode toolResult(JsonMapper mapper, String stdout, int exitCode) throws IOException
  {
    String json = """
      {"stdout": %s, "stderr": "", "exit_code": %d}
      """.formatted(mapper.writeValueAsString(stdout), exitCode);
    return mapper.readTree(json);
  }

  /**
   * Builds a hook data JSON node for the given session ID.
   *
   * @param mapper    the JSON mapper to use
   * @param sessionId the session ID
   * @return a JsonNode representing the hook data
   * @throws IOException if JSON parsing fails
   */
  private static JsonNode hookData(JsonMapper mapper, String sessionId) throws IOException
  {
    return mapper.readTree("""
      {"tool_input": {}, "tool_result": {}, "session_id": "%s"}
      """.formatted(sessionId));
  }

  /**
   * Verifies that Bash with exit_code 0 does NOT trigger Pattern 1 (build_failure) even when
   * stdout contains "BUILD FAILURE".
   * <p>
   * A successful Bash command cannot represent a real build failure — the keyword must be from
   * displayed content such as source files or documentation.
   */
  @Test
  public void bashExitCodeZeroDoesNotTriggerBuildFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "BUILD FAILURE", 0);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("build_failure");
    }
  }

  /**
   * Verifies that Bash with exit_code 0 does NOT trigger Pattern 2 (test_failure) even when
   * stdout contains "Tests run: 1, Failures: 1".
   * <p>
   * A successful Bash command cannot represent a real test failure — the keyword must be from
   * displayed content such as source files or documentation.
   */
  @Test
  public void bashExitCodeZeroDoesNotTriggerTestFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "Tests run: 1, Failures: 1, Errors: 0, Skipped: 0", 0);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("test_failure");
    }
  }

  /**
   * Verifies that Bash with exit_code != 0 DOES trigger Pattern 1 (build_failure) when
   * stdout contains "BUILD FAILURE".
   */
  @Test
  public void bashExitCodeNonZeroTriggersBuildFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "BUILD FAILURE", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("build_failure");
    }
  }

  /**
   * Verifies that Bash with exit_code != 0 DOES trigger Pattern 2 (test_failure) when
   * stdout contains "Tests run: 1, Failures: 1".
   */
  @Test
  public void bashExitCodeNonZeroTriggersTestFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "Tests run: 1, Failures: 1, Errors: 0, Skipped: 0", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that "FAIL: progress-banner launcher failed" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void failPrefixProgressBannerIsNotTestFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "FAIL: progress-banner launcher failed");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("test_failure");
    }
  }

  /**
   * Verifies that "FAIL: some phase error" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void failPrefixPhaseErrorIsNotTestFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "FAIL: some phase error");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("test_failure");
    }
  }

  /**
   * Verifies that Maven Surefire output "Tests run: 5, Failures: 2" triggers Pattern 2 (test_failure)
   * when exit_code is non-zero.
   */
  @Test
  public void mavenSurefireTestFailureIsDetected() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "Tests run: 5, Failures: 2, Errors: 0, Skipped: 0", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that "3 tests failed" triggers Pattern 2 (test_failure) when exit_code is non-zero.
   */
  @Test
  public void multipleTestsFailedIsDetected() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "3 tests failed", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that "FAIL: some documentation error message" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void failPrefixDocumentationErrorIsNotTestFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "FAIL: some documentation error message");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("test_failure");
    }
  }

  /**
   * Verifies that "MyTest.testMethod ... FAILED" triggers Pattern 2 (test_failure)
   * when exit_code is non-zero.
   */
  @Test
  public void testMethodFailedIsDetected() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "MyTest.testMethod ... FAILED", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that "5 failures" triggers Pattern 2 (test_failure) when exit_code is non-zero.
   */
  @Test
  public void failuresCountIsDetected() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "5 failures", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that "1 test failed" triggers Pattern 2 (test_failure) when exit_code is non-zero.
   */
  @Test
  public void singleTestFailedIsDetected() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "1 test failed", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that "Tests run: 5, Failures: 0" does not trigger Pattern 2 (test_failure).
   */
  @Test
  public void zeroFailuresDoesNotTriggerTestFailure() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "Tests run: 5, Failures: 0, Errors: 0, Skipped: 0");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("test_failure");
    }
  }

  /**
   * Verifies that uppercase Maven Surefire output triggers Pattern 2 (test_failure)
   * when exit_code is non-zero.
   */
  @Test
  public void uppercaseMavenSurefireIsDetected() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "TESTS RUN: 5, FAILURES: 2, ERRORS: 0, SKIPPED: 0", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that a test method failure with leading whitespace triggers Pattern 2 (test_failure)
   * when exit_code is non-zero.
   */
  @Test
  public void indentedTestMethodFailedIsDetected() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "  MyTest.testMethod ... FAILED", 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("test_failure");
    }
  }

  /**
   * Verifies that severity-table content "Must fix critical issues" does not trigger
   * critical_self_acknowledgment.
   * <p>
   * Pattern 11 only applies to assistant messages, not to tool output.
   */
  @Test
  public void severityTableMustFixCriticalIssuesIsNotTriggered() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "Must fix critical issues");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("critical_self_acknowledgment");
    }
  }

  /**
   * Verifies that severity-table content "critical error in severity table" does not trigger
   * critical_self_acknowledgment.
   * <p>
   * Pattern 11 only applies to assistant messages, not to tool output.
   */
  @Test
  public void severityTableCriticalErrorDescriptionIsNotTriggered() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "critical error in severity table");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("critical_self_acknowledgment");
    }
  }

  /**
   * Verifies that severity-table content "CRITICAL | Blocks release" does not trigger
   * critical_self_acknowledgment.
   * <p>
   * Pattern 11 only applies to assistant messages, not to tool output.
   */
  @Test
  public void severityTableCriticalBlocksReleaseIsNotTriggered() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "CRITICAL | Blocks release");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("critical_self_acknowledgment");
    }
  }

  /**
   * Verifies that "I made a critical error" in tool output does NOT trigger
   * critical_self_acknowledgment.
   * <p>
   * Pattern 11 only applies to assistant messages. Tool output containing this phrase is a false
   * positive from displayed content (e.g., grep output, documentation).
   */
  @Test
  public void firstPersonCriticalErrorInToolOutputIsNotTriggered() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "I made a critical error");
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        doesNotContain("critical_self_acknowledgment");
    }
  }

  /**
   * Verifies that an invalid sessionId (non-UUID) triggers an IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid sessionId format.*")
  public void invalidSessionIdThrowsException() throws IOException
  {
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      JsonNode result = toolResult(mapper, "some output");
      JsonNode hook = hookData(mapper, "not-a-valid-uuid");

      handler.check("Bash", result, "not-a-valid-uuid", hook);
    }
  }

  /**
   * Verifies that real Maven BUILD FAILURE output still triggers Pattern 1 (build_failure)
   * when exit_code is non-zero.
   * <p>
   * True positive preserved: genuine Maven build failures must continue to be detected.
   */
  @Test
  public void realMavenBuildFailureTriggersBuildFailurePattern() throws IOException
  {
    String sessionId = "00000000-0000-0000-0000-000000000000";
    try (TestClaudeHook scope = new TestClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      AutoLearnMistakes handler = new AutoLearnMistakes(scope);
      // Simulates actual Maven build failure output — real build failure with non-zero exit code.
      String mavenOutput = """
        [INFO] --- maven-compiler-plugin:3.11.0:compile (default-compile) @ cat-hooks ---
        [ERROR] /workspace/client/src/main/java/Foo.java:[10,5] ';' expected
        [INFO] BUILD FAILURE
        [INFO] ------------------------------------------------------------------------
        [ERROR] Failed to execute goal compile
        """;
      JsonNode result = toolResult(mapper, mavenOutput, 1);
      JsonNode hook = hookData(mapper, sessionId);

      PostToolHandler.Result detection = handler.check("Bash", result, sessionId, hook);

      requireThat(detection.additionalContext(), "additionalContext").
        contains("build_failure");
    }
  }
}
