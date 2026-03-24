/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.bash.WarnFileExtraction;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link WarnFileExtraction}.
 * <p>
 * Tests verify that file extraction commands (tar -x, unzip, gunzip) produce a warning,
 * while non-extraction commands (ls, tar -c, tar -t) are allowed through without warning.
 */
public final class WarnFileExtractionTest
{
  private static final String SESSION_ID = "00000000-0000-0000-0000-000000000000";
  private static final String WORKING_DIR = "/tmp";

  /**
   * Verifies that {@code tar -xzf archive.tar.gz} triggers a warn.
   * <p>
   * The {@code -x} flag signals extraction, which must produce a warning.
   *
   * @throws IOException if JSON construction fails
   */
  @Test
  public void tarExtractionCommandIsWarned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      WarnFileExtraction handler = new WarnFileExtraction();

      BashHandler.Result result = handler.check(TestUtils.bashHook("tar -xzf archive.tar.gz",
        WORKING_DIR, SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("File extraction detected");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code tar -tzf archive.tar.gz} (list only) does NOT trigger a warn.
   * <p>
   * The {@code -t} flag lists archive contents without extracting — no extraction occurs.
   *
   * @throws IOException if JSON construction fails
   */
  @Test
  public void tarListCommandIsNotWarned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      WarnFileExtraction handler = new WarnFileExtraction();

      BashHandler.Result result = handler.check(TestUtils.bashHook("tar -tzf archive.tar.gz",
        WORKING_DIR, SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code unzip archive.zip} triggers a warn.
   * <p>
   * The {@code unzip} command is an extraction command and must produce a warning.
   *
   * @throws IOException if JSON construction fails
   */
  @Test
  public void unzipCommandIsWarned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      WarnFileExtraction handler = new WarnFileExtraction();

      BashHandler.Result result = handler.check(TestUtils.bashHook("unzip archive.zip",
        WORKING_DIR, SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("File extraction detected");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code gunzip file.gz} triggers a warn.
   * <p>
   * The {@code gunzip} command extracts a gzip file and must produce a warning.
   *
   * @throws IOException if JSON construction fails
   */
  @Test
  public void gunzipCommandIsWarned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      WarnFileExtraction handler = new WarnFileExtraction();

      BashHandler.Result result = handler.check(TestUtils.bashHook("gunzip file.gz",
        WORKING_DIR, SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
      requireThat(result.reason(), "reason").contains("File extraction detected");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a non-extraction command {@code ls -la} is allowed without warning.
   * <p>
   * Listing directory contents has nothing to do with file extraction.
   *
   * @throws IOException if JSON construction fails
   */
  @Test
  public void nonExtractionCommandIsNotWarned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      WarnFileExtraction handler = new WarnFileExtraction();

      BashHandler.Result result = handler.check(TestUtils.bashHook("ls -la", WORKING_DIR, SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code tar -czf backup.tar.gz .} (archive creation) does NOT trigger a warn.
   * <p>
   * The {@code -c} flag creates an archive rather than extracting, so no warning is expected.
   *
   * @throws IOException if JSON construction fails
   */
  @Test
  public void tarCreateCommandIsNotWarned() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      WarnFileExtraction handler = new WarnFileExtraction();

      BashHandler.Result result = handler.check(TestUtils.bashHook("tar -czf backup.tar.gz .",
        WORKING_DIR, SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
