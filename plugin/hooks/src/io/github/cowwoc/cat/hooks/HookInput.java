package io.github.cowwoc.cat.hooks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Utility class for reading and parsing hook input from stdin.
 */
public final class HookInput
{
  private final JsonMapper mapper;
  private final JsonNode data;

  private HookInput(JsonMapper mapper, JsonNode data)
  {
    this.mapper = mapper;
    this.data = data;
  }

  /**
   * Read and parse JSON input from stdin.
   *
   * @return Parsed hook input, or empty input if stdin is not available or invalid
   */
  public static HookInput readFromStdin()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    try
    {
      if (System.console() != null && System.in.available() == 0)
      {
        // Interactive terminal with no piped input
        return new HookInput(mapper, mapper.createObjectNode());
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      String raw = reader.lines().collect(Collectors.joining("\n"));

      if (raw == null || raw.isBlank())
      {
        return new HookInput(mapper, mapper.createObjectNode());
      }

      JsonNode node = mapper.readTree(raw);
      return new HookInput(mapper, node);
    }
    catch (IOException e)
    {
      return new HookInput(mapper, mapper.createObjectNode());
    }
  }

  /**
   * Create an empty hook input.
   *
   * @return an empty HookInput instance
   */
  public static HookInput empty()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    return new HookInput(mapper, mapper.createObjectNode());
  }

  /**
   * Get a string value from the input.
   *
   * @param key the key to look up
   * @return the string value, or null if not found or not textual
   */
  public String getString(String key)
  {
    JsonNode node = data.get(key);
    if (node != null && node.isTextual())
    {
      return node.asString();
    }
    return null;
  }

  /**
   * Get a string value with fallback keys.
   *
   * @param keys the keys to try in order
   * @return the first non-empty string value found, or null
   */
  public String getString(String... keys)
  {
    for (String key : keys)
    {
      String value = getString(key);
      if (value != null && !value.isEmpty())
      {
        return value;
      }
    }
    return null;
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
    if (value != null)
    {
      return value;
    }
    return defaultValue;
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
    {
      return node;
    }
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
    {
      return node;
    }
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
    {
      node = getObject("tool_response");
    }
    if (node != null)
    {
      return node;
    }
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
