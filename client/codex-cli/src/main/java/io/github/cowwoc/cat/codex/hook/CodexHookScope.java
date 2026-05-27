/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import io.github.cowwoc.cat.agent.AgentPluginScope;

import java.io.InputStream;

/**
 * Codex hook invocation scope.
 */
public interface CodexHookScope extends AgentPluginScope
{
  /**
   * Returns standard input for this hook invocation.
   *
   * @return the hook input stream
   * @throws IllegalStateException if this scope is closed
   */
  InputStream getHookInput();
}
