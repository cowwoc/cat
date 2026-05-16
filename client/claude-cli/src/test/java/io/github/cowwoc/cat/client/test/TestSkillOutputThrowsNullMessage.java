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
 * Test SkillOutput that throws RuntimeException with null message from getOutput().
 */
public final class TestSkillOutputThrowsNullMessage implements SkillOutput
{
  /**
   * Creates a TestSkillOutputThrowsNullMessage instance.
   *
   * @param scope the CliTool scope (unused in test)
   */
  public TestSkillOutputThrowsNullMessage(CliTool scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    throw new IllegalStateException((String) null);
  }
}
