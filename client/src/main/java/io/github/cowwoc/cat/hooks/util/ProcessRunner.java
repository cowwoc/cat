/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * Utility class for running external processes and capturing output.
 * <p>
 * Provides methods for executing processes and reading their output
 * without triggering PMD's AssignmentInOperand warning.
 */
public final class ProcessRunner
{
  /**
   * Result of running a process.
   *
   * @param exitCode the process exit code
   * @param stdout the standard output
   */
  public record Result(int exitCode, String stdout)
  {
    /**
     * Creates a new process result.
     *
     * @param exitCode the process exit code
     * @param stdout the standard output
     * @throws NullPointerException if {@code stdout} is null
     */
    public Result
    {
      requireThat(stdout, "stdout").isNotNull();
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private ProcessRunner()
  {
  }

  /**
   * Runs a command and returns the exit code and stdout.
   *
   * @param command the command and arguments to run
   * @return the result with exit code and stdout
   */
  public static Result run(String... command)
  {
    return run(null, command);
  }

  /**
   * Runs a command in a specific working directory and returns the exit code and stdout.
   *
   * @param workingDirectory the working directory for the process, or {@code null} to inherit the JVM's
   *                         working directory
   * @param command          the command and arguments to run
   * @return the result with exit code and stdout
   */
  public static Result run(Path workingDirectory, String... command)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command);
      if (workingDirectory != null)
        pb.directory(workingDirectory.toFile());
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String output;
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        output = readAllLines(reader);
      }

      int exitCode = process.waitFor();
      return new Result(exitCode, output);
    }
    catch (IOException | InterruptedException _)
    {
      return new Result(1, "");
    }
  }

  /**
   * Runs a command and returns the first line of output, or {@code null} on error or non-zero exit code.
   * <p>
   * Use this when only the first line of output is needed, to avoid reading unnecessary data.
   *
   * @param command the command and arguments to run
   * @return the first line of output, or {@code null} if the command fails, exits non-zero, or produces no
   *   output
   */
  public static String runAndCaptureFirstLine(List<String> command)
  {
    Process process = null;
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      process = pb.start();

      String firstLine;
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        firstLine = reader.readLine();
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        return null;
      return firstLine;
    }
    catch (InterruptedException _)
    {
      if (process != null)
        process.destroyForcibly();
      Thread.currentThread().interrupt();
      return null;
    }
    catch (IOException _)
    {
      return null;
    }
  }

  /**
   * Reads all lines from a reader and returns them joined by newlines.
   *
   * @param reader the reader to read from
   * @return the lines joined by newlines, or an empty string if no lines were read
   * @throws NullPointerException if {@code reader} is null
   * @throws IOException if reading fails
   */
  public static String readAllLines(BufferedReader reader) throws IOException
  {
    StringJoiner joiner = new StringJoiner("\n");
    String line = reader.readLine();
    while (line != null)
    {
      joiner.add(line);
      line = reader.readLine();
    }
    return joiner.toString();
  }
}
