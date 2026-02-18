/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.failure;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Detects repeated consecutive tool failures and suggests drift recovery.
 * <p>
 * Tracks consecutive PostToolUseFailure events per session. When 2 or more consecutive failures are
 * detected, injects a system-reminder suggesting {@code /cat:recover-from-drift}.
 * <p>
 * Failure counts are persisted in {@code /tmp/cat-failure-tracking-<sessionId>.count}. Files older than
 * 1 day are cleaned up at most every 6 hours.
 */
public final class DetectRepeatedFailures implements PostToolHandler
{
  private static final int FAILURE_THRESHOLD = 2;
  private static final Duration FILE_TTL = Duration.ofDays(1);
  private static final Duration CLEANUP_INTERVAL = Duration.ofHours(6);
  private static final Path DEFAULT_TRACKING_DIRECTORY = Path.of("/tmp");
  private final Clock clock;
  private final Path trackingDirectory;
  private Instant lastCleanup = Instant.EPOCH;

  /**
   * Creates a new DetectRepeatedFailures handler using the system UTC clock and the default tracking
   * directory ({@code /tmp}).
   */
  public DetectRepeatedFailures()
  {
    this(Clock.systemUTC(), DEFAULT_TRACKING_DIRECTORY);
  }

  /**
   * Creates a new DetectRepeatedFailures handler with the default tracking directory ({@code /tmp}).
   *
   * @param clock the clock to use for time-based operations
   * @throws NullPointerException if {@code clock} is null
   */
  public DetectRepeatedFailures(Clock clock)
  {
    this(clock, DEFAULT_TRACKING_DIRECTORY);
  }

  /**
   * Creates a new DetectRepeatedFailures handler.
   *
   * @param clock             the clock to use for time-based operations
   * @param trackingDirectory the directory where tracking files are stored
   * @throws NullPointerException if {@code clock} or {@code trackingDirectory} are null
   */
  public DetectRepeatedFailures(Clock clock, Path trackingDirectory)
  {
    requireThat(clock, "clock").isNotNull();
    requireThat(trackingDirectory, "trackingDirectory").isNotNull();
    this.clock = clock;
    this.trackingDirectory = trackingDirectory;
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(sessionId, "sessionId").isNotBlank();

    Path trackingFile = trackingDirectory.resolve("cat-failure-tracking-" + sessionId + ".count");

    int failureCount = readFailureCount(trackingFile);
    ++failureCount;
    writeFailureCount(trackingFile, failureCount);

    cleanupOldTrackingFiles();

    if (failureCount >= FAILURE_THRESHOLD)
      return Result.context(buildWarningMessage());
    return Result.allow();
  }

  /**
   * Reads the failure count from the tracking file.
   *
   * @param trackingFile the path to the tracking file
   * @return the current failure count, or 0 if the file does not exist or cannot be read
   */
  private int readFailureCount(Path trackingFile)
  {
    if (!Files.exists(trackingFile))
      return 0;
    try
    {
      String content = Files.readString(trackingFile).strip();
      return Integer.parseInt(content);
    }
    catch (IOException | NumberFormatException _)
    {
      return 0;
    }
  }

  /**
   * Writes the failure count to the tracking file.
   * <p>
   * The file is created with owner-only read/write permissions ({@code rw-------}) if it does not already
   * exist.
   *
   * @param trackingFile the path to the tracking file
   * @param count the failure count to write
   */
  private void writeFailureCount(Path trackingFile, int count)
  {
    try
    {
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
      try
      {
        Files.createFile(trackingFile, PosixFilePermissions.asFileAttribute(perms));
      }
      catch (FileAlreadyExistsException _)
      {
        // File already exists, continue with write
      }
      Files.writeString(trackingFile, String.valueOf(count));
    }
    catch (IOException _)
    {
      // Fail gracefully — tracking is best-effort
    }
  }

  /**
   * Removes tracking files older than the TTL from {@code /tmp}.
   * <p>
   * Cleanup runs at most once every {@link #CLEANUP_INTERVAL} to avoid scanning {@code /tmp} on every
   * invocation.
   */
  private void cleanupOldTrackingFiles()
  {
    Instant now = clock.instant();
    if (now.isBefore(lastCleanup.plus(CLEANUP_INTERVAL)))
      return;
    lastCleanup = now;

    Instant cutoff = now.minus(FILE_TTL);
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(trackingDirectory,
      "cat-failure-tracking-*.count"))
    {
      for (Path p : stream)
        deleteIfExpired(p, cutoff);
    }
    catch (IOException _)
    {
      // Fail gracefully if /tmp is not accessible
    }
  }

  /**
   * Attempts to delete a single tracking file if it is expired and not a symlink.
   *
   * @param file the tracking file to check
   * @param cutoff files modified before this instant are deleted
   */
  private void deleteIfExpired(Path file, Instant cutoff)
  {
    try
    {
      if (Files.isSymbolicLink(file))
        return;
      BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
      if (attrs.lastModifiedTime().toInstant().isBefore(cutoff))
        Files.deleteIfExists(file);
    }
    catch (IOException _)
    {
      // Skip files that cannot be read or deleted
    }
  }

  /**
   * Builds the drift recovery suggestion message.
   *
   * @return the system-reminder message text
   */
  private String buildWarningMessage()
  {
    return """
      🔄 REPEATED TOOL FAILURES DETECTED

      **Failure Count**: 2+ consecutive failures detected.

      **Possible Causes**:
      1. **Goal Drift**: You may be attempting actions not in the current PLAN.md execution step
      2. **Legitimate Error**: The current step has a genuine technical issue

      **RECOMMENDED ACTION**:
      Consider running `/cat:recover-from-drift` to verify you are aligned with the current execution step.

      The recovery skill will:
      - Read the current PLAN.md
      - Identify which step should be active
      - Compare your failing action against the plan
      - Determine if drift has occurred
      - Provide specific guidance on how to proceed

      **Key Principle**: Before repeatedly retrying or generalizing solutions, verify you are working on \
      the correct step.""";
  }
}
