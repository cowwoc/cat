/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Utility class for reading and parsing hook input from stdin.
 */
public final class HookInput
{
  private final JsonMapper mapper;
  private final JsonNode data;

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
  }

  /**
   * Read and parse JSON input from stdin.
   *
   * @param mapper the JSON mapper to use for parsing
   * @return parsed hook input, or empty input if stdin is not available or contains invalid JSON
   * @throws NullPointerException if mapper is null
   */
  public static HookInput readFromStdin(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    try
    {
      if (System.console() != null && System.in.available() == 0)
      {
        // Interactive terminal with no piped input
        return new HookInput(mapper, mapper.createObjectNode());
      }
    }
    catch (IOException _)
    {
      return new HookInput(mapper, mapper.createObjectNode());
    }
    return readFrom(mapper, System.in);
  }

  /**
   * Read and parse JSON input from a stream.
   *
   * @param mapper the JSON mapper to use for parsing
   * @param inputStream the stream to read from
   * @return parsed hook input, or empty input if the stream is not available or contains invalid JSON
   * @throws NullPointerException if mapper or inputStream is null
   */
  public static HookInput readFrom(JsonMapper mapper, InputStream inputStream)
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(inputStream, "inputStream").isNotNull();
    try
    {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      String raw = reader.lines().collect(Collectors.joining("\n"));

      if (raw == null || raw.isBlank())
        return new HookInput(mapper, mapper.createObjectNode());

      JsonNode node = mapper.readTree(raw);
      return new HookInput(mapper, node);
    }
    catch (JacksonException _)
    {
      return new HookInput(mapper, mapper.createObjectNode());
    }
  }

  /**
   * Create an empty hook input.
   *
   * @param mapper the JSON mapper to use
   * @return an empty HookInput instance
   * @throws NullPointerException if mapper is null
   */
  public static HookInput empty(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    return new HookInput(mapper, mapper.createObjectNode());
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
   * @return the session ID, or empty string if not found
   */
  public String getSessionId()
  {
    return getString("session_id", "");
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
