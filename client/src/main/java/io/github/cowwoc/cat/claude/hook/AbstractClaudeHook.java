/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.jackson.DefaultJacksonValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.util.GetSkill;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Abstract base class for hook handler processes that parses hook JSON from stdin (or an injected
 * value) and exposes infrastructure env-var paths and hook input/output methods via
 * {@link ClaudeHook}.
 * <p>
 * Subclasses that run in production (e.g., {@link MainClaudeHook}) construct from stdin. Test
 * subclasses accept injected JSON payloads.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractClaudeHook extends AbstractClaudePluginScope implements ClaudeHook
{
  /**
   * Pattern that accepts alphanumeric characters, hyphens, and underscores.
   * <p>
   * Prevents path traversal characters ({@code /}, {@code ..}) while accepting UUIDs and any
   * reasonable session ID format.
   */
  private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  /**
   * Pattern that accepts alphanumeric characters, hyphens, and underscores.
   * <p>
   * Prevents path traversal characters ({@code /}, {@code ..}) while accepting any reasonable
   * agent ID format.
   */
  private static final Pattern AGENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  private final JsonNode data;
  private final String sessionId;
  private final String agentId;

  /**
   * Creates a new abstract Claude hook scope with the given hook JSON payload and infrastructure paths.
   *
   * @param data the parsed hook JSON payload
   * @param projectPath the project's root directory
   * @param pluginRoot the Claude plugin root directory
   * @param pluginData the Claude plugin data directory
   * @param claudeConfigPath the Claude config directory
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if the {@code session_id} field is missing, blank, or invalid
   */
  protected AbstractClaudeHook(JsonNode data, Path projectPath, Path pluginRoot, Path pluginData,
    Path claudeConfigPath)
  {
    super(projectPath, pluginRoot, pluginData, claudeConfigPath);
    requireThat(data, "data").isNotNull();
    this.data = data;
    this.sessionId = validateSessionId(data);
    this.agentId = validateAgentId(data);
  }

  /**
   * Creates a minimal {@link JsonMapper} for bootstrap use, before a scope instance is available.
   * <p>
   * This is one of two permitted call sites for {@code JsonMapper.builder()} — the other is
   * {@link AbstractJvmScope}. All other code must obtain a mapper via
   * {@link JvmScope#getJsonMapper()}.
   *
   * @return a plain {@link JsonMapper} instance
   */
  protected static JsonMapper createStdinMapper()
  {
    return JsonMapper.builder().build();
  }

  /**
   * Reads and parses hook JSON from the given input stream.
   *
   * @param mapper the JSON mapper to use for parsing
   * @param inputStream the stream to read from
   * @return the parsed JSON node
   * @throws NullPointerException if {@code mapper} or {@code inputStream} are null
   * @throws IllegalStateException if the stream contains blank or malformed JSON, or is missing a
   *   session_id
   */
  protected static JsonNode readFrom(JsonMapper mapper, InputStream inputStream)
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(inputStream, "inputStream").isNotNull();
    try
    {
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      String raw = reader.lines().collect(Collectors.joining("\n"));

      if (raw.isBlank())
        throw new IllegalStateException("Hook input is blank.");

      return mapper.readTree(raw);
    }
    catch (JacksonException e)
    {
      throw new IllegalStateException("Hook input contains malformed JSON.", e);
    }
  }

  /**
   * Validates and returns the session ID from the JSON data.
   *
   * @param data the parsed JSON data
   * @return the session ID (never empty)
   * @throws IllegalArgumentException if the session_id field is missing, blank, or contains
   *   characters other than alphanumerics, hyphens, and underscores
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
   * @throws IllegalArgumentException if the agent_id field is present but contains invalid characters
   */
  private static String validateAgentId(JsonNode data)
  {
    JsonNode node = data.get("agent_id");
    if (node == null || !node.isString())
      return "";
    String value = node.asString();
    if (value.isBlank())
      return "";
    if (!AGENT_ID_PATTERN.matcher(value).matches())
    {
      throw new IllegalArgumentException("Invalid agent_id format: '" + value +
        "'. Expected alphanumeric, hyphens, and underscores only.");
    }
    return value;
  }

  @Override
  public String getSessionId()
  {
    ensureOpen();
    return sessionId;
  }

  @Override
  public String getCommand()
  {
    ensureOpen();
    JsonNode toolInput = getToolInput();
    JsonNode commandNode = toolInput.get("command");
    if (commandNode == null || !commandNode.isString())
      return "";
    return commandNode.asString();
  }

  @Override
  public String getString(String key)
  {
    ensureOpen();
    JsonNode node = data.get(key);
    if (node == null)
      return "";
    if (!node.isString())
      throw new IllegalArgumentException("Expected string for key \"" + key + "\", got: " +
        node.getNodeType());
    return node.asString();
  }

  @Override
  public String getString(String... keys)
  {
    ensureOpen();
    for (String key : keys)
    {
      String value = getString(key);
      if (!value.isEmpty())
        return value;
    }
    return "";
  }

  @Override
  public String getString(String key, String defaultValue)
  {
    ensureOpen();
    String value = getString(key);
    if (!value.isEmpty())
      return value;
    return defaultValue;
  }

  @Override
  public boolean getBoolean(String key, boolean defaultValue)
  {
    ensureOpen();
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
      throw new IllegalArgumentException("Expected boolean for key \"" + key +
        "\", got string: \"" + value + "\"");
    }
    throw new IllegalArgumentException("Expected boolean for key \"" + key + "\", got: " +
      node.getNodeType());
  }

  @Override
  public JsonNode getObject(String key)
  {
    ensureOpen();
    JsonNode node = data.get(key);
    if (node != null && node.isObject())
      return node;
    return null;
  }

  @Override
  public JsonNode getRaw()
  {
    ensureOpen();
    return data;
  }

  @Override
  public boolean isEmpty()
  {
    ensureOpen();
    return data.isEmpty();
  }

  @Override
  public String getAgentId()
  {
    ensureOpen();
    return agentId;
  }

  @Override
  public String getCatAgentId(String sessionId)
  {
    ensureOpen();
    requireThat(sessionId, "sessionId").isNotNull();
    String nativeAgentId = getAgentId();
    if (nativeAgentId.isEmpty())
      return sessionId;
    return sessionId + "/" + GetSkill.SUBAGENTS_DIR + "/" + nativeAgentId;
  }

  /**
   * Extracts the session ID from a CAT agent ID.
   * <p>
   * For the main agent the agent ID equals the session ID. For subagents the format is
   * {@code sessionId/subagents/agentXxx}, and only the session ID prefix is returned.
   *
   * @param catAgentId the CAT agent ID
   * @return the session ID portion of the agent ID
   * @throws NullPointerException if {@code catAgentId} is null
   */
  public static String extractSessionId(String catAgentId)
  {
    int subIdx = catAgentId.indexOf('/');
    if (subIdx < 0)
      return catAgentId;
    return catAgentId.substring(0, subIdx);
  }

  @Override
  public String getToolName()
  {
    ensureOpen();
    return getString("tool_name", "");
  }

  @Override
  public JsonNode getToolInput()
  {
    ensureOpen();
    JsonNode node = getObject("tool_input");
    if (node != null)
      return node;
    return getJsonMapper().createObjectNode();
  }

  @Override
  public JsonNode getToolResult()
  {
    ensureOpen();
    JsonNode node = getObject("tool_result");
    if (node == null)
      node = getObject("tool_response");
    if (node != null)
      return node;
    return getJsonMapper().createObjectNode();
  }

  @Override
  public JsonMapper getMapper()
  {
    ensureOpen();
    return getJsonMapper();
  }

  @Override
  public String getUserPrompt()
  {
    ensureOpen();
    return getString("message", "user_message", "prompt");
  }

  @Override
  public String empty()
  {
    ensureOpen();
    return "{}";
  }

  @Override
  public String block(String reason)
  {
    ensureOpen();
    requireThat(reason, "reason").isNotBlank();
    ObjectNode response = getJsonMapper().createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    return toJson(response);
  }

  @Override
  public String block(String reason, String additionalContext)
  {
    ensureOpen();
    requireThat(reason, "reason").isNotBlank();
    requireThat(additionalContext, "additionalContext").isNotBlank();
    ObjectNode response = getJsonMapper().createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    response.put("additionalContext", additionalContext);
    return toJson(response);
  }

  @Override
  public String additionalContext(String hookEventName, String additionalContext)
  {
    ensureOpen();
    requireThat(hookEventName, "hookEventName").isNotBlank();
    requireThat(additionalContext, "additionalContext").isNotBlank();
    ObjectNode hookSpecific = getJsonMapper().createObjectNode();
    hookSpecific.put("hookEventName", hookEventName);
    hookSpecific.put("additionalContext", additionalContext);

    ObjectNode response = getJsonMapper().createObjectNode();
    response.set("hookSpecificOutput", hookSpecific);
    return toJson(response);
  }

  @Override
  public String toJson(ObjectNode node)
  {
    ensureOpen();
    try
    {
      return getJsonMapper().writeValueAsString(node);
    }
    catch (Exception _)
    {
      return "{}";
    }
  }
}
