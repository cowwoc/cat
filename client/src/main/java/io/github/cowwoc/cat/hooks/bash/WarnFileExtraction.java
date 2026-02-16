/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import tools.jackson.databind.JsonNode;

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
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    // Check for tar/unzip extraction
    if (EXTRACTION_PATTERN.matcher(command).find())
      // Just a mild warning, don't block
      return Result.warn("File extraction detected. Ensure destination directory is appropriate.");

    return Result.allow();
  }
}
