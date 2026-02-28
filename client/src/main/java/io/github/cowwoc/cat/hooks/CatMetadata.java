/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

/**
 * Constants for CAT metadata file names stored in git directories.
 */
public final class CatMetadata
{
  /**
   * The name of the file that stores the fork-point commit hash in a worktree's git directory.
   * <p>
   * This file is created by {@code /cat:work} when setting up an issue worktree and contains a 40-character
   * hex commit hash representing the exact commit the issue branch was forked from.
   */
  public static final String BRANCH_POINT_FILE = "cat-branch-point";

  /**
   * Creates a new CatMetadata instance.
   */
  private CatMetadata()
  {
  }
}
