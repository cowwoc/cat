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
 * Test SkillOutput that throws RuntimeException with null message from getOutput().
 */
public final class TestSkillOutputThrowsNullMessage implements SkillOutput
{
  /**
   * Creates a TestSkillOutputThrowsNullMessage instance.
   *
   * @param scope the JVM scope (unused in test)
   */
  public TestSkillOutputThrowsNullMessage(JvmScope scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    throw new IllegalStateException((String) null);
  }
}
