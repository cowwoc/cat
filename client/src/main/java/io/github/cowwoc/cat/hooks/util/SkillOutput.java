/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import java.io.IOException;

/**
 * Interface for classes that generate skill output for preprocessor directives.
 */
public interface SkillOutput
{
  /**
   * Generates the output for this skill.
   *
   * @param args the arguments from the preprocessor directive
   * @return the generated output
   * @throws NullPointerException if {@code args} is null
   * @throws IOException if an I/O error occurs
   */
  String getOutput(String[] args) throws IOException;
}
