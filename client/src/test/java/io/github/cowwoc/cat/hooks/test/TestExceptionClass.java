/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

/**
 * Test class for preprocessor directive exception handling.
 * <p>
 * Throws an exception from main() to test error handling.
 */
public final class TestExceptionClass
{
  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   * @throws IllegalStateException for test purposes
   */
  public static void main(String[] args)
  {
    throw new IllegalStateException("Test exception message");
  }
}
