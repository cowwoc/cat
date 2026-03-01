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
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*sourceBranch.*")
  public void executeRejectsNullSourceBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute(null, "main");
    }
  }

  /**
   * Verifies that execute rejects blank sourceBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sourceBranch.*")
  public void executeRejectsBlankSourceBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute("", "main");
    }
  }

  /**
   * Verifies that execute rejects null targetBranch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void executeRejectsNullTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute("task-branch", null);
    }
  }

  /**
   * Verifies that execute rejects blank targetBranch.
   * <p>
   * The target branch is required — there is no auto-detect fallback.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void executeRejectsBlankTargetBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitMergeLinear cmd = new GitMergeLinear(scope, ".");

      cmd.execute("task-branch", "");
    }
  }
}
