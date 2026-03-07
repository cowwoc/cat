/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.jackson.DefaultJacksonValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.SkillLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utility class for reading and parsing hook input from stdin.
 */
public final class HookInput
{
  /**
   * Pattern that accepts alphanumeric characters, hyphens, and underscores.
   * <p>
   * Prevents path traversal characters ({@code /}, {@code ..}) while accepting UUIDs and any reasonable
   * session ID format.
   */
  private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  /**
   * Pattern that accepts alphanumeric characters, hyphens, and underscores.
   * <p>
   * Prevents path traversal characters ({@code /}, {@code ..}) while accepting any reasonable
   * agent ID format.
   */
  private static final Pattern AGENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  private final JsonMapper mapper;
  private final JsonNode data;
  private final String sessionId;
  private final String agentId;

  /**
   * Creates a new HookInput.
   *
   * @param mapper the JSON mapper
   * @param data the parsed JSON data
   */
  private HookInput(JsonMapper mapper, JsonNode data)
  {
    this.mapper = mapper;
    this.data = data;
    this.sessionId = validateSessionId(data);
    this.agentId = validateAgentId(data);
  }

  /**
   * Validates and returns the session ID from the JSON data.
   *
   * @param data the parsed JSON data
   * @return the session ID (never empty)
   * @throws IllegalArgumentException if the session_id field is missing, blank, or contains characters
   *   other than alphanumerics, hyphens, and underscores
   */
  private static String validateSessionId(JsonNode data)
  {
    String value = requireThat(data, "data").property("session_id").isString().getValue().asString();
    if (value.isBlank())
    {
      throw new IllegalArgumentException(
        "sessionId is empty. Hook infrastructure must provide CLAUDE_SESSION_ID.");
    }
    if (!SESSION_ID_PATTERN.matcher(value).matches())
    {
      throw new IllegalArgumentException("Invalid session_id format: '" + value +
        "'. Expected alphanumeric, hyphens, and underscores only.");
    }
    return value;
  }

  /**
   * Validates and returns the agent ID from the JSON data.
   *
   * @param data the parsed JSON data
   * @return the agent ID, or empty string if missing or empty
   * @throws IllegalArgumentException if the agent_id field is present but contains characters other than
   *   alphanumerics, hyphens, and underscores
   */
  private static String validateAgentId(JsonNode data)
  {
    JsonNode node = data.get("agent_id");
    if (node == null || !node.isString())
      return "";
    String value = node.asString();
    if (value == null || value.isBlank())
      return "";
    if (!AGENT_ID_PATTERN.matcher(value).matches())
    {
      throw new IllegalArgumentException("Invalid agent_id format: '" + value +
        "'. Expected alphanumeric, hyphens, and underscores only.");
    }
    return value;
  }

  /**
   * Read and parse JSON input from stdin.
   *
   * @param mapper the JSON mapper to use for parsing
   * @return parsed hook input
   * @throws NullPointerException if mapper is null
   * @throws IllegalStateException if stdin has no piped input, contains blank/malformed JSON, or is missing
   *   a session_id
   */
  public static HookInput readFromStdin(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    try
    {
      if (System.console() != null && System.in.available() == 0)
        throw new IllegalStateException("No piped input available on stdin.");
    }
    catch (IOException e)
    {
      throw new IllegalStateException("Failed to check stdin availability.", e);
    }
    return readFrom(mapper, System.in);
  }

  /**
   * Read and parse JSON input from a stream.
   *
   * @param mapper the JSON mapper to use for parsing
   * @param inputStream the stream to read from
   * @return parsed hook input
   * @throws NullPointerException if mapper or inputStream is null
   * @throws IllegalStateException if the stream contains blank/malformed JSON, or is missing a session_id
   */
  public static HookInput readFrom(JsonMapper mapper, InputStream inputStream)
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(inputStream, "inputStream").isNotNull();
    try
    {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      String raw = reader.lines().collect(Collectors.joining("\n"));

      if (raw.isBlank())
        throw new IllegalStateException("Hook input is blank.");

      JsonNode node = mapper.readTree(raw);
      return new HookInput(mapper, node);
    }
    catch (JacksonException e)
    {
      throw new IllegalStateException("Hook input contains malformed JSON.", e);
    }
  }

  /**
   * Create a HookInput for a bash command with explicit field values.
   * <p>
   * Constructs the JSON structure expected by bash handlers: {@code tool_name}, {@code tool_input} with
   * {@code command}, {@code cwd}, {@code session_id}, {@code agent_id}, and optionally
   * {@code tool_result}.
   *
   * @param mapper the JSON mapper to use
   * @param command the bash command string
   * @param workingDirectory the shell's current working directory, or empty string if unavailable
   * @param sessionId the session ID, or empty string if not available
   * @param agentId the native agent ID (not composite), or empty string if not available
   * @param toolResult the tool result node (for PostToolUse), or null if not applicable
   * @return a HookInput with the provided values
   * @throws NullPointerException if mapper, command, workingDirectory, or sessionId is null
   */
  public static HookInput forBash(JsonMapper mapper, String command, String workingDirectory,
    String sessionId, String agentId, JsonNode toolResult)
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();

    ObjectNode root = mapper.createObjectNode();
    root.put("tool_name", "Bash");

    ObjectNode toolInput = mapper.createObjectNode();
    toolInput.put("command", command);
    root.set("tool_input", toolInput);

    root.put("cwd", workingDirectory);

    if (!sessionId.isEmpty())
      root.put("session_id", sessionId);

    if (agentId != null && !agentId.isEmpty())
      root.put("agent_id", agentId);

    if (toolResult != null)
      root.set("tool_result", toolResult);

    return new HookInput(mapper, root);
  }

  /**
   * Get the bash command from the tool input.
   *
   * @return the command string, or empty string if not found
   */
  public String getCommand()
  {
    JsonNode toolInput = getToolInput();
    JsonNode commandNode = toolInput.get("command");
    if (commandNode == null || !commandNode.isString())
      return "";
    String value = commandNode.asString();
    if (value == null)
      return "";
    return value;
  }

  /**
   * Get a string value from the input.
   *
   * @param key the key to look up
   * @return the string value, or empty string if the key is not found
   * @throws IllegalArgumentException if the value exists but is not textual
   */
  public String getString(String key)
  {
    JsonNode node = data.get(key);
    if (node == null)
      return "";
    if (!node.isString())
      throw new IllegalArgumentException("Expected string for key \"" + key + "\", got: " + node.getNodeType());
    String value = node.asString();
    if (value == null)
      return "";
    return value;
  }

  /**
   * Get a string value with fallback keys.
   *
   * @param keys the keys to try in order
   * @return the first non-empty string value found, or empty string
   */
  public String getString(String... keys)
  {
    for (String key : keys)
    {
      String value = getString(key);
      if (!value.isEmpty())
        return value;
    }
    return "";
  }

  /**
   * Get a string value with default.
   *
   * @param key the key to look up
   * @param defaultValue the default value if not found
   * @return the string value, or defaultValue if not found
   */
  public String getString(String key, String defaultValue)
  {
    String value = getString(key);
    if (!value.isEmpty())
      return value;
    return defaultValue;
  }

  /**
   * Get a boolean value from the input.
   * <p>
   * Accepts both JSON booleans ({@code true}/{@code false}) and JSON strings
   * ({@code "true"}/{@code "false"}).
   *
   * @param key the key to look up
   * @param defaultValue the default value if the key is not found
   * @return the boolean value, or defaultValue if not found
   * @throws IllegalArgumentException if the value exists but is not a boolean or a string representing a boolean
   */
  public boolean getBoolean(String key, boolean defaultValue)
  {
    JsonNode node = data.get(key);
    if (node == null)
      return defaultValue;
    if (node.isBoolean())
      return node.asBoolean();
    if (node.isString())
    {
      String value = node.asString();
      if (value.equals("true"))
        return true;
      if (value.equals("false"))
        return false;
      throw new IllegalArgumentException("Expected boolean for key \"" + key + "\", got string: \"" + value + "\"");
    }
    throw new IllegalArgumentException("Expected boolean for key \"" + key + "\", got: " + node.getNodeType());
  }

  /**
   * Get an object node from the input.
   *
   * @param key the key to look up
   * @return the object node, or null if not found or not an object
   */
  public JsonNode getObject(String key)
  {
    JsonNode node = data.get(key);
    if (node != null && node.isObject())
      return node;
    return null;
  }

  /**
   * Get the raw JSON node.
   *
   * @return the underlying JSON node
   */
  public JsonNode getRaw()
  {
    return data;
  }

  /**
   * Check if the input is empty.
   *
   * @return true if the input has no data
   */
  public boolean isEmpty()
  {
    return data == null || data.isEmpty();
  }

  /**
   * Get the session ID from standard hook input locations.
   *
   * @return the session ID (never empty)
   */
  public String getSessionId()
  {
    return sessionId;
  }

  /**
   * Get the agent ID from standard hook input locations.
   * <p>
   * Returns empty string if the agent ID is missing or empty. Throws if the agent ID is present but
   * contains characters other than alphanumerics, hyphens, and underscores.
   *
   * @return the agent ID, or empty string if not present
   */
  public String getAgentId()
  {
    return agentId;
  }

  /**
   * Get the composite agent ID that uniquely identifies the current agent within the session.
   * <p>
   * For the main agent, returns {@code sessionId}. For subagents, returns
   * {@code sessionId + "/subagents/" + agentId}.
   *
   * @param sessionId the session ID
   * @return the composite agent ID
   * @throws NullPointerException if {@code sessionId} is null
   */
  public String getCatAgentId(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotNull();
    String nativeAgentId = getAgentId();
    if (nativeAgentId.isEmpty())
      return sessionId;
    return sessionId + "/" + SkillLoader.SUBAGENTS_DIR + "/" + nativeAgentId;
  }

  /**
   * Get the tool name from standard hook input locations.
   *
   * @return the tool name, or empty string if not found
   */
  public String getToolName()
  {
    return getString("tool_name", "");
  }

  /**
   * Get the tool input object.
   *
   * @return the tool input node, or an empty object if not found
   */
  public JsonNode getToolInput()
  {
    JsonNode node = getObject("tool_input");
    if (node != null)
      return node;
    return mapper.createObjectNode();
  }

  /**
   * Get the tool result object.
   *
   * @return the tool result node, or an empty object if not found
   */
  public JsonNode getToolResult()
  {
    JsonNode node = getObject("tool_result");
    if (node == null)
      node = getObject("tool_response");
    if (node != null)
      return node;
    return mapper.createObjectNode();
  }

  /**
   * Get the user message/prompt from standard hook input locations.
   *
   * @return the user prompt from message, user_message, or prompt fields
   */
  public String getUserPrompt()
  {
    return getString("message", "user_message", "prompt");
  }
}
