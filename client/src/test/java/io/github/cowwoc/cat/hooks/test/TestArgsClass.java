/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import java.util.StringJoiner;

/**
 * Test class for preprocessor directive argument testing.
 * <p>
 * Outputs arguments joined with pipe separator.
 */
public final class TestArgsClass
{
  /**
   * Main entry point.
   *
   * @param args command line arguments to echo
   */
  public static void main(String[] args)
  {
    StringJoiner joiner = new StringJoiner("|");
    for (String arg : args)
      joiner.add(arg);
    System.out.print(joiner);
    System.out.flush();
  }
}
