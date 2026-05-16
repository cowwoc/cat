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
 * Test SkillOutput whose constructor throws RuntimeException, which triggers InvocationTargetException.
 */
public final class TestSkillOutputThrowsFromConstructor implements SkillOutput
{
  /**
   * Creates a TestSkillOutputThrowsFromConstructor instance.
   *
   * @param scope the CliTool scope (unused in test)
   * @throws IllegalStateException always, to simulate constructor failure
   */
  public TestSkillOutputThrowsFromConstructor(CliTool scope)
  {
    throw new IllegalStateException("constructor failure");
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    return "";
  }
}
