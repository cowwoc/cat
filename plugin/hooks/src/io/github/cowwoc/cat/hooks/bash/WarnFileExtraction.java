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
  @SuppressWarnings("UnusedVariable")
  public Result check(String command, JsonNode _toolInput, JsonNode _toolResult, String _sessionId)
  {
    // Check for tar/unzip extraction
    if (EXTRACTION_PATTERN.matcher(command).find())
      // Just a mild warning, don't block
      return Result.warn("File extraction detected. Ensure destination directory is appropriate.");

    return Result.allow();
  }
}
