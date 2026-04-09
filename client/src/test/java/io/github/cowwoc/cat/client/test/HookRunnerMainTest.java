/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookRunner;
import org.testng.annotations.Test;

/**
 * Tests for {@link HookRunner#execute(HookRunner.HookHandlerFactory, String[])} CLI entry point.
 */
public class HookRunnerMainTest
{
  /**
   * Verifies that execute() throws NullPointerException for null factory.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*factory.*")
  public void nullFactoryThrowsException()
  {
    HookRunner.execute(null, new String[]{});
  }
}
