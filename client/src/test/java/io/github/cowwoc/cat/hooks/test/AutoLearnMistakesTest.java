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
 * Tests for {@link AutoLearnMistakes} Pattern 11 (critical self-acknowledgment detection).
 * <p>
 * Verifies that severity-table vocabulary does not trigger false positives, while genuine
 * first-person critical mistake acknowledgments are correctly detected.
 */
public final class AutoLearnMistakesTest
{
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final String SESSION_ID = "test-session-autolearn";

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
}
