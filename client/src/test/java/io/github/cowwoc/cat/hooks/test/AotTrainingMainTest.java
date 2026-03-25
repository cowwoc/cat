/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.AotTraining;
import io.github.cowwoc.cat.hooks.AbstractClaudeHook;
import org.testng.annotations.Test;

/**
 * Tests for {@link AotTraining#run(AbstractClaudeHook)} CLI error path handling.
 */
public class AotTrainingMainTest
{
  /**
   * Verifies that run() throws NullPointerException for null scope.
   *
   * @throws Exception if an unexpected error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void nullScopeThrowsException() throws Exception
  {
    AotTraining.run(null);
  }
}
