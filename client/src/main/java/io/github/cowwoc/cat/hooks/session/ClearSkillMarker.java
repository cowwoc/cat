/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.SkillLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deletes a single agent's skill marker file so that skills reload with full content.
 * <p>
 * Marker files track which skills have been loaded by each agent. Deleting a marker forces a fresh
 * skill load on the next invocation. Marker paths:
 * <ul>
 *   <li>Main agent: {@code {configDir}/projects/-workspace/{sessionId}/skills-loaded}</li>
 *   <li>Subagent: {@code {configDir}/projects/-workspace/{sessionId}/subagents/{agentId}/skills-loaded}</li>
 * </ul>
 * <p>
 * Called by {@code SessionStartHook} for the main agent (on session startup and after context
 * compaction) and by {@code SubagentStartHook} for individual subagents.
 *
 * @see io.github.cowwoc.cat.hooks.util.SkillLoader
 */
public final class ClearSkillMarker
{
  private final JvmScope scope;

  /**
   * Creates a new ClearSkillMarker.
   *
   * @param scope the JVM scope providing the config directory path
   * @throws NullPointerException if {@code scope} is null
   */
  public ClearSkillMarker(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Deletes the main agent's skill marker file for the current session.
   *
   * @param sessionId the session ID
   * @return a warning message if deletion fails, or an empty string on success
   * @throws NullPointerException     if {@code sessionId} is null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  public String clearMainAgentMarker(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    Path baseDir = scope.getClaudeConfigDir().resolve(SkillLoader.PROJECTS_DIR).toAbsolutePath().normalize();
    Path sessionDir = SkillLoader.resolveAndValidateContainment(baseDir, sessionId, "sessionId");
    if (!Files.isDirectory(sessionDir))
      return "";
    return deleteMarker(sessionDir.resolve("skills-loaded"));
  }

  /**
   * Deletes the skill marker file for a specific subagent.
   *
   * @param sessionId the session ID
   * @param agentId   the subagent's native agent ID (not the composite ID)
   * @return a warning message if deletion fails, or an empty string on success
   * @throws NullPointerException     if {@code sessionId} or {@code agentId} are null
   * @throws IllegalArgumentException if {@code sessionId} or {@code agentId} are blank
   */
  public String clearSubagentMarker(String sessionId, String agentId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(agentId, "agentId").isNotBlank();
    Path baseDir = scope.getClaudeConfigDir().resolve(SkillLoader.PROJECTS_DIR).toAbsolutePath().normalize();
    String subagentPath = sessionId + "/" + SkillLoader.SUBAGENTS_DIR + "/" + agentId;
    Path markerFile = SkillLoader.resolveAndValidateContainment(baseDir, subagentPath,
      "sessionId or agentId").resolve("skills-loaded");
    return deleteMarker(markerFile);
  }

  /**
   * Deletes a marker file and returns a warning if deletion fails.
   *
   * @param markerFile the path to the marker file to delete
   * @return a warning message if deletion fails, or an empty string on success
   */
  private static String deleteMarker(Path markerFile)
  {
    try
    {
      Files.deleteIfExists(markerFile);
    }
    catch (IOException e)
    {
      return "Warning: Failed to delete skill marker file: " + markerFile + ": " + e.getMessage();
    }
    return "";
  }
}
