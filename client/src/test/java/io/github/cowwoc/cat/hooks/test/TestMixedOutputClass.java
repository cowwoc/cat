/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

/**
 * Test class for preprocessor directive mixed output testing.
 * <p>
 * Outputs to both stdout and stderr to test stream capture.
 */
public final class TestMixedOutputClass
{
  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    System.out.print("STDOUT");
    System.out.flush();
    System.err.print("STDERR");
    System.err.flush();
  }
}
