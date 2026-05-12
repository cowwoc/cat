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
import io.github.cowwoc.cat.claude.hook.util.TurnSegment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
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

    List<TurnSegment> recentSegments = getRecentAssistantSegments(conversationLog);
    if (recentSegments.isEmpty())
      return Result.allow();

    GivingUpDetector detector = new GivingUpDetector();
    String reminder = detector.check(recentSegments);
    if (!reminder.isEmpty())
      return Result.context("<system-reminder>\n" + reminder + "\n</system-reminder>");

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
   * Gets the turn segments of recent assistant messages from the conversation log.
   * <p>
   * Returns a flat list of all {@link TurnSegment}s from the last {@link #MESSAGE_LIMIT} assistant
   * message turns. Each segment carries the file paths of its adjacent tool_use blocks for
   * context-aware giving-up detection.
   *
   * @param conversationLog the path to the conversation log
   * @return flat list of segments from up to MESSAGE_LIMIT recent assistant messages
   */
  private List<TurnSegment> getRecentAssistantSegments(Path conversationLog)
  {
    try
    {
      // Use a bounded deque of per-message segment lists to cap at MESSAGE_LIMIT messages,
      // then flatten at the end. Avoids loading the entire (potentially multi-MB) session file.
      Deque<List<TurnSegment>> buffer = new ArrayDeque<>(MESSAGE_LIMIT + 1);
      try (BufferedReader reader = Files.newBufferedReader(conversationLog))
      {
        String line = reader.readLine();
        while (line != null)
        {
          if (line.contains("\"role\":\"assistant\""))
          {
            List<TurnSegment> segments = ConversationLogUtils.extractSegments(line, mapper);
            if (!segments.isEmpty())
            {
              buffer.addLast(segments);
              if (buffer.size() > MESSAGE_LIMIT)
                buffer.removeFirst();
            }
          }
          line = reader.readLine();
        }
      }
      return buffer.stream().flatMap(Collection::stream).toList();
    }
    catch (IOException _)
    {
      return List.of();
    }
  }
}
