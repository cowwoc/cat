/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.util.List;

/**
 * Provides cross-module access to package-private constructors of {@link PostToolUseHook}.
 * <p>
 * Part of the SharedSecrets mechanism that enables tests to inject custom handler lists without using
 * reflection.
 */
@FunctionalInterface
public interface PostToolUseHookAccess
{
  /**
   * Creates a new PostToolUseHook with the specified handlers.
   *
   * @param handlers the handlers to use
   * @return a new PostToolUseHook
   * @throws NullPointerException if {@code handlers} is null
   */
  PostToolUseHook newPostToolUseHook(List<PostToolHandler> handlers);
}
