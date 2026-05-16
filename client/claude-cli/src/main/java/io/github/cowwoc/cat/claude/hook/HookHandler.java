/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

/**
 * A handler that processes hook input and produces hook output.
 */
@FunctionalInterface
public interface HookHandler
{
  /**
   * Processes hook data and returns the result with any warnings.
   *
   * @return the hook result containing JSON output and warnings
   */
  HookResult run();
}
