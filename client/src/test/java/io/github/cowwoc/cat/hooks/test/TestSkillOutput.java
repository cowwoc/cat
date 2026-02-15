/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import java.io.IOException;

/**
 * Test implementation of SkillOutput for testing preprocessor directives.
 */
public final class TestSkillOutput implements SkillOutput
{
  /**
   * Creates a TestSkillOutput instance.
   *
   * @param scope the JVM scope (unused in test)
   */
  public TestSkillOutput(JvmScope scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    if (args.length == 0)
      return "NO_ARGS_OUTPUT";
    return "ARGS:" + String.join(",", args);
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    System.out.print(args.length == 0 ? "NO_ARGS_OUTPUT" : "ARGS:" + String.join(",", args));
  }
}
