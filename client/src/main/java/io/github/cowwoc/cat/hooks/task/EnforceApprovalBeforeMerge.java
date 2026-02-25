/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.task;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.util.SessionFileUtils;
import io.github.cowwoc.cat.hooks.util.TrustLevel;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Block work-merge subagent spawn when trust=medium/low without explicit user approval.
 * <p>
 * This handler enforces the trust-based approval requirement:
 * <ul>
 *   <li>trust=high: No approval needed (skip this check)</li>
 *   <li>trust=medium/low: MUST have explicit user approval before merge</li>
 * </ul>
 * <p>
 * Prevention: Blocks Task tool when spawning cat:work-merge without prior approval.
 */
public final class EnforceApprovalBeforeMerge implements TaskHandler
{
  /**
   * Number of recent JSONL lines to scan for approval messages. 75 lines covers approximately
   * the last several user interactions, which is sufficient to detect recent approval without scanning
   * the entire session file.
   */
  private static final int RECENT_LINES_TO_SCAN = 75;

  /**
   * Recognized phrases for direct merge approval. Each phrase is lowercased for case-insensitive
   * matching.
   */
  private static final Set<String> APPROVAL_PHRASES = Set.of(
    "approve and merge",
    "approve merge",
    "approved merge",
    "approved and merge",
    "merge and approve",
    "merge approved");

  private final JvmScope scope;

  /**
   * Creates a new EnforceApprovalBeforeMerge handler.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public EnforceApprovalBeforeMerge(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId, String cwd)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();

    JsonNode subagentTypeNode = toolInput.get("subagent_type");
    String subagentType;
    if (subagentTypeNode != null)
      subagentType = subagentTypeNode.asString();
    else
      subagentType = "";

    if (!subagentType.equals("cat:work-merge"))
      return Result.allow();

    TrustLevel trust;
    try
    {
      trust = getTrustLevel();
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }

    if (trust == TrustLevel.HIGH)
      return Result.allow();

    if (sessionId.isEmpty())
    {
      String reason = "FAIL: Cannot verify user approval - session ID not available.\n" +
                      "\n" +
                      "Trust level is \"" + trust + "\" which requires explicit approval before merge.\n" +
                      "\n" +
                      "BLOCKING: This merge attempt is blocked until user approval can be verified.";
      return Result.block(reason);
    }

    Path sessionFile = scope.getSessionBasePath().resolve(sessionId + ".jsonl");

    if (!Files.exists(sessionFile))
    {
      String reason = "FAIL: Cannot verify user approval - session file not found.\n" +
                      "\n" +
                      "Trust level is \"" + trust + "\" which requires explicit approval before merge.\n" +
                      "\n" +
                      "BLOCKING: This merge attempt is blocked until user approval can be verified.";
      return Result.block(reason);
    }

    if (checkApprovalInSession(sessionFile))
      return Result.allow();

    String reason = "FAIL: Explicit user approval required before merge\n" +
                    "\n" +
                    "Trust level: " + trust + "\n" +
                    "Requirement: Explicit user approval via AskUserQuestion or direct message\n" +
                    "\n" +
                    "BLOCKING: No approval detected in session history.\n" +
                    "\n" +
                    "Approval can be given in two ways:\n" +
                    "1. AskUserQuestion wizard: Select \"Approve and merge\" from the approval gate options\n" +
                    "2. Direct message: Type \"approve and merge\" (or \"approve merge\") in the chat\n" +
                    "\n" +
                    "Do NOT proceed to merge based on:\n" +
                    "- Silence or lack of objection\n" +
                    "- System reminders or notifications\n" +
                    "- Assumed approval\n" +
                    "\n" +
                    "Fail-fast principle: Unknown consent = No consent = STOP";

    return Result.block(reason);
  }

  /**
   * Get the trust level from cat-config.json.
   *
   * @return the trust level
   * @throws IOException if the config file cannot be read or contains invalid JSON
   */
  private TrustLevel getTrustLevel() throws IOException
  {
    Config config = Config.load(scope.getJsonMapper(), scope.getClaudeProjectDir());
    return config.getTrust();
  }

  /**
   * Check if explicit approval is found in the session file.
   * <p>
   * Approval is detected by either:
   * <ul>
   *   <li>The AskUserQuestion wizard flow (askuserquestion + approve + user_approval or user message)</li>
   *   <li>A direct user message containing both "approve" and "merge"</li>
   * </ul>
   *
   * @param sessionFile the session JSONL file
   * @return true if approval found
   */
  private boolean checkApprovalInSession(Path sessionFile)
  {
    try
    {
      List<String> recentLines = SessionFileUtils.getRecentLines(sessionFile, RECENT_LINES_TO_SCAN);
      String recentContent = String.join("\n", recentLines);
      String lowerContent = recentContent.toLowerCase(Locale.ROOT);

      boolean hasAskQuestion = lowerContent.contains("askuserquestion") &&
                               lowerContent.contains("approve");
      boolean hasApproval = lowerContent.contains("user_approval") ||
                            hasWizardApprovalMessage(recentLines);
      if (hasAskQuestion && hasApproval)
        return true;

      if (hasDirectApprovalMessage(recentLines))
        return true;
    }
    catch (IOException _)
    {
      // Cannot read session file
    }

    return false;
  }

  /**
   * Checks if a JSONL line represents a user message.
   *
   * @param line the raw JSONL line
   * @return true if the line is a valid JSON object with {@code "type": "user"}
   */
  private boolean isUserMessage(String line)
  {
    JsonMapper mapper = scope.getJsonMapper();
    try
    {
      JsonNode node = mapper.readTree(line);
      if (node == null || !node.isObject())
        return false;
      JsonNode typeNode = node.get("type");
      return typeNode != null && typeNode.asString().equals("user");
    }
    catch (JacksonException _)
    {
      return false;
    }
  }

  /**
   * Extracts the text content from a user message JSONL line.
   * <p>
   * Handles two content formats used by Claude Code:
   * <ul>
   *   <li>Plain string: {@code {"content":"text here"}} — used for direct user-typed messages</li>
   *   <li>Array format: {@code {"content":[{"type":"tool_result",...}]}} — used for messages
   *       containing tool results (responses to AskUserQuestion, Read, Bash, etc.)</li>
   * </ul>
   *
   * @param line the raw JSONL line
   * @return the lowercased text content, or empty string if the structure is not recognized
   */
  private String extractUserMessageText(String line)
  {
    JsonMapper mapper = scope.getJsonMapper();
    try
    {
      JsonNode node = mapper.readTree(line);
      if (node == null || !node.isObject())
        return "";
      JsonNode messageNode = node.get("message");
      if (messageNode == null || !messageNode.isObject())
        return "";
      JsonNode contentNode = messageNode.get("content");
      if (contentNode == null)
        return "";
      if (contentNode.isString())
        return contentNode.asString().toLowerCase(Locale.ROOT);
      if (!contentNode.isArray())
        return "";
      StringBuilder text = new StringBuilder();
      for (JsonNode element : contentNode)
      {
        JsonNode textNode = element.get("text");
        if (textNode != null)
        {
          if (!text.isEmpty())
            text.append(' ');
          text.append(textNode.asString());
        }
      }
      return text.toString().toLowerCase(Locale.ROOT);
    }
    catch (JacksonException _)
    {
      return "";
    }
  }

  /**
   * Finds the first user message in recent lines whose content matches the given predicate.
   *
   * @param recentLines the recent JSONL lines from the session file
   * @param contentMatcher a predicate applied to the lowercased text content of each user message
   * @return true if a matching user message is found
   */
  private boolean findUserMessageMatching(List<String> recentLines,
    Predicate<String> contentMatcher)
  {
    for (String line : recentLines)
    {
      if (!isUserMessage(line))
        continue;
      String text = extractUserMessageText(line);
      if (contentMatcher.test(text))
        return true;
    }
    return false;
  }

  /**
   * Checks if recent lines contain a user message that serves as wizard-flow approval.
   * <p>
   * In the AskUserQuestion wizard flow, the user responds to a structured question. Accepted
   * keywords are: "approve", "approved", "yes", or "proceed".
   *
   * @param recentLines the recent lines from the session file
   * @return true if a wizard-flow user approval message is found
   */
  private boolean hasWizardApprovalMessage(List<String> recentLines)
  {
    return findUserMessageMatching(recentLines, text ->
      text.contains("approve") ||
      text.contains("yes") ||
      text.contains("proceed"));
  }

  /**
   * Checks if recent lines contain a direct user message explicitly approving the merge.
   * <p>
   * Only specific approval phrases are recognized (e.g., "approve and merge", "approve merge").
   * This prevents false positives from messages that happen to contain both "approve" and "merge"
   * in unrelated contexts (e.g., "don't approve the merge yet").
   *
   * @param recentLines the recent lines from the session file
   * @return true if a direct user approval message with a recognized phrase is found
   */
  private boolean hasDirectApprovalMessage(List<String> recentLines)
  {
    return findUserMessageMatching(recentLines, text ->
    {
      for (String phrase : APPROVAL_PHRASES)
      {
        if (text.contains(phrase))
          return true;
      }
      return false;
    });
  }
}
