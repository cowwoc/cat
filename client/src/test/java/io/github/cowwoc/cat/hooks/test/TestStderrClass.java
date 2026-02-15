/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

/**
 * Test class for preprocessor directive stderr output.
 * <p>
 * Outputs error message to stderr without calling System.exit().
 */
public final class TestStderrClass
{
  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    System.err.print("Error occurred");
    System.err.flush();
  }
}
