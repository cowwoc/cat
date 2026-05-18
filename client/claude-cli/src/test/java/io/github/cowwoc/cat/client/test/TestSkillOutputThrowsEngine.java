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
 * Test SkillOutput that throws RuntimeException from getOutput().
 */
public final class TestSkillOutputThrowsEngine implements SkillOutput
{
  /**
   * Creates a TestSkillOutputThrowsEngine instance.
   *
   * @param scope the CliTool scope (unused in test)
   */
  public TestSkillOutputThrowsEngine(CliTool scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    throw new IllegalStateException("simulated engine failure");
  }
}
