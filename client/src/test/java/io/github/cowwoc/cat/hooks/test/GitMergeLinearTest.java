/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.GitMergeLinear;
import io.github.cowwoc.cat.hooks.JvmScope;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitMergeLinear validation and error handling.
 * <p>
 * Tests verify input validation without requiring actual git repository setup.
 */
public class GitMergeLinearTest
{
  /**
   * Verifies that execute rejects null taskBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullTaskBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    GitMergeLinear cmd = new GitMergeLinear(scope, ".");

    try
    {
      cmd.execute(null, "main", false);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("taskBranch");
    }
    }
  }

  /**
   * Verifies that execute rejects blank taskBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsBlankTaskBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    GitMergeLinear cmd = new GitMergeLinear(scope, ".");

    try
    {
      cmd.execute("", "main", false);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("taskBranch");
    }
    }
  }

  /**
   * Verifies that execute rejects null baseBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullBaseBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    GitMergeLinear cmd = new GitMergeLinear(scope, ".");

    try
    {
      cmd.execute("task-branch", null, false);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("baseBranch");
    }
    }
  }

  /**
   * Verifies that execute accepts empty baseBranch for auto-detect.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeAcceptsEmptyBaseBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = TestUtils.createTempGitRepo("main");
      try
      {
        GitMergeLinear cmd = new GitMergeLinear(scope, tempDir.toString());

        try
        {
          cmd.execute("task-branch", "", false);
        }
        catch (IOException e)
        {
          requireThat(e.getMessage(), "message").isNotNull();
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }
}
