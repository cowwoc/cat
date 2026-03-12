/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.tool.post;

import static io.github.cowwoc.cat.hooks.Strings.WORK_EXECUTE_SUBAGENT_TYPE;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.WorktreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sets a pending-agent-result flag file when the main agent completes an Agent tool invocation
 * in a work-with-issue context.
 * <p>
 * When the main agent (not a subagent) completes an Agent tool invocation with
 * {@code subagent_type: cat:work-execute} and an active worktree lock exists for the session,
 * this handler creates a flag file at
 * {@code {sessionBasePath}/{sessionId}/pending-agent-result}. The flag signals that
 * {@code collect-results-agent} must be invoked before any subsequent Task or Skill call.
 * <p>
 * Agent invocations with any other {@code subagent_type} (e.g. adversarial agents) do not
 * produce worktree artifacts and are skipped without creating the flag.
 * <p>
 * This handler always returns {@link Result#allow()} — it never blocks the Agent tool itself.
 * Failures during flag file creation are logged but do not propagate.
 */
public final class SetPendingAgentResult implements PostToolHandler
{
  private final Logger log = LoggerFactory.getLogger(SetPendingAgentResult.class);
  private final JvmScope scope;

  /**
   * Creates a new SetPendingAgentResult handler.
   *
   * @param scope the JVM scope providing project directory and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public SetPendingAgentResult(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(sessionId, "sessionId").isNotBlank();

    try
    {
      // Only applies to the Agent tool
      if (!toolName.equalsIgnoreCase("Agent"))
        return Result.allow();

      // Only applies to the main agent (not subagents spawning subagents)
      JsonNode agentIdNode = null;
      if (hookData != null)
        agentIdNode = hookData.get("agent_id");
      String agentId;
      if (agentIdNode != null)
        agentId = agentIdNode.asString();
      else
        agentId = "";
      // agentId is used only as a presence check (non-empty = subagent); no path construction, so no injection risk
      if (!agentId.isEmpty())
        return Result.allow();

      // Only enforce collect-results gate for cat:work-execute subagents.
      // Check subagent_type BEFORE the expensive WorktreeContext lookup.
      // Other agent types (adversarial, red-team, blue-team, diff-validation) produce no worktree artifacts.
      JsonNode toolInputNode = null;
      if (hookData != null)
        toolInputNode = hookData.get("tool_input");
      boolean isWorkExecute = false;
      if (toolInputNode != null)
      {
        JsonNode subagentTypeNode = toolInputNode.get("subagent_type");
        if (subagentTypeNode != null && subagentTypeNode.isString())
          isWorkExecute = WORK_EXECUTE_SUBAGENT_TYPE.equalsIgnoreCase(subagentTypeNode.asString());
      }
      if (!isWorkExecute)
        return Result.allow();

      // Only applies when an active worktree lock exists (work-with-issue context)
      WorktreeContext context = WorktreeContext.forSession(
        scope.getProjectCatDir(), scope.getClaudeProjectDir(), scope.getJsonMapper(), sessionId);
      if (context == null)
        return Result.allow();

      // Create the flag file
      Path flagPath = scope.getSessionBasePath().resolve(sessionId).resolve("pending-agent-result");
      Files.createDirectories(flagPath.getParent());
      Files.writeString(flagPath, "");
    }
    catch (IOException e)
    {
      log.warn("SetPendingAgentResult: failed to write flag file for session {}: {}", sessionId,
        e.getMessage());
    }
    catch (RuntimeException e)
    {
      log.error("SetPendingAgentResult: unexpected error writing flag file for session {}: {}", sessionId,
        e.getMessage());
    }

    return Result.allow();
  }
}
