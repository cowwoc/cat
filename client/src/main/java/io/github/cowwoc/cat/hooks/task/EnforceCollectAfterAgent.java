/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.task;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.WorktreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Blocks Task and Skill tool calls when a pending-agent-result flag exists.
 * <p>
 * After the main agent completes an Agent tool invocation in a work-with-issue context,
 * {@link io.github.cowwoc.cat.hooks.tool.post.SetPendingAgentResult} creates a flag file at
 * {@code {sessionBasePath}/{sessionId}/pending-agent-result}. This handler blocks all subsequent
 * Task and Skill calls until {@code cat:collect-results-agent} or {@code cat:merge-subagent-agent}
 * is invoked, at which point the flag is deleted and the call is allowed through.
 */
public final class EnforceCollectAfterAgent implements TaskHandler
{
  private final Logger log = LoggerFactory.getLogger(EnforceCollectAfterAgent.class);
  private final JvmScope scope;

  /**
   * Creates a new EnforceCollectAfterAgent handler.
   *
   * @param scope the JVM scope providing the session base path
   * @throws NullPointerException if {@code scope} is null
   */
  public EnforceCollectAfterAgent(JvmScope scope)
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

    Path flagPath = scope.getSessionBasePath().resolve(sessionId).resolve("pending-agent-result");
    if (!Files.exists(flagPath))
      return Result.allow();

    if (WorktreeContext.forSession(
      scope.getProjectCatDir(), scope.getClaudeProjectDir(), scope.getJsonMapper(), sessionId) == null)
    {
      // No active worktree lock — flag is stale; clean up and allow
      try
      {
        Files.deleteIfExists(flagPath);
      }
      catch (IOException e)
      {
        log.warn("EnforceCollectAfterAgent: failed to delete stale flag file {}: {}", flagPath,
          e.getMessage());
      }
      return Result.allow();
    }

    // Read skill and subagent_type from tool input
    JsonNode skillNode = toolInput.get("skill");
    String skill;
    if (skillNode != null)
      skill = skillNode.asString();
    else
      skill = "";

    JsonNode subagentTypeNode = toolInput.get("subagent_type");
    String subagentType;
    if (subagentTypeNode != null)
      subagentType = subagentTypeNode.asString();
    else
      subagentType = "";

    // Allow collect-results-agent and merge-subagent-agent, clear flag
    if (skill.equals("cat:collect-results-agent") || skill.equals("cat:merge-subagent-agent"))
    {
      try
      {
        Files.deleteIfExists(flagPath);
      }
      catch (IOException e)
      {
        log.warn("EnforceCollectAfterAgent: failed to delete flag file {}: {}", flagPath, e.getMessage());
      }
      return Result.allow();
    }

    // Determine what was attempted for the error message
    String attemptedTool;
    if (!skill.isEmpty())
      attemptedTool = "Skill (" + skill + ")";
    else if (!subagentType.isEmpty())
      attemptedTool = "Task (" + subagentType + ")";
    else
      attemptedTool = "Task or Skill (no skill/subagent_type)";

    String reason = """
      BLOCKED: Agent tool result has not been processed.

      The previous Agent tool invocation completed but collect-results-agent was not called.

      Required next step: Invoke collect-results-agent before any other Task or Skill call.

      Correct invocation:
        Skill tool: skill="cat:collect-results-agent"
        Arguments: "<cat_agent_id> <issue_path> <subagent_commits_json>"

      Where <cat_agent_id> = {CLAUDE_SESSION_ID}/subagents/{rawAgentId}
        (rawAgentId is the agentId: value from the Agent tool result footer)

      See plugin/skills/collect-results-agent/SKILL.md for argument details.

      Attempted tool: %s""".formatted(attemptedTool);

    return Result.block(reason);
  }
}
