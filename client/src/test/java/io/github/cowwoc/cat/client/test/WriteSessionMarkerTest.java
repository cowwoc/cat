/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

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
    try
    {
      new WriteSessionMarker().getOutput(new String[]{tempDir.toString(), "2.1-fix-foo", "squashed:abc123def"});

      Path markerFile = tempDir.resolve(".cat/work/markers/2.1-fix-foo");
      requireThat(Files.exists(markerFile), "markerFileExists").isTrue();
      requireThat(Files.readString(markerFile), "markerContent").isEqualTo("squashed:abc123def");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the markers directory is created if it does not yet exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void createsMarkersDirWhenAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("write-session-marker-test");
    try
    {
      Path markersDir = tempDir.resolve(".cat/work/markers");
      requireThat(Files.notExists(markersDir), "markersDirAbsent").isTrue();

      new WriteSessionMarker().getOutput(new String[]{tempDir.toString(), "2.1-fix-foo", "squashed:abc123def"});

      requireThat(Files.isDirectory(markersDir), "markersDirCreated").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that passing a number of arguments other than 3 throws an {@link IllegalArgumentException}
   * mentioning "3 arguments".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*3 arguments.*")
  public void rejectsWhenArgCountIsNotThree() throws IOException
  {
    new WriteSessionMarker().getOutput(new String[]{"worktree-path", "issue-id"});
  }

  /**
   * Verifies that a blank worktree-path throws an {@link IllegalArgumentException} mentioning both
   * "worktree-path" and "blank".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*worktree-path)(?=.*blank).*")
  public void rejectsBlankWorktreePath() throws IOException
  {
    new WriteSessionMarker().getOutput(new String[]{"", "issue-id", "content"});
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
    new WriteSessionMarker().getOutput(new String[]{"worktree-path", "", "content"});
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
    try
    {
      new WriteSessionMarker().getOutput(new String[]{tempDir.toString(), "issue-id", ""});

      Path markerFile = tempDir.resolve(".cat/work/markers/issue-id");
      requireThat(Files.exists(markerFile), "markerFileExists").isTrue();
      requireThat(Files.readString(markerFile).isEmpty(), "markerContentIsEmpty").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
