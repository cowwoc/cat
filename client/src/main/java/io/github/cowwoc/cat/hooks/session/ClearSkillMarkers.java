/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deletes the session skill marker file so that skills reload with full content.
 * <p>
 * The marker file {@code /tmp/cat-skills-loaded-{sessionId}} tracks which skills have been
 * loaded in the current session. Deleting it forces a fresh skill load on the next invocation.
 * <p>
 * This handler is registered for {@code SessionStart} so skills load fresh at session start. The normal
 * SessionStart chain also fires after context compaction, ensuring skills reload.
 *
 * @see io.github.cowwoc.cat.hooks.util.SkillLoader
 */
public final class ClearSkillMarkers implements SessionStartHandler
{
  /**
   * Creates a new ClearSkillMarkers handler.
   */
  public ClearSkillMarkers()
  {
  }

  /**
   * Deletes the current session's skill marker file from /tmp.
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
    Path markerFile = Path.of("/tmp/cat-skills-loaded-" + sessionId);
    if (Files.isSymbolicLink(markerFile))
      return Result.empty();
    try
    {
      Files.deleteIfExists(markerFile);
    }
    catch (IOException _)
    {
      // Silently ignore deletion failures
    }
    return Result.empty();
  }
}
