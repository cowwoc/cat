/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Utility methods for parsing conversation log JSONL lines.
 */
public final class ConversationLogUtils
{
  private ConversationLogUtils()
  {
  }

  /**
   * Extracts text-only content from an assistant JSONL line.
   * <p>
   * Handles two content formats:
   * <ul>
   *   <li>String content: {@code {"role":"assistant","content":"text"}}</li>
   *   <li>Array content: only {@code {"type":"text","text":"..."}} blocks are included;
   *       tool_use and other block types are skipped</li>
   * </ul>
   * Also handles the wrapped format where the message is under a {@code "message"} key.
   *
   * @param jsonlLine the raw JSONL line to parse
   * @param mapper    the JSON mapper to use for parsing
   * @return the extracted text content, or empty string if none found or parse fails
   * @throws NullPointerException if {@code jsonlLine} or {@code mapper} is null
   */
  public static String extractTextContent(String jsonlLine, JsonMapper mapper)
  {
    try
    {
      JsonNode root = mapper.readTree(jsonlLine);
      JsonNode contentNode = root.path("content");
      if (contentNode.isMissingNode())
        contentNode = root.path("message").path("content");
      if (contentNode.isMissingNode())
        return "";

      if (contentNode.isString())
        return contentNode.asString();

      if (contentNode.isArray())
      {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : contentNode)
        {
          if ("text".equals(block.path("type").asString()))
          {
            String rawText = block.path("text").asString();
            String text;
            if (rawText != null)
              text = rawText;
            else
              text = "";
            if (!text.isEmpty())
            {
              if (!sb.isEmpty())
                sb.append(' ');
              sb.append(text);
            }
          }
        }
        return sb.toString();
      }
      return "";
    }
    catch (JacksonException _)
    {
      return "";
    }
  }
}
