/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.task;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.Config;
import io.github.cowwoc.cat.agent.TrustLevel;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.TaskHandler;
import io.github.cowwoc.cat.tool.util.SessionFileUtils;
import io.github.cowwoc.cat.tool.util.StructuredMergeApproval;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Block work-merge invocations when trust=medium/low without explicit user approval.
 * <p>
 * This handler enforces the trust-based approval requirement:
 * <ul>
 *   <li>trust=high: No approval needed (skip this check)</li>
 *   <li>trust=medium/low: MUST have explicit user approval before merge</li>
 * </ul>
 * <p>
 * Prevention: Blocks Task tool (subagent_type=cat:work-merge) and Skill tool
 * (skill=cat:work-merge) invocations without prior approval.
 */
public final class EnforceApprovalBeforeMerge implements TaskHandler
{
  /**
   * Number of recent JSONL lines to scan for approval messages. 75 lines covers approximately the last
   * several user interactions, which is sufficient to detect recent approval without scanning the entire
   * session file.
   */
  private static final int RECENT_LINES_TO_SCAN = 75;

  private final ClaudeHook scope;

  /**
   * Creates a new EnforceApprovalBeforeMerge handler.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public EnforceApprovalBeforeMerge(ClaudeHook scope)
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

    // Detect work-merge via Task tool (subagent_type) or Skill tool (skill)
    JsonNode subagentTypeNode = toolInput.get("subagent_type");
    String subagentType;
    if (subagentTypeNode != null)
      subagentType = subagentTypeNode.asString();
    else
      subagentType = "";

    JsonNode skillNode = toolInput.get("skill");
    String skill;
    if (skillNode != null)
      skill = skillNode.asString();
    else
      skill = "";

    if (!subagentType.equals("cat:work-merge") && !skill.equals("cat:work-merge"))
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

    Path sessionFile = scope.getClaudeSessionsPath().resolve(sessionId + ".jsonl");

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
                    "Requirement: Explicit user approval via AskUserQuestion\n" +
                    "\n" +
                    "BLOCKING: No approval detected in session history.\n" +
                    "\n" +
                    "Approval must be given through the AskUserQuestion wizard by selecting " +
                    "\"Approve and merge\" from the approval gate options.\n" +
                    "\n" +
                    "Do NOT proceed to merge based on:\n" +
                    "- Direct chat messages such as \"approve and merge\"\n" +
                    "- Silence or lack of objection\n" +
                    "- System reminders or notifications\n" +
                    "- Assumed approval\n" +
                    "\n" +
                    "Fail-fast principle: Unknown consent = No consent = STOP";

    return Result.block(reason);
  }

  /**
   * Get the trust level from config.json.
   *
   * @return the trust level
   * @throws IOException if the config file cannot be read or contains invalid JSON
   */
  private TrustLevel getTrustLevel() throws IOException
  {
    Config config = Config.load(scope.getJsonMapper(), scope.getProjectPath());
    return config.getTrust();
  }

  /**
   * Check if explicit approval is found in the session file.
   * <p>
   * Approval is detected only from the AskUserQuestion wizard flow. Plain chat messages such as
   * "approve and merge" are intentionally ignored.
   *
   * @param sessionFile the session JSONL file
   * @return true if approval found
   */
  private boolean checkApprovalInSession(Path sessionFile)
  {
    try
    {
      List<String> recentLines = SessionFileUtils.getRecentLines(sessionFile, RECENT_LINES_TO_SCAN);
      return StructuredMergeApproval.isPresent(scope.getJsonMapper(), recentLines);
    }
    catch (IOException _)
    {
      // Cannot read session file
    }
    return false;
  }
}
