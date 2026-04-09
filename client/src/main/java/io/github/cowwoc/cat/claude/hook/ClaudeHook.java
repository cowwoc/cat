/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A {@link ClaudePluginScope} for hook handler processes that combines the Claude session environment
 * (project path, plugin root, config dir), hook input data, and hook output building in a single
 * scope object.
 * <p>
 * Implementations read infrastructure env vars at construction and parse hook JSON from stdin
 * (production) or from an injected value (tests).
 */
public interface ClaudeHook extends ClaudePluginScope
{
  /**
   * Returns the Claude session ID extracted from the hook input JSON.
   *
   * @return the session ID (never blank)
   * @throws IllegalStateException if this scope is closed
   */
  String getSessionId();

  // Hook input methods

  /**
   * Returns the bash command from the tool input.
   *
   * @return the command string, or empty string if not found
   * @throws IllegalStateException if this scope is closed
   */
  String getCommand();

  /**
   * Returns a string value from the hook input.
   *
   * @param key the key to look up
   * @return the string value, or empty string if not found
   * @throws IllegalArgumentException if the value exists but is not textual
   * @throws IllegalStateException if this scope is closed
   */
  String getString(String key);

  /**
   * Returns a string value with fallback keys.
   *
   * @param keys the keys to try in order
   * @return the first non-empty string value found, or empty string
   * @throws IllegalStateException if this scope is closed
   */
  String getString(String... keys);

  /**
   * Returns a string value with a default.
   *
   * @param key the key to look up
   * @param defaultValue the default value if not found
   * @return the string value, or defaultValue if not found
   * @throws IllegalStateException if this scope is closed
   */
  String getString(String key, String defaultValue);

  /**
   * Returns a boolean value from the hook input.
   *
   * @param key the key to look up
   * @param defaultValue the default value if the key is not found
   * @return the boolean value, or defaultValue if not found
   * @throws IllegalArgumentException if the value exists but is not a boolean or a string representing one
   * @throws IllegalStateException if this scope is closed
   */
  boolean getBoolean(String key, boolean defaultValue);

  /**
   * Returns an object node from the hook input.
   *
   * @param key the key to look up
   * @return the object node, or null if not found or not an object
   * @throws IllegalStateException if this scope is closed
   */
  JsonNode getObject(String key);

  /**
   * Returns the raw JSON node of the hook input.
   *
   * @return the underlying JSON node
   * @throws IllegalStateException if this scope is closed
   */
  JsonNode getRaw();

  /**
   * Indicates whether the hook input is empty.
   *
   * @return true if the hook input has no data
   * @throws IllegalStateException if this scope is closed
   */
  boolean isEmpty();

  /**
   * Returns the agent ID from the hook input.
   *
   * @return the agent ID, or empty string if not present
   * @throws IllegalStateException if this scope is closed
   */
  String getAgentId();

  /**
   * Returns the composite agent ID that uniquely identifies the current agent within the session.
   *
   * @param sessionId the session ID
   * @return the composite agent ID
   * @throws NullPointerException if {@code sessionId} is null
   * @throws IllegalStateException if this scope is closed
   */
  String getCatAgentId(String sessionId);

  /**
   * Returns the tool name from the hook input.
   *
   * @return the tool name, or empty string if not found
   * @throws IllegalStateException if this scope is closed
   */
  String getToolName();

  /**
   * Returns the tool input object.
   *
   * @return the tool input node, or an empty object if not found
   * @throws IllegalStateException if this scope is closed
   */
  JsonNode getToolInput();

  /**
   * Returns the tool result object.
   *
   * @return the tool result node, or an empty object if not found
   * @throws IllegalStateException if this scope is closed
   */
  JsonNode getToolResult();

  /**
   * Returns the JSON mapper used to parse the hook input.
   *
   * @return the JSON mapper
   * @throws IllegalStateException if this scope is closed
   */
  JsonMapper getMapper();

  /**
   * Returns the user message/prompt from the hook input.
   *
   * @return the user prompt from message, user_message, or prompt fields
   * @throws IllegalStateException if this scope is closed
   */
  String getUserPrompt();

  // Hook output methods

  /**
   * Returns an empty response (allow the operation).
   *
   * @return empty JSON object string
   * @throws IllegalStateException if this scope is closed
   */
  String empty();

  /**
   * Builds a block decision.
   *
   * @param reason the reason for blocking
   * @return JSON string with block decision
   * @throws IllegalArgumentException if {@code reason} is null or blank
   * @throws IllegalStateException if this scope is closed
   */
  String block(String reason);

  /**
   * Builds a block decision with additional context.
   *
   * @param reason the reason for blocking
   * @param additionalContext extra context to provide
   * @return JSON string with block decision and context
   * @throws IllegalArgumentException if {@code reason} or {@code additionalContext} are null or blank
   * @throws IllegalStateException if this scope is closed
   */
  String block(String reason, String additionalContext);

  /**
   * Builds additional context via hookSpecificOutput.
   *
   * @param hookEventName the hook event name (e.g., "UserPromptSubmit", "PostToolUse")
   * @param additionalContext the context to inject
   * @return JSON string with hook-specific output
   * @throws IllegalArgumentException if {@code hookEventName} or {@code additionalContext} are null or blank
   * @throws IllegalStateException if this scope is closed
   */
  String additionalContext(String hookEventName, String additionalContext);

  /**
   * Converts a JSON node to a string.
   *
   * @param node the JSON node to serialize
   * @return JSON string, or empty object if serialization fails
   * @throws IllegalStateException if this scope is closed
   */
  String toJson(ObjectNode node);
}
