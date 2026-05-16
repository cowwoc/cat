/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.SkillOutput;

import java.io.IOException;

/**
 * Test implementation of SkillOutput with a CliTool constructor.
 * <p>
 * Used to verify that invokeSkillOutput() correctly handles SkillOutput classes
 * whose constructor takes CliTool rather than JvmScope.
 */
public final class TestClaudeToolSkillOutput implements SkillOutput
{
  /**
   * Creates a TestClaudeToolSkillOutput instance.
   *
   * @param scope the CliTool scope (unused in test)
   */
  public TestClaudeToolSkillOutput(CliTool scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    return "CLAUDE_TOOL_OUTPUT";
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    System.out.print("CLAUDE_TOOL_OUTPUT");
  }
}
