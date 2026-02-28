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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects assistant giving-up patterns in conversation logs.
 * <p>
 * Monitors the last 20 assistant messages for token usage rationalization patterns that violate
 * the Token Usage Policy. Each message is checked individually — keywords must all appear in the
 * same message to trigger. Only text-type content blocks are scanned; tool_use inputs are excluded.
 * Rate-limited to once per 60 seconds per session.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class DetectAssistantGivingUp implements PostToolHandler
{
  private static final Duration RATE_LIMIT_DURATION = Duration.ofSeconds(60);
  private static final int MESSAGE_LIMIT = 20;
  private static final Map<String, Instant> SESSION_TO_LAST_CHECK = new ConcurrentHashMap<>();

  private final Clock clock;
  private final Path claudeConfigDir;
  private final JsonMapper mapper;

  /**
   * Creates a new detect-assistant-giving-up handler.
   *
   * @param scope the JVM scope providing configuration paths and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public DetectAssistantGivingUp(JvmScope scope)
  {
    this(Clock.systemUTC(), scope);
  }

  /**
   * Creates a new detect-assistant-giving-up handler with specified clock.
   *
   * @param clock the clock to use for rate limiting
   * @param scope the JVM scope providing configuration paths and JSON mapper
   * @throws NullPointerException if {@code clock} or {@code scope} are null
   */
  public DetectAssistantGivingUp(Clock clock, JvmScope scope)
  {
    requireThat(clock, "clock").isNotNull();
    requireThat(scope, "scope").isNotNull();
    this.clock = clock;
    this.claudeConfigDir = scope.getClaudeConfigDir();
    this.mapper = scope.getJsonMapper();
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolResult, "toolResult").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(hookData, "hookData").isNotNull();

    Instant now = clock.instant();
    Instant lastCheck = SESSION_TO_LAST_CHECK.get(sessionId);

    if (lastCheck != null)
    {
      Duration timeSinceLastCheck = Duration.between(lastCheck, now);
      if (timeSinceLastCheck.compareTo(RATE_LIMIT_DURATION) < 0)
        return Result.allow();
    }

    SESSION_TO_LAST_CHECK.put(sessionId, now);

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
    return claudeConfigDir.
      resolve("projects").
      resolve("-workspace").
      resolve(sessionId + ".jsonl");
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
        map(this::extractTextContent).
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
   * @return the extracted text content, or empty string if none found or parse fails
   */
  private String extractTextContent(String jsonlLine)
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

  /**
   * Detects giving-up patterns in a single assistant message's text.
   *
   * @param messageText the text content of one assistant message
   * @return true if a giving-up pattern is detected
   */
  private boolean detectGivingUpPattern(String messageText)
  {
    String lower = messageText.toLowerCase(Locale.ENGLISH);

    if (containsPattern(lower, "given", "token usage", "let me"))
      return true;
    if (containsPattern(lower, "given", "token usage", "i'll"))
      return true;
    if (containsPattern(lower, "given", "token usage", "strategic", "optimization"))
      return true;
    if (containsPattern(lower, "token usage", "complete a few more"))
      return true;
    if (containsPattern(lower, "token usage", "then proceed to"))
      return true;
    if (containsPattern(lower, "token usage (", "/", ")"))
      return true;
    if (containsPattern(lower, "tokens used", "let me"))
      return true;
    if (containsPattern(lower, "tokens remaining", "i'll"))
      return true;
    if (containsPattern(lower, "given our token", "complete"))
      return true;
    if (containsPattern(lower, "given our context", "complete"))
      return true;
    if (containsPattern(lower, "token budget", "a few more"))
      return true;
    if (containsPattern(lower, "context constraints", "strategic"))
      return true;
    return containsPattern(lower, "i've optimized", "let me", "then proceed") ||
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
