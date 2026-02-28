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

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitMergeLinear validation and error handling.
 * <p>
 * Tests verify input validation without requiring actual git repository setup.
 */
public class GitMergeLinearTest
{
  /**
   * Verifies that execute rejects null sourceBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullSourceBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      try
      {
        cmd.execute(null, "main");
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("sourceBranch");
      }
    }
  }

  /**
   * Verifies that execute rejects blank sourceBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsBlankSourceBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      try
      {
        cmd.execute("", "main");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("sourceBranch");
      }
    }
  }

  /**
   * Verifies that execute rejects null targetBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      try
      {
        cmd.execute("task-branch", null);
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("targetBranch");
      }
    }
  }

  /**
   * Verifies that execute rejects blank targetBranch.
   * <p>
   * The target branch is required — there is no auto-detect fallback.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsBlankTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      try
      {
        cmd.execute("task-branch", "");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("targetBranch");
      }
    }
  }
}
