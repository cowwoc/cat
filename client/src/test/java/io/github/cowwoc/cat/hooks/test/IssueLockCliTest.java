/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.IssueLock;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for IssueLock CLI entry point (run() method).
 * <p>
 * Tests verify argument parsing, output format, and exit behavior for all subcommands.
 * Uses injectable streams and scopes to avoid System.exit() calls during tests.
 * All output (success and error) is written to stdout using HookOutput.block() format.
 */
public class IssueLockCliTest
{
  /**
   * Verifies that acquire with valid arguments writes acquired status to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithValidArgsWritesAcquiredToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock.run(new String[]{"acquire", "test-issue", sessionId}, scope, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"acquired\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with missing session ID writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithMissingSessionIdWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(new String[]{"acquire", "test-issue"}, scope, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("Usage");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with an invalid UUID session ID writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithInvalidUuidSessionIdWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(new String[]{"acquire", "test-issue", "not-a-uuid"}, scope, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("UUID");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that release with valid arguments writes released status to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void releaseWithValidArgsWritesReleasedToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "");

        IssueLock.run(new String[]{"release", "test-issue", sessionId}, scope, out);

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
   * Verifies that update with valid arguments writes updated status to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void updateWithValidArgsWritesUpdatedToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "");

        IssueLock.run(new String[]{"update", "test-issue", sessionId, "/new/worktree"}, scope, out);

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
   * Verifies that check with a valid issue returns JSON with "locked" field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkWithValidIssueReturnsLockedFalseJson() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(new String[]{"check", "unlocked-issue"}, scope, out);

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
   * Verifies that list returns a JSON array.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void listCommandReturnsJsonArray() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(new String[]{"list"}, scope, out);

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
   * Verifies that an unknown command writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unknownCommandWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(new String[]{"invalid-command"}, scope, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("Unknown command");
        requireThat(output, "output").contains("invalid-command");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that force-release with valid arguments writes released status to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void forceReleaseWithValidIssueWritesReleasedToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "");

        IssueLock.run(new String[]{"force-release", "test-issue"}, scope, out);

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
   * Verifies that no-args invocation writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noArgsWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(new String[]{}, scope, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("Usage");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }
}
