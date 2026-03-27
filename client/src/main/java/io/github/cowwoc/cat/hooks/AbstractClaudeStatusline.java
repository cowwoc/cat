/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Abstract base class for scopes that provide statusline rendering capabilities.
 * <p>
 * Extends {@link AbstractClaudeScope} with the {@link ClaudeStatusline} contract, giving subclasses
 * access to the JSON mapper and CAT work path required to render the Claude Code statusline.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudeStatusline extends AbstractClaudeScope implements ClaudeStatusline
{
  private String modelDisplayName = "unknown";
  private String sessionId = "unknown";
  private Duration totalDuration = Duration.ZERO;
  private int usedPercentage;

  /**
   * Creates a new abstract Claude statusline scope with default field values.
   * <p>
   * Subclasses that do not read statusline data from an input stream may use this constructor.
   *
   * @param projectPath the project's root directory
   * @param pluginRoot the Claude plugin root directory
   * @param claudeConfigPath the Claude config directory
   * @throws NullPointerException if any parameter is null
   */
  protected AbstractClaudeStatusline(Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    super(projectPath, pluginRoot, claudeConfigPath);
  }

  /**
   * Creates a new abstract Claude statusline scope, reading and parsing statusline JSON from the
   * given input stream.
   * <p>
   * Reads all bytes from {@code stdin}, converts them to a UTF-8 string, and calls
   * {@link #parseStatuslineJson(String)} to populate the statusline fields.
   *
   * @param projectPath the project's root directory
   * @param pluginRoot the Claude plugin root directory
   * @param claudeConfigPath the Claude config directory
   * @param stdin the input stream providing Claude Code hook JSON
   * @throws NullPointerException if any parameter is null
   * @throws IOException if an I/O error occurs while reading the stream
   */
  @SuppressWarnings({"this-escape", "PMD.ConstructorCallsOverridableMethod"})
  protected AbstractClaudeStatusline(Path projectPath, Path pluginRoot, Path claudeConfigPath,
    InputStream stdin) throws IOException
  {
    super(projectPath, pluginRoot, claudeConfigPath);
    requireThat(stdin, "stdin").isNotNull();
    String json = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
    parseStatuslineJson(json);
  }

  /**
   * Parses Claude Code hook JSON input and stores the extracted data internally.
   * <p>
   * On JSON parse failure or missing fields, default values are used for graceful degradation:
   * {@code "unknown"} for string fields and {@code 0} for numeric fields.
   *
   * @param jsonInput the JSON string to parse (from Claude Code's hook stdin)
   * @throws NullPointerException if {@code jsonInput} is null
   */
  protected void parseStatuslineJson(String jsonInput)
  {
    requireThat(jsonInput, "jsonInput").isNotNull();

    String parsedModelDisplayName = "unknown";
    String parsedSessionId = "unknown";
    Duration parsedTotalDuration = Duration.ZERO;
    int parsedUsedPercentage = 0;

    try
    {
      JsonNode root = getJsonMapper().readTree(jsonInput);

      JsonNode modelNode = root.get("model");
      if (modelNode != null && !modelNode.isNull())
      {
        JsonNode displayNameNode = modelNode.get("display_name");
        if (displayNameNode != null && !displayNameNode.isNull())
          parsedModelDisplayName = displayNameNode.asString();
      }

      JsonNode sessionIdNode = root.get("session_id");
      if (sessionIdNode != null && !sessionIdNode.isNull())
        parsedSessionId = sessionIdNode.asString();

      JsonNode costNode = root.get("cost");
      if (costNode != null && !costNode.isNull())
      {
        JsonNode durationNode = costNode.get("total_duration_ms");
        if (durationNode != null && !durationNode.isNull() && durationNode.canConvertToLong())
          parsedTotalDuration = Duration.ofMillis(durationNode.longValue());
      }

      JsonNode contextNode = root.get("context_window");
      if (contextNode != null && !contextNode.isNull())
      {
        JsonNode percentageNode = contextNode.get("used_percentage");
        if (percentageNode != null && !percentageNode.isNull() && percentageNode.canConvertToInt())
          parsedUsedPercentage = percentageNode.intValue();
      }
    }
    catch (JacksonException _)
    {
      // Use defaults on parse failure (graceful degradation)
    }

    // Clamp values to valid ranges
    if (parsedTotalDuration.isNegative())
      parsedTotalDuration = Duration.ZERO;
    if (parsedUsedPercentage < 0)
      parsedUsedPercentage = 0;
    if (parsedUsedPercentage > 100)
      parsedUsedPercentage = 100;

    this.modelDisplayName = parsedModelDisplayName;
    this.sessionId = parsedSessionId;
    this.totalDuration = parsedTotalDuration;
    this.usedPercentage = parsedUsedPercentage;
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
   * Returns the context window usage percentage parsed from the statusline JSON.
   *
   * @return the usage percentage (0–100), or {@code 0} if not yet parsed or absent
   */
  @Override
  public int getUsedPercentage()
  {
    return usedPercentage;
  }
}
