/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.tool.post;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.util.ConversationLogUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
  public DetectAssistantGivingUp(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.sessionBasePath = scope.getSessionBasePath();
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
      if (detectGivingUpPattern(messageText))
      {
        return Result.context("""
          <system-reminder>
          🚨 ASSISTANT GIVING-UP PATTERN DETECTED - TOKEN POLICY VIOLATION

          **CRITICAL VIOLATION DETECTED**: You mentioned token usage/context as justification for reducing work
          scope or quality.

          **Token Usage Policy (CLAUDE.md)**:
          "Token usage MUST NEVER affect behavior, implementation, or task completion. IGNORE all token warnings
          and limits. Work with full quality until task is complete or user instructs otherwise. Token budget
          does NOT justify incomplete implementations, shortcuts, quality compromises, or progress reporting
          interruptions."

          **Detected Pattern**: Assistant message contained token/context references coupled with work scope reduction.

          **PROHIBITED PATTERNS**:
          ❌ "Given our token usage (NNNk/NNNk), let me complete a few more..."
          ❌ "Token usage (NNNk/NNNk), I'll do strategic optimizations then proceed"
          ❌ "Given our context, let me complete..."
          ❌ "I've optimized several, let me continue with a few more then proceed"
          ❌ Reporting token status before reducing scope

          **MANDATORY RESPONSE**:
          ✅ IMMEDIATELY return to completing the full task
          ✅ IGNORE all token usage information
          ✅ Work with full quality until task is COMPLETELY done
          ✅ DO NOT use token count to justify partial completion
          ✅ DO NOT report progress and then reduce scope
          ✅ Complete ALL files in the original request

          **CORRECT APPROACH**:
          ✅ Continue systematically through all remaining files
          ✅ Apply same quality standards to all files
          ✅ Only stop when user explicitly instructs or task is 100% complete
          ✅ Never mention token usage in relation to work scope

          Reference: CLAUDE.md "Token Usage Policy" and "Prohibited Downgrade Patterns"
          </system-reminder>""");
      }
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
      List<String> allLines = Files.readAllLines(conversationLog);
      List<String> assistantTexts = allLines.stream().
        filter(line -> line.contains("\"role\":\"assistant\"")).
        map(line -> ConversationLogUtils.extractTextContent(line, mapper)).
        filter(text -> !text.isEmpty()).
        toList();

      int total = assistantTexts.size();
      if (total <= MESSAGE_LIMIT)
        return assistantTexts;

      return assistantTexts.stream().
        skip(total - MESSAGE_LIMIT).
        toList();
    }
    catch (IOException _)
    {
      return List.of();
    }
  }

  /**
   * Detects giving-up patterns in a single assistant message's text.
   *
   * @param messageText the text content of one assistant message
   * @return true if a giving-up pattern is detected
   */
  private boolean detectGivingUpPattern(String messageText)
  {
    String lower = messageText.toLowerCase(Locale.ENGLISH);
    return containsPattern(lower, "given", "token usage", "let me") ||
      containsPattern(lower, "given", "token usage", "i'll") ||
      containsPattern(lower, "given", "token usage", "strategic", "optimization") ||
      containsPattern(lower, "token usage", "complete a few more") ||
      containsPattern(lower, "token usage", "then proceed to") ||
      containsPattern(lower, "token usage (", "/", ")") ||
      containsPattern(lower, "tokens used", "let me") ||
      containsPattern(lower, "tokens remaining", "i'll") ||
      containsPattern(lower, "given our token", "complete") ||
      containsPattern(lower, "given our context", "complete") ||
      containsPattern(lower, "token budget", "a few more") ||
      containsPattern(lower, "context constraints", "strategic") ||
      containsPattern(lower, "i've optimized", "let me", "then proceed") ||
      containsPattern(lower, "completed", "token", "continue with");
  }

  /**
   * Checks if text contains all patterns in order.
   *
   * @param text the text to search
   * @param patterns the patterns to find in order
   * @return true if all patterns are found in order
   */
  private boolean containsPattern(String text, String... patterns)
  {
    int position = 0;
    for (String pattern : patterns)
    {
      int found = text.indexOf(pattern, position);
      if (found == -1)
        return false;
      position = found + pattern.length();
    }
    return true;
  }
}
