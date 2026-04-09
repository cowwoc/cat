/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.ReadSessionMarker;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link ReadSessionMarker}.
 * <p>
 * Each test is self-contained with no shared state, safe for parallel execution.
 */
public class ReadSessionMarkerTest
{
  /**
   * Verifies that the marker file content is returned correctly on a successful invocation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readsMarkerFileContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path markerFile = tempDir.resolve(".cat/work/sessions/session-abc123/squash-complete-2.1-fix-foo");
      Files.createDirectories(markerFile.getParent());
      Files.writeString(markerFile, "squashed:abc123def");

      String result = new ReadSessionMarker(scope).getOutput(
        new String[]{"session-abc123", "squash-complete-2.1-fix-foo"});
      requireThat(result, "result").isEqualTo("squashed:abc123def");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a {@link NoSuchFileException} is thrown when the marker file does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NoSuchFileException.class)
  public void throwsWhenFileAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new ReadSessionMarker(scope).getOutput(
        new String[]{"session-abc123", "squash-complete-missing-issue"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that passing a number of arguments other than 2 throws an {@link IllegalArgumentException}
   * mentioning "2 arguments".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*2 arguments.*")
  public void rejectsWhenArgCountIsNotTwo() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new ReadSessionMarker(scope).getOutput(new String[]{"session-id", "marker-name", "extra"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blank session-id throws an {@link IllegalArgumentException} mentioning both
   * "session-id" and "blank".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*session-id)(?=.*blank).*")
  public void rejectsBlankSessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new ReadSessionMarker(scope).getOutput(new String[]{"", "squash-complete-issue"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blank marker-name throws an {@link IllegalArgumentException} mentioning both
   * "marker-name" and "blank".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*marker-name)(?=.*blank).*")
  public void rejectsBlankMarkerName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new ReadSessionMarker(scope).getOutput(new String[]{"session-id", ""});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a whitespace-only session-id throws an {@link IllegalArgumentException} mentioning
   * both "session-id" and "blank", confirming that {@code isBlank()} semantics are enforced rather
   * than {@code isEmpty()}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*session-id)(?=.*blank).*")
  public void rejectsWhitespaceOnlySessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new ReadSessionMarker(scope).getOutput(new String[]{"   ", "squash-complete-issue"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a whitespace-only marker-name throws an {@link IllegalArgumentException} mentioning
   * both "marker-name" and "blank", confirming that {@code isBlank()} semantics are enforced rather
   * than {@code isEmpty()}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*marker-name)(?=.*blank).*")
  public void rejectsWhitespaceOnlyMarkerName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("read-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new ReadSessionMarker(scope).getOutput(new String[]{"session-id", "   "});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
