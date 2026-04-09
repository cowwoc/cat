/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.util.SkillOutput;

import java.io.IOException;

/**
 * Test SkillOutput that throws IOException from getOutput().
 */
public final class TestSkillOutputThrowsIo implements SkillOutput
{
  /**
   * Creates a TestSkillOutputThrowsIo instance.
   *
   * @param scope the ClaudeTool scope (unused in test)
   */
  public TestSkillOutputThrowsIo(ClaudeTool scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    throw new IOException("simulated IO failure");
  }
}
