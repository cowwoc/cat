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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cleans up accumulated skill marker files from previous sessions.
 * <p>
 * Removes {@code /tmp/cat-skills-loaded-*} files that track which skills have been
 * loaded in each session. These files accumulate over time as sessions start and end.
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
   * Deletes skill marker files from /tmp.
   *
   * @param input the hook input (not used, but required by interface)
   * @return an empty result (silent operation)
   * @throws NullPointerException if input is null
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    Path tmpDir = Path.of("/tmp");
    if (!Files.isDirectory(tmpDir))
      return Result.empty();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(tmpDir, "cat-skills-loaded-*"))
    {
      for (Path path : stream)
      {
        try
        {
          if (Files.isSymbolicLink(path))
            continue;
          Files.deleteIfExists(path);
        }
        catch (IOException _)
        {
          // Silently ignore deletion failures
        }
      }
    }
    catch (IOException _)
    {
      // Silently ignore directory stream failures
    }
    return Result.empty();
  }
}
