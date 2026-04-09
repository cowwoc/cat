/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;

import java.util.regex.Pattern;

/**
 * Warn about extracting large files.
 */
public final class WarnFileExtraction implements BashHandler
{
  private static final Pattern EXTRACTION_PATTERN =
    Pattern.compile("(tar\\s+.*-?x|unzip|gunzip)");

  /**
   * Creates a new handler for warning about file extraction.
   */
  public WarnFileExtraction()
  {
    // Handler class
  }

  @Override
  public Result check(ClaudeHook scope)
  {
    String command = scope.getCommand();

    // Check for tar/unzip extraction
    if (EXTRACTION_PATTERN.matcher(command).find())
      // Just a mild warning, don't block
      return Result.warn(
        "File extraction detected. Verify the destination directory before proceeding:\n" +
        "\n" +
        "- Extract to a temp directory (e.g., mktemp -d) rather than the project root\n" +
        "- Confirm the archive does not overwrite tracked files in the worktree\n" +
        "- If extracting to the worktree, ensure the files are intentional additions\n" +
        "\n" +
        "Proceed only if the extraction destination is appropriate for the current task.");

    return Result.allow();
  }
}
