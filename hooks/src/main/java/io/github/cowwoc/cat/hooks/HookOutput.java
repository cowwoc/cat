/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utility class for building hook output JSON.
 */
public final class HookOutput
{
  private final JsonMapper mapper;

  /**
   * Creates a new HookOutput.
   *
   * @param mapper the JSON mapper to use for serialization
   * @throws NullPointerException if {@code mapper} is null
   */
  public HookOutput(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  /**
   * Returns an empty response (allow the operation).
   *
   * @return empty JSON object
   */
  public String empty()
  {
    return "{}";
  }

  /**
   * Builds a block decision.
   *
   * @param reason Reason for blocking
   * @return JSON string with block decision
   * @throws IllegalArgumentException if {@code reason} is null or blank
   */
  public String block(String reason)
  {
    requireThat(reason, "reason").isNotBlank();
    ObjectNode response = mapper.createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    return toJson(response);
  }

  /**
   * Builds a block decision with additional context.
   *
   * @param reason Reason for blocking
   * @param additionalContext Extra context to provide
   * @return JSON string with block decision and context
   * @throws IllegalArgumentException if {@code reason} or {@code additionalContext} are null or blank
   */
  public String block(String reason, String additionalContext)
  {
    requireThat(reason, "reason").isNotBlank();
    requireThat(additionalContext, "additionalContext").isNotBlank();
    ObjectNode response = mapper.createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    response.put("additionalContext", additionalContext);
    return toJson(response);
  }

  /**
   * Builds additional context via hookSpecificOutput.
   *
   * @param hookEventName The hook event name (e.g., "UserPromptSubmit", "PostToolUse")
   * @param additionalContext The context to inject
   * @return JSON string with hook-specific output
   * @throws IllegalArgumentException if {@code hookEventName} or {@code additionalContext} are null or blank
   */
  public String additionalContext(String hookEventName, String additionalContext)
  {
    requireThat(hookEventName, "hookEventName").isNotBlank();
    requireThat(additionalContext, "additionalContext").isNotBlank();
    ObjectNode hookSpecific = mapper.createObjectNode();
    hookSpecific.put("hookEventName", hookEventName);
    hookSpecific.put("additionalContext", additionalContext);

    ObjectNode response = mapper.createObjectNode();
    response.set("hookSpecificOutput", hookSpecific);
    return toJson(response);
  }

  /**
   * Converts a JSON node to a string.
   *
   * @param node JSON node to serialize
   * @return JSON string, or empty object if serialization fails
   */
  public String toJson(ObjectNode node)
  {
    try
    {
      return mapper.writeValueAsString(node);
    }
    catch (Exception _)
    {
      return "{}";
    }
  }

  /**
   * Wrap a string in system-reminder tags.
   *
   * @param content the content to wrap
   * @return the content wrapped in system-reminder tags
   * @throws IllegalArgumentException if content is null or blank
   */
  public static String wrapSystemReminder(String content)
  {
    requireThat(content, "content").isNotBlank();
    return "<system-reminder>\n" + content + "\n</system-reminder>";
  }
}
