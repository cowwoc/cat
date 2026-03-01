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
 * Test implementation of SkillOutput that returns content wrapped in an {@code <output>} tag.
 * <p>
 * Used to verify that preprocessor-generated {@code <output>} tags are detected correctly by
 * SkillLoader.load().
 */
public final class TestSkillOutputWithTag implements SkillOutput
{
  /**
   * Creates a TestSkillOutputWithTag instance.
   *
   * @param scope the JVM scope (unused in test)
   */
  public TestSkillOutputWithTag(JvmScope scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    return "<output type=\"tag\">preprocessor-generated content</output>";
  }
}
