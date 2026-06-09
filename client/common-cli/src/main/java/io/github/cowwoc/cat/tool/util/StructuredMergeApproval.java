/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;

/**
 * Detects merge approval from Claude Code's structured AskUserQuestion transcript entries.
 */
public final class StructuredMergeApproval
{
  private static final String APPROVAL_OPTION = "approve and merge";

  private StructuredMergeApproval()
  {
  }

  /**
   * Indicates whether the recent session transcript contains a structured merge approval.
   *
   * @param mapper the JSON mapper
   * @param recentLines recent JSONL transcript lines
   * @return true if an AskUserQuestion invocation was followed by a tool_result selecting
   *   {@code Approve and merge}
   */
  public static boolean isPresent(JsonMapper mapper, List<String> recentLines)
  {
    return containsAskUserQuestion(recentLines) && containsApprovalToolResult(mapper, recentLines);
  }

  /**
   * Checks whether transcript includes AskUserQuestion tool invocation.
   *
   * @param recentLines recent transcript lines
   * @return {@code true} if AskUserQuestion appears
   */
  private static boolean containsAskUserQuestion(List<String> recentLines)
  {
    for (String line : recentLines)
    {
      if (line.toLowerCase(Locale.ROOT).contains("askuserquestion"))
        return true;
    }
    return false;
  }

  /**
   * Checks whether transcript includes merge-approval tool result.
   *
   * @param mapper JSON mapper
   * @param recentLines recent transcript lines
   * @return {@code true} if approval tool result appears
   */
  private static boolean containsApprovalToolResult(JsonMapper mapper, List<String> recentLines)
  {
    for (String line : recentLines)
    {
      if (isApprovalToolResult(mapper, line))
        return true;
    }
    return false;
  }

  /**
   * Parses one transcript line for structured approval selection.
   *
   * @param mapper JSON mapper
   * @param line transcript line
   * @return {@code true} if line is approval tool result
   */
  private static boolean isApprovalToolResult(JsonMapper mapper, String line)
  {
    try
    {
      JsonNode node = mapper.readTree(line);
      if (node == null || !node.isObject())
        return false;
      JsonNode typeNode = node.get("type");
      if (typeNode == null || !typeNode.asString().equals("user"))
        return false;
      JsonNode messageNode = node.get("message");
      if (messageNode == null || !messageNode.isObject())
        return false;
      JsonNode contentNode = messageNode.get("content");
      if (contentNode == null || !contentNode.isArray())
        return false;
      for (JsonNode element : contentNode)
      {
        JsonNode elementTypeNode = element.get("type");
        if (elementTypeNode == null || !elementTypeNode.asString().equals("tool_result"))
          continue;
        JsonNode resultContentNode = element.get("content");
        if (resultContentNode != null && resultContentNode.isString() &&
            resultContentNode.asString().toLowerCase(Locale.ROOT).contains(APPROVAL_OPTION))
        {
          return true;
        }
      }
    }
    catch (JacksonException _)
    {
      return false;
    }
    return false;
  }
}
