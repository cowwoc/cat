/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.FileUtils;
import io.github.cowwoc.cat.hooks.util.GetSkill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes a single agent's loaded marker directory so that skills and files reload with full content.
 * <p>
 * The {@code loaded/} directory tracks which skills and files have been loaded by each agent.
 * Deleting the directory forces a fresh load on the next invocation. Marker paths:
 * <ul>
 *   <li>Main agent: {@code {sessionBasePath}/{sessionId}/loaded/}</li>
 *   <li>Subagent: {@code {sessionBasePath}/{sessionId}/subagents/{agentId}/loaded/}</li>
 * </ul>
 * <p>
 * Called by {@code SessionStartHook} for the main agent (on session startup and after context
 * compaction) and by {@code SubagentStartHook} for individual subagents.
 *
 * @see io.github.cowwoc.cat.hooks.util.GetSkill
 * @see io.github.cowwoc.cat.hooks.util.GetFile
 */
public final class ClearAgentMarkers
{
  private final JvmScope scope;

  /**
   * Creates a new ClearAgentMarkers.
   *
   * @param scope the JVM scope providing the session base path
   * @throws NullPointerException if {@code scope} is null
   */
  public ClearAgentMarkers(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Deletes the main agent's loaded marker directory for the current session.
   *
   * @param sessionId the session ID
   * @return a warning message if deletion fails, or an empty string on success
   * @throws NullPointerException     if {@code sessionId} is null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  public String clearMainAgentMarker(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    Path baseDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize();
    Path sessionDir = GetSkill.resolveAndValidateContainment(baseDir, sessionId, "sessionId");
    if (!Files.isDirectory(sessionDir))
      return "";
    return deleteLoadedDir(sessionDir.resolve(GetSkill.LOADED_DIR));
  }

  /**
   * Deletes the loaded marker directory for a specific subagent.
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
    Path baseDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize();
    String subagentPath = sessionId + "/" + GetSkill.SUBAGENTS_DIR + "/" + agentId;
    Path agentDir = GetSkill.resolveAndValidateContainment(baseDir, subagentPath,
      "sessionId or agentId");
    return deleteLoadedDir(agentDir.resolve(GetSkill.LOADED_DIR));
  }

  /**
   * Deletes the loaded directory and all its contents, returning a warning if deletion fails.
   *
   * @param loadedDir the path to the loaded directory to delete
   * @return a warning message if deletion fails, or an empty string on success
   */
  private static String deleteLoadedDir(Path loadedDir)
  {
    if (!Files.isDirectory(loadedDir))
      return "";
    List<IOException> failures = new ArrayList<>();
    FileUtils.deleteDirectoryRecursively(loadedDir, failures);
    if (failures.isEmpty())
      return "";
    return "Warning: Failed to delete loaded directory: " + loadedDir + ": " +
      failures.getFirst().getMessage();
  }
}
