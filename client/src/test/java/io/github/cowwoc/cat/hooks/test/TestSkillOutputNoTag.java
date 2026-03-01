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
 * Test implementation of SkillOutput that returns plain text without an {@code <output>} tag wrapper.
 * <p>
 * Used to verify that preprocessor directives returning untagged content fall into the
 * "content without tags" path in SkillLoader.load(), treating all content as instructions.
 */
public final class TestSkillOutputNoTag implements SkillOutput
{
  /**
   * Creates a TestSkillOutputNoTag instance.
   *
   * @param scope the JVM scope (unused in test)
   */
  public TestSkillOutputNoTag(JvmScope scope)
  {
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    return "plain-text-no-output-tag";
  }
}
