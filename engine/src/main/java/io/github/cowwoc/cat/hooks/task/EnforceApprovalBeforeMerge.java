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
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Block work-merge subagent spawn when trust=medium/low without explicit user approval (M479/M480).
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
  public Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    JsonNode subagentTypeNode = toolInput.get("subagent_type");
    String subagentType;
    if (subagentTypeNode != null)
      subagentType = subagentTypeNode.asString();
    else
      subagentType = "";

    if (!subagentType.equals("cat:work-merge"))
      return Result.allow();

    String trust = getTrustLevel();

    if (trust.equals("high"))
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

    Path sessionFile = Paths.get(System.getProperty("user.home"),
      ".config/claude/projects/-workspace", sessionId + ".jsonl");

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

    String reason = "FAIL: Explicit user approval required before merge (M479/M480)\n" +
                    "\n" +
                    "Trust level: " + trust + "\n" +
                    "Requirement: Explicit user approval via AskUserQuestion\n" +
                    "\n" +
                    "BLOCKING: No approval detected in session history.\n" +
                    "\n" +
                    "The approval gate (Step 6 in work-with-issue) MUST:\n" +
                    "1. Present task summary and review results\n" +
                    "2. Use AskUserQuestion with \"Approve and merge\" option\n" +
                    "3. Wait for explicit user selection\n" +
                    "4. Only proceed to merge AFTER user selects approval\n" +
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
   * @return the trust level ("high", "medium", or "low")
   */
  private String getTrustLevel()
  {
    Config config = Config.load(scope.getJsonMapper(), scope.getClaudeProjectDir());
    return config.getString("trust", "medium");
  }

  /**
   * Check if explicit approval is found in the session file.
   *
   * @param sessionFile the session JSONL file
   * @return true if approval found
   */
  private boolean checkApprovalInSession(Path sessionFile)
  {
    try
    {
      List<String> recentLines = SessionFileUtils.getRecentLines(sessionFile, 50);
      String recentContent = String.join("\n", recentLines);
      String lowerContent = recentContent.toLowerCase(Locale.ROOT);

      boolean hasAskQuestion = lowerContent.contains("askuserquestion") &&
                               lowerContent.contains("approve");
      boolean hasApproval = lowerContent.contains("user_approval") ||
                            hasUserApprovalMessage(recentLines);
      if (hasAskQuestion && hasApproval)
        return true;
    }
    catch (IOException _)
    {
      // Cannot read session file
    }

    return false;
  }

  /**
   * Check if recent lines contain a user approval message.
   *
   * @param recentLines the recent lines from the session file
   * @return true if user approval message found
   */
  private boolean hasUserApprovalMessage(List<String> recentLines)
  {
    for (String line : recentLines)
    {
      String lowerLine = line.toLowerCase(Locale.ROOT);
      boolean isUserMessage = lowerLine.contains("\"type\":\"user\"") ||
                              lowerLine.contains("\"type\": \"user\"");
      if (isUserMessage)
      {
        boolean hasApproval = lowerLine.contains("approve") ||
                              lowerLine.contains("yes") ||
                              lowerLine.contains("proceed") ||
                              lowerLine.contains("merge");
        if (hasApproval)
          return true;
      }
    }
    return false;
  }
}
