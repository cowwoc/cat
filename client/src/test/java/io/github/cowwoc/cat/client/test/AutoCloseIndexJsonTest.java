/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.util.AutoCloseIndexJson;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tests for {@link AutoCloseIndexJson}.
 * <p>
 * Each test is self-contained with no shared state, safe for parallel execution.
 */
public class AutoCloseIndexJsonTest
{
  /**
   * Verifies that a branch not following the CAT naming convention returns {@code index_updated: false}
   * without touching any files.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonCatBranchReturnsNotUpdated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("auto-close-index-test");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      String result = new AutoCloseIndexJson(scope).getOutput(
        new String[]{tempDir.toString(), "main"});
      requireThat(result, "result").contains("\"index_updated\": false");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the {@code index.json} file is absent, the tool returns
   * {@code index_updated: false} without error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void absentIndexJsonReturnsNotUpdated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("auto-close-index-test");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Branch follows CAT naming but index.json does not exist
      String result = new AutoCloseIndexJson(scope).getOutput(
        new String[]{tempDir.toString(), "2.1-my-issue"});
      requireThat(result, "result").contains("\"index_updated\": false");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the {@code index.json} already has {@code "status": "closed"}, the tool
   * returns {@code index_updated: false} and leaves the file unchanged.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void alreadyClosedReturnsNotUpdated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("auto-close-index-test");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path indexJson = tempDir.resolve(".cat/issues/v2/v2.1/my-issue/index.json");
      Files.createDirectories(indexJson.getParent());
      Files.writeString(indexJson, """
        {
          "status": "closed",
          "title": "My Issue"
        }
        """, UTF_8);

      String result = new AutoCloseIndexJson(scope).getOutput(
        new String[]{tempDir.toString(), "2.1-my-issue"});
      requireThat(result, "result").contains("\"index_updated\": false");

      // File must be unchanged
      String afterContent = Files.readString(indexJson, UTF_8);
      requireThat(afterContent, "afterContent").contains("\"status\": \"closed\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the {@code index.json} has a non-closed status, the tool updates it to
   * {@code "closed"}, returns {@code index_updated: true}, and includes the {@code index_path}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void updatesStatusFromInProgressToClosed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("auto-close-index-test");
    try
    {
      // Create a real git repo so git add can run
      Path repoDir = TestUtils.createTempGitRepo("2.1-my-issue");
      try (JvmScope scope = new TestClaudeTool(repoDir, repoDir))
      {
        Path indexJson = repoDir.resolve(".cat/issues/v2/v2.1/my-issue/index.json");
        Files.createDirectories(indexJson.getParent());
        Files.writeString(indexJson, """
          {
            "status": "in-progress",
            "title": "My Issue"
          }
          """, UTF_8);

        String result = new AutoCloseIndexJson(scope).getOutput(
          new String[]{repoDir.toString(), "2.1-my-issue"});

        requireThat(result, "result").contains("\"index_updated\": true");
        requireThat(result, "result").contains("index_path");
        requireThat(result, "result").contains("my-issue/index.json");

        // Verify file was actually updated
        String afterContent = Files.readString(indexJson, UTF_8);
        requireThat(afterContent, "afterContent").contains("\"status\" : \"closed\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(repoDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that passing a number of arguments other than 2 throws an
   * {@link IllegalArgumentException} mentioning "2 arguments".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*2 arguments.*")
  public void rejectsWhenArgCountIsNotTwo() throws IOException
  {
    Path tempDir = Files.createTempDirectory("auto-close-index-test");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      new AutoCloseIndexJson(scope).getOutput(new String[]{"/tmp/worktree"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blank worktree_path throws an {@link IllegalArgumentException} mentioning
   * both "worktree_path" and "blank".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*worktree_path)(?=.*blank).*")
  public void rejectsBlankWorktreePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("auto-close-index-test");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      new AutoCloseIndexJson(scope).getOutput(new String[]{"", "2.1-my-issue"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blank branch throws an {@link IllegalArgumentException} mentioning
   * both "branch" and "blank".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*branch)(?=.*blank).*")
  public void rejectsBlankBranch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("auto-close-index-test");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      new AutoCloseIndexJson(scope).getOutput(new String[]{tempDir.toString(), ""});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
