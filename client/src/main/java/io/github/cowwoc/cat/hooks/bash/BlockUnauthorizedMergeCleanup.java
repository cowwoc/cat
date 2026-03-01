/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;
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
 * Block direct invocations of the merge-and-cleanup binary when trust=medium/low without explicit
 * user approval.
 * <p>
 * Agents that skip the work-with-issue approval gate (Step 8) and invoke the merge-and-cleanup binary
 * directly via Bash tool bypass the Task-tool-level enforcement in EnforceApprovalBeforeMerge. This
 * handler closes that bypass route by enforcing the same approval check at the Bash tool level.
 */
public final class BlockUnauthorizedMergeCleanup implements BashHandler
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
   * Creates a new handler for blocking unauthorized merge-and-cleanup invocations.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public BlockUnauthorizedMergeCleanup(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    requireThat(command, "command").isNotNull();

    // Only intercept commands that invoke the merge-and-cleanup binary
    if (!command.contains("merge-and-cleanup"))
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

    if (sessionId == null || sessionId.isBlank())
    {
      return Result.block("""
        FAIL: Cannot verify user approval - session ID not available.

        Trust level requires explicit approval before merge.

        BLOCKING: This merge attempt is blocked until user approval can be verified.""");
    }

    Path sessionFile = scope.getSessionBasePath().resolve(sessionId + ".jsonl");

    if (!Files.exists(sessionFile))
    {
      return Result.block("""
        FAIL: Cannot verify user approval - session file not found.

        Trust level requires explicit approval before merge.

        BLOCKING: This merge attempt is blocked until user approval can be verified.""");
    }

    if (checkApprovalInSession(sessionFile))
      return Result.allow();

    return Result.block("""
      FAIL: Explicit user approval required before merge

      Invoking merge-and-cleanup directly bypasses the Step 8 Approval Gate in work-with-issue.

      BLOCKING: No approval detected in session history.

      The correct merge path is:
      1. Complete Step 8 (Approval Gate) in work-with-issue - present AskUserQuestion to the user
      2. After user selects "Approve and merge", invoke merge via Task tool (subagent_type: cat:work-merge)
         or Skill tool (skill: cat:work-merge)

      Do NOT invoke merge-and-cleanup directly via Bash - this bypasses the approval gate.

      Fail-fast principle: Unknown consent = No consent = STOP""");
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
        // "text" is used by regular user messages; "content" is used by tool_result entries
        // (e.g., AskUserQuestion wizard responses). Both must be checked.
        JsonNode valueNode = element.get("text");
        if (valueNode == null)
          valueNode = element.get("content");
        if (valueNode != null && valueNode.isString())
        {
          if (!text.isEmpty())
            text.append(' ');
          text.append(valueNode.asString());
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
