/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes the session skill marker files so that skills reload with full content.
 * <p>
 * Marker files {@code skills-loaded-{catAgentId}} are stored under
 * {@code {configDir}/projects/-workspace/{sessionId}/} and track which skills have been
 * loaded by each agent in the current session. Deleting them forces a fresh skill load on
 * the next invocation.
 * <p>
 * This handler is registered for {@code SessionStart} so skills load fresh at session start. The normal
 * SessionStart chain also fires after context compaction, ensuring skills reload.
 *
 * @see io.github.cowwoc.cat.hooks.util.SkillLoader
 */
public final class ClearSkillMarkers implements SessionStartHandler
{
  private final JvmScope scope;

  /**
   * Creates a new ClearSkillMarkers handler.
   *
   * @param scope the JVM scope providing the config directory path
   * @throws NullPointerException if {@code scope} is null
   */
  public ClearSkillMarkers(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Deletes all skill marker files for the current session from the session directory.
   *
   * @param input the hook input containing the {@code session_id} field
   * @return an empty result (silent operation)
   * @throws NullPointerException if {@code input} is null
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    String sessionId = input.getSessionId();
    if (sessionId.isEmpty())
      return Result.empty();
    Path sessionDir = scope.getClaudeConfigDir().resolve("projects/-workspace/" + sessionId);
    if (!Files.isDirectory(sessionDir))
      return Result.empty();
    List<String> warnings = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionDir, "skills-loaded-*"))
    {
      for (Path markerFile : stream)
      {
        if (Files.isSymbolicLink(markerFile))
          continue;
        try
        {
          Files.deleteIfExists(markerFile);
        }
        catch (IOException e)
        {
          warnings.add("Warning: Failed to delete skill marker file: " + markerFile + ": " + e.getMessage());
        }
      }
    }
    catch (IOException e)
    {
      warnings.add("Warning: Failed to list skill marker files in: " + sessionDir + ": " + e.getMessage());
    }
    if (warnings.isEmpty())
      return Result.empty();
    return Result.stderr(String.join("\n", warnings));
  }
}
