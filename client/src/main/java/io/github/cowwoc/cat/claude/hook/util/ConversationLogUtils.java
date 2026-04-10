/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
   *   <li>Array content with no {@code tool_use} blocks: only {@code {"type":"text","text":"..."}}
   *       blocks are included; other block types are skipped</li>
   *   <li>Compound array content (containing at least one {@code tool_use} block): returns {@code ""}
   *       because the text in these messages narrates tool invocations rather than reflecting
   *       the agent's analytical reasoning</li>
   * </ul>
   * Also handles the wrapped format where the message is under a {@code "message"} key.
   * <p>
   * <b>Design tradeoff:</b> Compound messages (those containing {@code tool_use} blocks) are
   * suppressed entirely rather than scanning only their text portions. This is intentional: the
   * text in compound messages typically describes the upcoming tool call (e.g., "Let me remove
   * this file") and is not an expression of the agent's intent to give up. Scanning it would
   * cause false positives. The tradeoff is that genuinely problematic text co-located with a
   * tool call is not detected; this is acceptable because giving-up signals in tool-adjacent
   * narration are low-confidence and the false-positive cost is higher than the miss cost.
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
        // Skip compound messages (text + tool_use): the text describes the upcoming tool call,
        // not the agent's analytical reasoning. Scanning it causes false positives in
        // DetectAssistantGivingUp (e.g., "Let me remove X" → CODE_REMOVAL false positive).
        for (JsonNode block : contentNode)
        {
          if ("tool_use".equals(block.path("type").asString()))
            return "";
        }
        // Pure-text message: extract and concatenate all text blocks
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : contentNode)
        {
          if ("text".equals(block.path("type").asString()))
          {
            String text = block.path("text").asString();
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
    catch (JacksonException e)
    {
      Logger log = LoggerFactory.getLogger(ConversationLogUtils.class);
      log.debug("Failed to parse JSONL line as JSON: {}", e.getMessage());
      return "";
    }
  }
}
