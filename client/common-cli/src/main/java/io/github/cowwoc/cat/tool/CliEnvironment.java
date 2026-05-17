/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool;

import java.util.function.Function;

/**
 * Environment variable helpers for CLI scopes.
 */
public final class CliEnvironment
{
  /**
   * Prevent construction.
   */
  private CliEnvironment()
  {
  }

  /**
   * Reads a required environment variable.
   *
   * @param environment resolves environment variable names to values
   * @param name the environment variable name
   * @return the non-blank value
   * @throws AssertionError if the variable is unset or blank
   */
  public static String required(Function<String, String> environment, String name)
  {
    String value = environment.apply(name);
    if (value != null && !value.isBlank())
      return value;
    throw new AssertionError(name + " is required and must not be blank");
  }

  /**
   * Reads an optional environment variable.
   *
   * @param environment resolves environment variable names to values
   * @param name the environment variable name
   * @param defaultValue the value to return when the environment variable is unset
   * @return the configured value, or {@code defaultValue}
   */
  public static String optional(Function<String, String> environment, String name,
    String defaultValue)
  {
    String value = environment.apply(name);
    if (value == null)
      return defaultValue;
    return value;
  }
}
