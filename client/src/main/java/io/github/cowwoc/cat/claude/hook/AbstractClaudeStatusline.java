/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Abstract base class for scopes that provide statusline rendering capabilities.
 * <p>
 * Extends {@link AbstractJvmScope} with the {@link ClaudeStatusline} contract, giving subclasses
 * access to the JSON mapper and CAT work path required to render the Claude Code statusline.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudeStatusline extends AbstractJvmScope implements ClaudeStatusline
{
  private String modelDisplayName = "unknown";
  private String sessionId = "unknown";
  private Duration totalDuration = Duration.ZERO;
  private int usedTokens;
  private int totalContext;

  /**
   * Creates a new abstract Claude statusline scope with default field values.
   * <p>
   * Subclasses that do not read statusline data from an input stream may use this constructor.
   *
   * @param projectPath the project's root directory
   * @throws NullPointerException if {@code projectPath} is null
   */
  protected AbstractClaudeStatusline(Path projectPath)
  {
    super(projectPath);
  }

  /**
   * Creates a new abstract Claude statusline scope, reading and parsing statusline JSON from the
   * given input stream.
   * <p>
   * Reads all bytes from {@code stdin}, converts them to a UTF-8 string, and calls
   * {@link #parseStatuslineJson(String)} to populate the statusline fields.
   *
   * @param projectPath the project's root directory
   * @param stdin the input stream providing Claude Code hook JSON
   * @throws NullPointerException if any parameter is null
   * @throws IOException if an I/O error occurs while reading the stream
   */
  @SuppressWarnings({"this-escape", "PMD.ConstructorCallsOverridableMethod"})
  protected AbstractClaudeStatusline(Path projectPath, InputStream stdin) throws IOException
  {
    super(projectPath);
    requireThat(stdin, "stdin").isNotNull();
    String json = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
    parseStatuslineJson(json);
  }

  /**
   * Parses Claude Code hook JSON input and stores the extracted data internally.
   * <p>
   * Required fields ({@code model.display_name}, {@code session_id}, {@code cost.total_duration_ms},
   * {@code context_window.context_window_size} when {@code context_window} is present, and
   * {@code context_window.current_usage.input_tokens} when {@code current_usage} is present) throw
   * {@link IllegalArgumentException} when absent or invalid.
   * <p>
   * On JSON parse failure (malformed JSON), all fields fall back to defaults:
   * {@code "unknown"} for string fields and {@code 0} for numeric fields.
   *
   * @param jsonInput the JSON string to parse (from Claude Code's hook stdin)
   * @throws NullPointerException     if {@code jsonInput} is null
   * @throws IllegalArgumentException if a required field is missing or invalid
   */
  protected void parseStatuslineJson(String jsonInput)
  {
    requireThat(jsonInput, "jsonInput").isNotNull();

    String parsedModelDisplayName = "unknown";
    String parsedSessionId = "unknown";
    Duration parsedTotalDuration = Duration.ZERO;
    int parsedUsedTokens = 0;
    int parsedTotalContext = 0;

    try
    {
      JsonNode root = getJsonMapper().readTree(jsonInput);

      JsonNode modelNode = root.get("model");
      if (modelNode == null || modelNode.isNull())
        throw new IllegalArgumentException("model is missing in statusline JSON");
      JsonNode displayNameNode = modelNode.get("display_name");
      if (displayNameNode == null || displayNameNode.isNull())
        throw new IllegalArgumentException("model.display_name is missing in statusline JSON");
      parsedModelDisplayName = displayNameNode.asString();

      JsonNode sessionIdNode = root.get("session_id");
      if (sessionIdNode == null || sessionIdNode.isNull())
        throw new IllegalArgumentException("session_id is missing in statusline JSON");
      parsedSessionId = sessionIdNode.asString();

      JsonNode costNode = root.get("cost");
      if (costNode == null || costNode.isNull())
        throw new IllegalArgumentException("cost is missing in statusline JSON");
      JsonNode durationNode = costNode.get("total_duration_ms");
      if (durationNode == null || durationNode.isNull() || !durationNode.canConvertToLong())
        throw new IllegalArgumentException("cost.total_duration_ms is missing in statusline JSON");
      parsedTotalDuration = Duration.ofMillis(durationNode.longValue());

      JsonNode contextNode = root.get("context_window");
      if (contextNode != null && !contextNode.isNull())
      {
        JsonNode contextSizeNode = contextNode.get("context_window_size");
        int contextSizeValue;
        if (contextSizeNode != null && !contextSizeNode.isNull() && contextSizeNode.canConvertToInt())
          contextSizeValue = contextSizeNode.intValue();
        else
          contextSizeValue = 0;
        if (contextSizeValue <= 0)
        {
          String contextSizeStr;
          if (contextSizeNode == null)
            contextSizeStr = "null";
          else
            contextSizeStr = contextSizeNode.toString();
          throw new IllegalArgumentException(
            "context_window.context_window_size is missing or non-positive in statusline JSON. Value: " +
              contextSizeStr);
        }
        parsedTotalContext = contextSizeValue;

        JsonNode currentUsageNode = contextNode.get("current_usage");
        if (currentUsageNode != null && !currentUsageNode.isNull())
        {
          JsonNode inputTokensNode = currentUsageNode.get("input_tokens");
          if (inputTokensNode == null || inputTokensNode.isNull() || !inputTokensNode.canConvertToInt())
          {
            throw new IllegalArgumentException(
              "context_window.current_usage.input_tokens is missing in statusline JSON");
          }
          int inputTokensValue = inputTokensNode.intValue();
          if (inputTokensValue < 0)
          {
            throw new IllegalArgumentException(
              "context_window.current_usage.input_tokens is negative in statusline JSON. Value: " +
                inputTokensValue);
          }
          // Prompt caching splits the total input token count across three fields. When caching is
          // active, input_tokens contains only the non-cached portion (often near zero), so summing
          // all three fields is required to compute the true context usage.
          JsonNode cacheReadNode = currentUsageNode.get("cache_read_input_tokens");
          int cacheReadValue = 0;
          if (cacheReadNode != null && !cacheReadNode.isNull() && cacheReadNode.canConvertToInt())
            cacheReadValue = cacheReadNode.intValue();
          JsonNode cacheCreationNode = currentUsageNode.get("cache_creation_input_tokens");
          int cacheCreationValue = 0;
          if (cacheCreationNode != null && !cacheCreationNode.isNull() && cacheCreationNode.canConvertToInt())
            cacheCreationValue = cacheCreationNode.intValue();
          parsedUsedTokens = inputTokensValue + cacheReadValue + cacheCreationValue;
        }
      }
    }
    catch (JacksonException _)
    {
      // Use defaults on parse failure (graceful degradation)
    }

    // Clamp display-only values to valid ranges
    if (parsedTotalDuration.isNegative())
      parsedTotalDuration = Duration.ZERO;

    this.modelDisplayName = parsedModelDisplayName;
    this.sessionId = parsedSessionId;
    this.totalDuration = parsedTotalDuration;
    this.usedTokens = parsedUsedTokens;
    this.totalContext = parsedTotalContext;
  }

  /**
   * Returns the model display name parsed from the statusline JSON.
   *
   * @return the model display name, or {@code "unknown"} if not yet parsed or absent
   */
  @Override
  public String getModelDisplayName()
  {
    return modelDisplayName;
  }

  /**
   * Returns the session ID parsed from the statusline JSON.
   *
   * @return the session ID, or {@code "unknown"} if not yet parsed or absent
   */
  @Override
  public String getSessionId()
  {
    return sessionId;
  }

  /**
   * Returns the total session duration parsed from the statusline JSON.
   *
   * @return the total duration, or {@link Duration#ZERO} if not yet parsed or absent
   */
  @Override
  public Duration getTotalDuration()
  {
    return totalDuration;
  }

  /**
   * Returns the number of tokens used in the context window, as parsed from the statusline JSON.
   *
   * @return the number of used tokens, or {@code 0} if not present in the input
   */
  @Override
  public int getUsedTokens()
  {
    return usedTokens;
  }

  /**
   * Returns the total context window size in tokens, as parsed from the statusline JSON.
   *
   * @return the total context size in tokens, or {@code 0} if not present in the input
   */
  @Override
  public int getTotalContext()
  {
    return totalContext;
  }
}
