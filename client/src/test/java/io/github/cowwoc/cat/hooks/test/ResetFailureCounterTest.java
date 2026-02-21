// Copyright (c) 2026 Gili Tzabari. All rights reserved.
//
// Licensed under the CAT Commercial License.
// See LICENSE.md in the project root for license terms.
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.failure.ResetFailureCounter;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ResetFailureCounter.
 */
public final class ResetFailureCounterTest
{
  /**
   * Verifies that the tracking file is deleted when it exists.
   */
  @Test
  public void deletesTrackingFileWhenPresent() throws IOException
  {
    Path trackingDirectory = Files.createTempDirectory("cat-reset-test-");
    try
    {
      String sessionId = "test-session-reset-" + System.nanoTime();
      Path trackingFile = trackingDirectory.resolve("cat-failure-tracking-" + sessionId + ".count");
      Files.writeString(trackingFile, "3");

      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      ResetFailureCounter handler = new ResetFailureCounter(trackingDirectory);

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(Files.exists(trackingFile), "trackingFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(trackingDirectory);
    }
  }

  /**
   * Verifies that the handler succeeds gracefully when the tracking file does not exist.
   */
  @Test
  public void succeedsWhenTrackingFileAbsent() throws IOException
  {
    Path trackingDirectory = Files.createTempDirectory("cat-reset-test-");
    try
    {
      String sessionId = "test-session-absent-" + System.nanoTime();

      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      ResetFailureCounter handler = new ResetFailureCounter(trackingDirectory);

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(trackingDirectory);
    }
  }

  /**
   * Verifies that the handler returns allow even when the tracking file cannot be deleted due to permission
   * errors.
   */
  @Test
  public void returnsAllowWhenDeleteFails() throws IOException
  {
    Path trackingDirectory = Files.createTempDirectory("cat-reset-test-");
    try
    {
      String sessionId = "test-session-nodelperm-" + System.nanoTime();
      Path trackingFile = trackingDirectory.resolve("cat-failure-tracking-" + sessionId + ".count");
      Files.writeString(trackingFile, "2");

      // Remove write permission on the directory so delete fails
      Set<PosixFilePermission> noWrite = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_EXECUTE);
      Files.setPosixFilePermissions(trackingDirectory, noWrite);

      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      ResetFailureCounter handler = new ResetFailureCounter(trackingDirectory);

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      // Restore permissions before cleanup
      try
      {
        Files.setPosixFilePermissions(trackingDirectory, Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE));
      }
      catch (IOException _)
      {
        // Ignore
      }
      TestUtils.deleteDirectoryRecursively(trackingDirectory);
    }
  }

  /**
   * Verifies that a blank sessionId throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void blankSessionIdThrows() throws IOException
  {
    Path trackingDirectory = Files.createTempDirectory("cat-reset-test-");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      ResetFailureCounter handler = new ResetFailureCounter(trackingDirectory);

      handler.check("Bash", toolResult, "", hookData);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(trackingDirectory);
    }
  }

  /**
   * Verifies that the handler returns allow (no warning, no additionalContext).
   */
  @Test
  public void alwaysReturnsAllow() throws IOException
  {
    Path trackingDirectory = Files.createTempDirectory("cat-reset-test-");
    try
    {
      String sessionId = "test-session-allow-" + System.nanoTime();

      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      ResetFailureCounter handler = new ResetFailureCounter(trackingDirectory);

      PostToolHandler.Result result = handler.check("Read", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(trackingDirectory);
    }
  }
}
