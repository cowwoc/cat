/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import io.github.cowwoc.cat.agent.AgentScope;
import io.github.cowwoc.cat.claude.hook.prompt.UserIssues;

/**
 * JVM-wide scope providing lazy-loaded singletons and environment configuration.
 * <p>
 * All methods that return objects (paths, singletons, handlers) must always return the same value
 * for the lifetime of the scope. Multiple invocations of the same method must return equal values.
 * <p>
 * <b>Thread Safety:</b> Implementations are thread-safe.
 */
public interface JvmScope extends AgentScope
{
  /**
   * Returns the user issues prompt handler.
   *
   * @return the handler
   * @throws IllegalStateException if this scope is closed
   */
  UserIssues getUserIssues();
}
