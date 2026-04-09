/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import static io.github.cowwoc.cat.claude.hook.Strings.block;

import io.github.cowwoc.cat.claude.hook.JvmScope;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Utility methods for parsing command-line arguments.
 */
public final class CliArgs
{
  /**
   * Prevents instantiation.
   */
  private CliArgs()
  {
  }

  /**
   * Validates that a flag has a corresponding value argument.
   *
   * @param flagIndex the index of the flag argument in {@code args}
   * @param args the command-line arguments array
   * @param flagName the name of the flag (e.g., "--max-files")
   * @throws IllegalArgumentException if the flag lacks a value argument
   */
  public static void requiredValue(int flagIndex, String[] args, String flagName)
  {
    if (flagIndex + 1 >= args.length)
      throw new IllegalArgumentException("Missing value for " + flagName);
  }

  /**
   * Parses an integer value for a command-line flag.
   *
   * @param flagName the name of the flag (e.g., "--max-files")
   * @param value the string value to parse
   * @return the parsed integer
   * @throws IllegalArgumentException if {@code value} is not a valid integer
   */
  public static int requiredInt(String flagName, String value)
  {
    try
    {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException _)
    {
      throw new IllegalArgumentException("Error: " + flagName + " requires an integer, got: " + value);
    }
  }

  /**
   * Reads an integer flag value, writing a block response and returning empty if parsing fails.
   *
   * @param flagIndex the index of the flag in {@code args}
   * @param args the command-line arguments
   * @param flagName the flag name, used in the error message
   * @param scope the JVM scope
   * @param out the output stream
   * @return the parsed integer value, or empty if the value is missing or not an integer
   */
  public static OptionalInt optionalInt(int flagIndex, String[] args, String flagName, JvmScope scope,
    PrintStream out)
  {
    try
    {
      requiredValue(flagIndex, args, flagName);
      return OptionalInt.of(requiredInt(flagName, args[flagIndex + 1]));
    }
    catch (IllegalArgumentException e)
    {
      out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      return OptionalInt.empty();
    }
  }

  /**
   * Reads a string flag value, writing a block response and returning empty if the value is missing.
   *
   * @param flagIndex the index of the flag in {@code args}
   * @param args the command-line arguments
   * @param flagName the flag name, used in the error message
   * @param scope the JVM scope
   * @param out the output stream
   * @return the flag value, or empty if the value is missing
   */
  public static Optional<String> optionalString(int flagIndex, String[] args, String flagName,
    JvmScope scope, PrintStream out)
  {
    try
    {
      requiredValue(flagIndex, args, flagName);
      return Optional.of(args[flagIndex + 1]);
    }
    catch (IllegalArgumentException e)
    {
      out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      return Optional.empty();
    }
  }
}
