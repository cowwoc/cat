// Copyright (c) 2026 Gili Tzabari. All rights reserved.
//
// Licensed under the CAT Commercial License.
// See LICENSE.md in the project root for license terms.
package io.github.cowwoc.cat.hooks.failure;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Resets the consecutive failure counter after a successful tool execution.
 * <p>
 * On each successful PostToolUse event, deletes the tracking file
 * ({@code cat-failure-tracking-<sessionId>.count}) to reset the consecutive failure count tracked by
 * {@link DetectRepeatedFailures}.
 */
public final class ResetFailureCounter implements PostToolHandler
{
  private final Path trackingDirectory;

  /**
   * Creates a new ResetFailureCounter handler.
   *
   * @param trackingDirectory the directory where tracking files are stored
   * @throws NullPointerException if {@code trackingDirectory} is null
   */
  public ResetFailureCounter(Path trackingDirectory)
  {
    requireThat(trackingDirectory, "trackingDirectory").isNotNull();
    this.trackingDirectory = trackingDirectory;
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(sessionId, "sessionId").isNotBlank();

    Path trackingFile = trackingDirectory.resolve("cat-failure-tracking-" + sessionId + ".count");
    try
    {
      Files.deleteIfExists(trackingFile);
    }
    catch (IOException _)
    {
      // Fail gracefully — tracking is best-effort
    }
    return Result.allow();
  }
}
