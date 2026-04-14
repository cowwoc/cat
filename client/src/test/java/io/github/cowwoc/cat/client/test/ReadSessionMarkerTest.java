/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.ReadSessionMarker;
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
    try
    {
      Path markerFile = tempDir.resolve(".cat/work/markers/2.1-fix-foo");
      Files.createDirectories(markerFile.getParent());
      Files.writeString(markerFile, "squashed:abc123def");

      String result = new ReadSessionMarker().getOutput(new String[]{tempDir.toString(), "2.1-fix-foo"});
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
    try
    {
      new ReadSessionMarker().getOutput(new String[]{tempDir.toString(), "missing-issue"});
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
    new ReadSessionMarker().getOutput(new String[]{"worktree-path", "issue-id", "extra"});
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
    new ReadSessionMarker().getOutput(new String[]{"", "issue-id"});
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
    new ReadSessionMarker().getOutput(new String[]{"worktree-path", ""});
  }

  /**
   * Verifies that a whitespace-only worktree-path throws an {@link IllegalArgumentException} mentioning
   * both "worktree-path" and "blank", confirming that {@code isBlank()} semantics are enforced rather
   * than {@code isEmpty()}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*worktree-path)(?=.*blank).*")
  public void rejectsWhitespaceOnlyWorktreePath() throws IOException
  {
    new ReadSessionMarker().getOutput(new String[]{"   ", "issue-id"});
  }

  /**
   * Verifies that a whitespace-only issue-id throws an {@link IllegalArgumentException} mentioning
   * both "issue-id" and "blank", confirming that {@code isBlank()} semantics are enforced rather
   * than {@code isEmpty()}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*issue-id)(?=.*blank).*")
  public void rejectsWhitespaceOnlyIssueId() throws IOException
  {
    new ReadSessionMarker().getOutput(new String[]{"worktree-path", "   "});
  }
}
