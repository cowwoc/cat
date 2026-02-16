/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A priming message that simulates prior conversation context in empirical tests.
 * <p>
 * Priming messages are sent before the test prompt to establish conversation history.
 * They can be either simple user text messages or completed tool use sequences.
 */
public sealed interface PrimingMessage
{
  /**
   * A user text message.
   *
   * @param text the message text
   */
  record UserMessage(String text) implements PrimingMessage
  {
    /**
     * Creates a new user message.
     *
     * @throws NullPointerException if {@code text} is null
     */
    public UserMessage
    {
      requireThat(text, "text").isNotNull();
    }
  }

  /**
   * A completed tool use interaction (assistant tool_use + user tool_result).
   *
   * @param tool the tool name (e.g., "Bash", "Read")
   * @param input the tool input parameters
   * @param output the tool output content
   */
  record ToolUse(String tool, Map<String, Object> input, String output) implements PrimingMessage
  {
    /**
     * Creates a new tool use message.
     *
     * @throws NullPointerException if {@code tool}, {@code input}, or {@code output} are null
     */
    public ToolUse
    {
      requireThat(tool, "tool").isNotNull();
      requireThat(input, "input").isNotNull();
      requireThat(output, "output").isNotNull();
    }
  }

  /**
   * Converts a raw JSON-deserialized list of objects to typed priming messages.
   * <p>
   * Each element can be:
   * <ul>
   *   <li>A {@code String} -- converted to {@link UserMessage}</li>
   *   <li>A {@code Map} with {@code "type": "tool_use"} -- converted to {@link ToolUse}</li>
   * </ul>
   *
   * @param rawMessages the raw deserialized priming messages
   * @return the list of typed priming messages
   * @throws NullPointerException if {@code rawMessages} is null
   * @throws IllegalArgumentException if {@code rawMessages} contains a null element, a message has an
   *                                  unsupported type, or is missing required fields
   */
  static List<PrimingMessage> fromRawList(List<Object> rawMessages)
  {
    requireThat(rawMessages, "rawMessages").isNotNull().doesNotContain(null);
    List<PrimingMessage> result = new ArrayList<>(rawMessages.size());
    for (int i = 0; i < rawMessages.size(); ++i)
    {
      Object raw = rawMessages.get(i);
      if (raw instanceof String text)
        result.add(new UserMessage(text));
      else if (raw instanceof Map<?, ?> rawMap)
      {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        String type = (String) map.get("type");
        if (type == null || !type.equals("tool_use"))
        {
          throw new IllegalArgumentException(
            "priming_messages[" + i + "]: unsupported type '" + type + "'. " +
              "Expected 'tool_use'. Message: " + map);
        }
        String toolName = (String) map.get("tool");
        if (toolName == null)
        {
          throw new IllegalArgumentException(
            "priming_messages[" + i + "]: tool_use message is missing required field 'tool'. " +
              "Message: " + map);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> toolInput = (Map<String, Object>) map.get("input");
        if (toolInput == null)
        {
          throw new IllegalArgumentException(
            "priming_messages[" + i + "]: tool_use message is missing required field 'input'. " +
              "Message: " + map);
        }
        String toolOutput = (String) map.get("output");
        if (toolOutput == null)
        {
          throw new IllegalArgumentException(
            "priming_messages[" + i + "]: tool_use message is missing required field 'output'. " +
              "Message: " + map);
        }
        result.add(new ToolUse(toolName, toolInput, toolOutput));
      }
      else
      {
        throw new IllegalArgumentException(
          "priming_messages[" + i + "]: unsupported message type: " +
            raw.getClass().getName());
      }
    }
    return result;
  }
}
