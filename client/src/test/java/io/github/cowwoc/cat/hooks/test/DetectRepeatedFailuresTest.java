/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.failure.DetectRepeatedFailures;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for DetectRepeatedFailures.
 */
public final class DetectRepeatedFailuresTest
{
  /**
   * Verifies that the first failure returns allow (no warning).
   */
  @Test
  public void firstFailureAllowsQuietly() throws IOException
  {
    String sessionId = "test-session-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      DetectRepeatedFailures handler = new DetectRepeatedFailures();

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that the second consecutive failure returns a warning suggesting drift recovery.
   */
  @Test
  public void secondFailureReturnsWarning() throws IOException
  {
    String sessionId = "test-session-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      DetectRepeatedFailures handler = new DetectRepeatedFailures();

      // First failure - no warning
      handler.check("Bash", toolResult, sessionId, hookData);

      // Second failure - should warn
      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").contains("REPEATED TOOL FAILURES DETECTED");
      requireThat(result.additionalContext(), "additionalContext").contains("/cat:recover-from-drift");
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that third and subsequent failures continue returning a warning.
   */
  @Test
  public void thirdFailureContinuesToWarn() throws IOException
  {
    String sessionId = "test-session-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      DetectRepeatedFailures handler = new DetectRepeatedFailures();

      handler.check("Bash", toolResult, sessionId, hookData);
      handler.check("Bash", toolResult, sessionId, hookData);
      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").contains("REPEATED TOOL FAILURES DETECTED");
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that the failure count persists across handler instances for the same session.
   */
  @Test
  public void failureCountPersistsAcrossInstances() throws IOException
  {
    String sessionId = "test-session-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();

      // First instance records one failure
      new DetectRepeatedFailures().check("Bash", toolResult, sessionId, hookData);

      // Second instance should see the persisted count and warn on second failure
      PostToolHandler.Result result = new DetectRepeatedFailures().check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").contains("REPEATED TOOL FAILURES DETECTED");
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that different sessions track failures independently.
   */
  @Test
  public void differentSessionsAreIndependent() throws IOException
  {
    String sessionId1 = "test-session-a-" + System.nanoTime();
    String sessionId2 = "test-session-b-" + System.nanoTime();
    Path trackingFile1 = Path.of("/tmp/cat-failure-tracking-" + sessionId1 + ".count");
    Path trackingFile2 = Path.of("/tmp/cat-failure-tracking-" + sessionId2 + ".count");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      DetectRepeatedFailures handler = new DetectRepeatedFailures();

      // First failure on session 1 - no warning
      PostToolHandler.Result result1 = handler.check("Bash", toolResult, sessionId1, hookData);
      requireThat(result1.warning(), "warning").isEmpty();

      // First failure on session 2 - also no warning
      PostToolHandler.Result result2 = handler.check("Bash", toolResult, sessionId2, hookData);
      requireThat(result2.warning(), "warning").isEmpty();
    }
    finally
    {
      Files.deleteIfExists(trackingFile1);
      Files.deleteIfExists(trackingFile2);
    }
  }

  /**
   * Verifies that the warning includes key drift recovery information.
   */
  @Test
  public void warningContainsDriftRecoveryGuidance() throws IOException
  {
    String sessionId = "test-session-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      DetectRepeatedFailures handler = new DetectRepeatedFailures();

      handler.check("Bash", toolResult, sessionId, hookData);
      PostToolHandler.Result result = handler.check("Read", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").contains("REPEATED TOOL FAILURES DETECTED");
      requireThat(result.additionalContext(), "additionalContext").contains("Goal Drift");
      requireThat(result.additionalContext(), "additionalContext").contains("/cat:recover-from-drift");
      requireThat(result.additionalContext(), "additionalContext").contains("PLAN.md");
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that a blank sessionId throws IllegalArgumentException.
   * <p>
   * Format validation for path traversal characters is centralized in HookInput.getSessionId().
   * The handler's own contract requires a non-blank sessionId.
   */
  @Test
  public void blankSessionIdThrows() throws IOException
  {
    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode toolResult = mapper.createObjectNode();
    JsonNode hookData = mapper.createObjectNode();
    DetectRepeatedFailures handler = new DetectRepeatedFailures();

    try
    {
      handler.check("Bash", toolResult, "", hookData);
      requireThat(false, "check").isEqualTo(true);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("sessionId");
    }
  }

  /**
   * Verifies that a corrupted tracking file (non-numeric content) resets the counter to 0.
   * The first failure after a corrupted file should return allow (count treated as 1, below threshold).
   */
  @Test
  public void corruptedTrackingFileResetsCounter() throws IOException
  {
    String sessionId = "test-session-corrupt-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      Files.writeString(trackingFile, "not-a-number");

      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode toolResult = mapper.createObjectNode();
      JsonNode hookData = mapper.createObjectNode();
      DetectRepeatedFailures handler = new DetectRepeatedFailures();

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that the tracking file is created with owner-only read/write permissions (0600).
   */
  @Test
  public void trackingFileHasOwnerOnlyPermissions() throws IOException
  {
    String sessionId = "test-session-perms-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      DetectRepeatedFailures handler = new DetectRepeatedFailures();
      JsonMapper mapper = JsonMapper.builder().build();
      handler.check("Bash", mapper.createObjectNode(), sessionId, mapper.createObjectNode());

      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(trackingFile);
      requireThat(perms, "perms").containsExactly(Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE));
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that symlink tracking files matching the cleanup glob pattern are skipped (not deleted) during
   * cleanup.
   */
  @Test
  public void symlinkTrackingFilesAreSkippedDuringCleanup() throws IOException
  {
    String sessionId = "test-session-symlink-" + System.nanoTime();
    Path realFile = Files.createTempFile("cat-failure-real-", ".tmp");
    Path symlinkFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      Files.createSymbolicLink(symlinkFile, realFile);

      // Handler should not throw even with symlink in /tmp
      DetectRepeatedFailures handler = new DetectRepeatedFailures();
      JsonMapper mapper = JsonMapper.builder().build();
      String otherSession = "test-session-other-" + System.nanoTime();
      Path otherFile = Path.of("/tmp/cat-failure-tracking-" + otherSession + ".count");
      try
      {
        handler.check("Bash", mapper.createObjectNode(), otherSession, mapper.createObjectNode());
        // Symlink should still exist (not deleted)
        requireThat(Files.exists(symlinkFile, LinkOption.NOFOLLOW_LINKS), "symlinkExists").isTrue();
      }
      finally
      {
        Files.deleteIfExists(otherFile);
      }
    }
    finally
    {
      Files.deleteIfExists(symlinkFile);
      Files.deleteIfExists(realFile);
    }
  }

  /**
   * Verifies that old tracking files are deleted during cleanup when the TTL has expired.
   */
  @Test
  public void expiredTrackingFilesAreDeletedDuringCleanup() throws IOException
  {
    Instant now = Instant.parse("2025-06-01T12:00:00Z");
    String sessionId = "test-session-ttl-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      // Create an old tracking file (simulate 2-day-old file)
      Files.writeString(trackingFile, "5");
      Files.setLastModifiedTime(trackingFile,
        FileTime.from(now.minus(Duration.ofDays(2))));

      // New instance has lastCleanup=EPOCH, so cleanup runs on first check()
      String otherSession = "test-session-trigger-" + System.nanoTime();
      Path otherFile = Path.of("/tmp/cat-failure-tracking-" + otherSession + ".count");
      try
      {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DetectRepeatedFailures handler = new DetectRepeatedFailures(clock);
        JsonMapper mapper = JsonMapper.builder().build();
        handler.check("Bash", mapper.createObjectNode(), otherSession, mapper.createObjectNode());

        // The old file should have been cleaned up
        requireThat(Files.exists(trackingFile), "trackingFileExists").isFalse();
      }
      finally
      {
        Files.deleteIfExists(otherFile);
      }
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that cleanup does not run when still within the cleanup interval.
   */
  @Test
  public void cleanupDoesNotRunWithinInterval() throws IOException
  {
    Instant now = Instant.parse("2025-06-01T12:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    String sessionId = "test-session-interval-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      // First call sets lastCleanup = now (cleanup runs because lastCleanup starts at EPOCH)
      String triggerSession = "test-session-trigger-" + System.nanoTime();
      Path triggerFile = Path.of("/tmp/cat-failure-tracking-" + triggerSession + ".count");
      DetectRepeatedFailures handler = new DetectRepeatedFailures(clock);
      JsonMapper mapper = JsonMapper.builder().build();
      try
      {
        handler.check("Bash", mapper.createObjectNode(), triggerSession, mapper.createObjectNode());
      }
      finally
      {
        Files.deleteIfExists(triggerFile);
      }

      // Now create an old tracking file
      Files.writeString(trackingFile, "5");
      Files.setLastModifiedTime(trackingFile,
        FileTime.from(now.minus(Duration.ofDays(2))));

      // Second call on same handler instance - cleanup should NOT run (now is within 6h of now)
      String otherSession = "test-session-noclean-" + System.nanoTime();
      Path otherFile = Path.of("/tmp/cat-failure-tracking-" + otherSession + ".count");
      try
      {
        handler.check("Bash", mapper.createObjectNode(), otherSession, mapper.createObjectNode());

        // The old file should still exist (cleanup didn't run)
        requireThat(Files.exists(trackingFile), "trackingFileExists").isTrue();
      }
      finally
      {
        Files.deleteIfExists(otherFile);
      }
    }
    finally
    {
      Files.deleteIfExists(trackingFile);
    }
  }

  /**
   * Verifies that an unreadable tracking file resets the counter to 0.
   * When the file cannot be read, the handler treats the count as 0 and increments to 1.
   */
  @Test
  public void unreadableTrackingFileResetsCounter() throws IOException
  {
    String sessionId = "test-session-unreadable-" + System.nanoTime();
    Path trackingFile = Path.of("/tmp/cat-failure-tracking-" + sessionId + ".count");
    try
    {
      // Create file with no read permission
      Files.writeString(trackingFile, "10");
      Set<PosixFilePermission> noRead = Set.of(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(trackingFile, noRead);

      DetectRepeatedFailures handler = new DetectRepeatedFailures();
      JsonMapper mapper = JsonMapper.builder().build();
      // First call should treat as count=0 (can't read), increment to 1
      PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), sessionId,
        mapper.createObjectNode());
      // Count is 1, below threshold of 2 → allow
      requireThat(result.warning(), "warning").isEmpty();
    }
    finally
    {
      // Restore permissions for cleanup
      try
      {
        Files.setPosixFilePermissions(trackingFile,
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      }
      catch (IOException _)
      {
        // Ignore
      }
      Files.deleteIfExists(trackingFile);
    }
  }
}
