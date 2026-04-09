/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.util.WriteSessionMarker;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link WriteSessionMarker}.
 * <p>
 * Each test is self-contained with no shared state, safe for parallel execution.
 */
public class WriteSessionMarkerTest
{
  /**
   * Verifies that the marker file is created with the correct content on a successful invocation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void writesMarkerFileWithCorrectContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("write-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new WriteSessionMarker(scope).getOutput(new String[]{"session-abc123", "2.1-fix-foo", "squashed:abc123def"});

      Path markerFile = tempDir.resolve(".cat/work/sessions/session-abc123/squash-complete-2.1-fix-foo");
      requireThat(Files.exists(markerFile), "markerFileExists").isTrue();
      requireThat(Files.readString(markerFile), "markerContent").isEqualTo("squashed:abc123def");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the session directory is created if it does not yet exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void createsSessionDirectoryWhenAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("write-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sessionDir = tempDir.resolve(".cat/work/sessions/session-abc123");
      requireThat(Files.notExists(sessionDir), "sessionDirAbsent").isTrue();

      new WriteSessionMarker(scope).getOutput(new String[]{"session-abc123", "2.1-fix-foo", "squashed:abc123def"});

      requireThat(Files.isDirectory(sessionDir), "sessionDirCreated").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that passing fewer than 3 arguments throws an {@link IllegalArgumentException} mentioning
   * "3 arguments".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*3 arguments.*")
  public void rejectsWhenArgCountIsNotThree() throws IOException
  {
    try (ClaudeTool scope = new TestClaudeTool())
    {
      new WriteSessionMarker(scope).getOutput(new String[]{"session-id", "issue-id"});
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
    try (ClaudeTool scope = new TestClaudeTool())
    {
      new WriteSessionMarker(scope).getOutput(new String[]{"", "issue-id", "content"});
    }
  }

  /**
   * Verifies that a blank issue-id throws an {@link IllegalArgumentException} mentioning both
   * "issue-id" and "blank".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*issue-id)(?=.*blank).*")
  public void rejectsBlankIssueId() throws IOException
  {
    try (ClaudeTool scope = new TestClaudeTool())
    {
      new WriteSessionMarker(scope).getOutput(new String[]{"session-id", "", "content"});
    }
  }

  /**
   * Verifies that an empty string is accepted as valid marker content, and the resulting marker file
   * exists with empty content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsEmptyMarkerContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("write-session-marker-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      new WriteSessionMarker(scope).getOutput(new String[]{"session-id", "issue-id", ""});

      Path markerFile = tempDir.resolve(".cat/work/sessions/session-id/squash-complete-issue-id");
      requireThat(Files.exists(markerFile), "markerFileExists").isTrue();
      requireThat(Files.readString(markerFile).isEmpty(), "markerContentIsEmpty").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
