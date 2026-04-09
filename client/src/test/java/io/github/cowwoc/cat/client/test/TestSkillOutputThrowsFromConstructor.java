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
 * Test SkillOutput whose constructor throws RuntimeException, which triggers InvocationTargetException.
 */
public final class TestSkillOutputThrowsFromConstructor implements SkillOutput
{
  /**
   * Creates a TestSkillOutputThrowsFromConstructor instance.
   *
   * @param scope the ClaudeTool scope (unused in test)
   * @throws IllegalStateException always, to simulate constructor failure
   */
  public TestSkillOutputThrowsFromConstructor(ClaudeTool scope)
  {
    throw new IllegalStateException("constructor failure");
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    return "";
  }
}
