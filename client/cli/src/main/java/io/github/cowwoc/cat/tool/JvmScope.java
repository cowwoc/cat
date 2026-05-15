/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import io.github.cowwoc.cat.agent.AgentScope;

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
}
