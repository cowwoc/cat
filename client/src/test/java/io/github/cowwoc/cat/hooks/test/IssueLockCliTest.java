/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for IssueLock CLI entry point (run() method).
 * <p>
 * Tests verify argument parsing, output format, and exit behavior for all subcommands.
 * Uses injectable streams and scopes to avoid System.exit() calls during tests.
 */
public class IssueLockCliTest
{
  /**
   * Verifies that acquire with valid arguments writes acquired status to stdout and returns true.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithValidArgsWritesAcquiredToStdout() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        boolean success = IssueLock.run(new String[]{"acquire", "test-issue", sessionId},
          scope, out, err);

        requireThat(success, "success").isTrue();
        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"acquired\"");
        String errOutput = errBytes.toString(StandardCharsets.UTF_8);
        requireThat(errOutput, "errOutput").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with missing session ID writes error to stderr and returns false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithMissingSessionIdWritesErrorToStderr() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = IssueLock.run(new String[]{"acquire", "test-issue"}, scope, out, err);

        requireThat(success, "success").isFalse();
        String errOutput = errBytes.toString(StandardCharsets.UTF_8);
        requireThat(errOutput, "errOutput").contains("error");
        requireThat(errOutput, "errOutput").contains("Usage");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with an invalid UUID session ID writes error mentioning UUID and returns false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithInvalidUuidSessionIdWritesUuidErrorToStderr() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = IssueLock.run(new String[]{"acquire", "test-issue", "not-a-uuid"},
          scope, out, err);

        requireThat(success, "success").isFalse();
        String errOutput = errBytes.toString(StandardCharsets.UTF_8);
        requireThat(errOutput, "errOutput").contains("UUID");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that release with valid arguments writes released status to stdout and returns true.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void releaseWithValidArgsWritesReleasedToStdout() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "");

        boolean success = IssueLock.run(new String[]{"release", "test-issue", sessionId}, scope, out,
          err);

        requireThat(success, "success").isTrue();
        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"released\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that update with valid arguments writes updated status to stdout and returns true.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void updateWithValidArgsWritesUpdatedToStdout() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "");

        boolean success = IssueLock.run(new String[]{"update", "test-issue", sessionId, "/new/worktree"}, scope,
          out, err);

        requireThat(success, "success").isTrue();
        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"updated\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that check with a valid issue returns JSON with "locked" field and returns true.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkWithValidIssueReturnsLockedFalseJson() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = IssueLock.run(new String[]{"check", "unlocked-issue"}, scope, out, err);

        requireThat(success, "success").isTrue();
        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"locked\"");
        requireThat(output, "output").contains("false");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that list returns a JSON array and returns true.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void listCommandReturnsJsonArray() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = IssueLock.run(new String[]{"list"}, scope, out, err);

        requireThat(success, "success").isTrue();
        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("[");
        requireThat(output, "output").contains("]");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that an unknown command writes error to stderr and returns false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unknownCommandWritesErrorToStderr() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = IssueLock.run(new String[]{"invalid-command"}, scope, out, err);

        requireThat(success, "success").isFalse();
        String errOutput = errBytes.toString(StandardCharsets.UTF_8);
        requireThat(errOutput, "errOutput").contains("Unknown command");
        requireThat(errOutput, "errOutput").contains("invalid-command");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that force-release with valid arguments writes released status to stdout and returns true.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void forceReleaseWithValidIssueWritesReleasedToStdout() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "");

        boolean success = IssueLock.run(new String[]{"force-release", "test-issue"}, scope, out, err);

        requireThat(success, "success").isTrue();
        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"released\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that no-args invocation writes usage error to stderr and returns false.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noArgsWritesUsageErrorToStderr() throws IOException
  {
    Path tempDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        boolean success = IssueLock.run(new String[]{}, scope, out, err);

        requireThat(success, "success").isFalse();
        String errOutput = errBytes.toString(StandardCharsets.UTF_8);
        requireThat(errOutput, "errOutput").contains("error");
        requireThat(errOutput, "errOutput").contains("Usage");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Creates a temporary CAT project directory for test isolation.
   *
   * @return the path to the created temporary directory
   */
  private Path createTempProject()
  {
    try
    {
      Path tempDir = Files.createTempDirectory("issue-lock-cli-test");
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);
      return tempDir;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}
