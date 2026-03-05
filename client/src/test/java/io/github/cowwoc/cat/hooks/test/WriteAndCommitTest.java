/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.WriteAndCommit;
import io.github.cowwoc.cat.hooks.JvmScope;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Tests for WriteAndCommit validation and error handling.
 * <p>
 * Tests verify input validation and error paths without requiring
 * actual git repository setup.
 */
public class WriteAndCommitTest
{
  /**
   * Verifies that execute rejects null filePath.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*filePath.*")
  public void executeRejectsNullFilePath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = TestUtils.createTempDir("write-and-commit-test");
    try
    {
      Path contentFile = tempDir.resolve("content.txt");
      Path commitMsgFile = tempDir.resolve("commit.txt");
      Files.writeString(contentFile, "test content");
      Files.writeString(commitMsgFile, "test commit");

      WriteAndCommit cmd = new WriteAndCommit(scope);

      cmd.execute(null, contentFile.toString(), commitMsgFile.toString(), false);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects blank filePath.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*filePath.*")
  public void executeRejectsBlankFilePath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = TestUtils.createTempDir("write-and-commit-test");
    try
    {
      Path contentFile = tempDir.resolve("content.txt");
      Path commitMsgFile = tempDir.resolve("commit.txt");
      Files.writeString(contentFile, "test content");
      Files.writeString(commitMsgFile, "test commit");

      WriteAndCommit cmd = new WriteAndCommit(scope);

      cmd.execute("", contentFile.toString(), commitMsgFile.toString(), false);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects missing content file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Content file not found.*")
  public void executeRejectsMissingContentFile() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = TestUtils.createTempDir("write-and-commit-test");
    try
    {
      Path commitMsgFile = tempDir.resolve("commit.txt");
      Files.writeString(commitMsgFile, "test commit");

      WriteAndCommit cmd = new WriteAndCommit(scope);

      cmd.execute("test.txt", tempDir.resolve("missing.txt").toString(),
        commitMsgFile.toString(), false);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects missing commit message file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Commit message file not found.*")
  public void executeRejectsMissingCommitMsgFile() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = TestUtils.createTempDir("write-and-commit-test");
    try
    {
      Path contentFile = tempDir.resolve("content.txt");
      Files.writeString(contentFile, "test content");

      WriteAndCommit cmd = new WriteAndCommit(scope);

      cmd.execute("test.txt", contentFile.toString(),
        tempDir.resolve("missing.txt").toString(), false);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }
}
