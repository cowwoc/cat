/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool.post;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.PostToolHandler;
import io.github.cowwoc.cat.claude.hook.util.ConversationLogUtils;
import io.github.cowwoc.cat.claude.hook.util.GivingUpDetector;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Detects assistant giving-up patterns in conversation logs.
 * <p>
 * Monitors the last 20 assistant messages for token usage rationalization patterns that violate
 * the Token Usage Policy. Each message is checked individually — keywords must all appear in the
 * same message to trigger. Only text-type content blocks are scanned; tool_use inputs are excluded.
 */
public final class DetectAssistantGivingUp implements PostToolHandler
{
  private static final int MESSAGE_LIMIT = 20;

  private final Path sessionBasePath;
  private final JsonMapper mapper;

  /**
   * Creates a new detect-assistant-giving-up handler.
   *
   * @param scope the JVM scope providing configuration paths and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public DetectAssistantGivingUp(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.sessionBasePath = scope.getClaudeSessionsPath();
    this.mapper = scope.getJsonMapper();
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolResult, "toolResult").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(hookData, "hookData").isNotNull();

    Path conversationLog = getConversationLogPath(sessionId);
    if (!Files.exists(conversationLog))
      return Result.allow();

    List<String> recentTexts = getRecentAssistantTextContent(conversationLog);
    if (recentTexts.isEmpty())
      return Result.allow();

    for (String messageText : recentTexts)
    {
      String reminder = new GivingUpDetector().check(messageText);
      if (!reminder.isEmpty())
        return Result.context("<system-reminder>\n" + reminder + "\n</system-reminder>");
    }

    return Result.allow();
  }

  /**
   * Gets the path to the conversation log file.
   *
   * @param sessionId the session ID
   * @return the conversation log path
   */
  Path getConversationLogPath(String sessionId)
  {
    return sessionBasePath.resolve(sessionId + ".jsonl");
  }

  /**
   * Gets the text content of recent assistant messages from the conversation log.
   * <p>
   * Extracts only text-type content blocks, ignoring tool_use and other block types.
   * Each entry in the returned list corresponds to one assistant message turn.
   *
   * @param conversationLog the path to the conversation log
   * @return list of text content strings, one per assistant message (up to MESSAGE_LIMIT)
   */
  private List<String> getRecentAssistantTextContent(Path conversationLog)
  {
    try
    {
      // Use a bounded deque to keep only the last MESSAGE_LIMIT assistant messages,
      // avoiding loading the entire (potentially multi-MB) session file into memory.
      Deque<String> buffer = new ArrayDeque<>(MESSAGE_LIMIT + 1);
      try (BufferedReader reader = Files.newBufferedReader(conversationLog))
      {
        String line = reader.readLine();
        while (line != null)
        {
          if (line.contains("\"role\":\"assistant\""))
          {
            String text = ConversationLogUtils.extractTextContent(line, mapper);
            if (!text.isEmpty())
            {
              buffer.addLast(text);
              if (buffer.size() > MESSAGE_LIMIT)
                buffer.removeFirst();
            }
          }
          line = reader.readLine();
        }
      }
      return new ArrayList<>(buffer);
    }
    catch (IOException _)
    {
      return List.of();
    }
  }
}
