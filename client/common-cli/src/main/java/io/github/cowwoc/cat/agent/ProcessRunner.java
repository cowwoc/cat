/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
   * @param output combined stdout and stderr output
   */
  public record Result(int exitCode, String output)
  {
    /**
     * Creates a new process result.
     *
     * @param exitCode the process exit code
     * @param output combined stdout and stderr output
     * @throws NullPointerException if {@code output} is null
     */
    public Result
    {
      requireThat(output, "output").isNotNull();
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private ProcessRunner()
  {
  }

  /**
   * Runs a command and returns the exit code and combined output.
   *
   * @param command the command and arguments to run
   * @return the result with exit code and combined output
   */
  public static Result run(String... command)
  {
    return run(null, command);
  }

  /**
   * Runs a command in a specific working directory and returns the exit code and combined output.
   *
   * @param workingDirectory the working directory for the process, or {@code null} to inherit the JVM's
   *                         working directory
   * @param command          the command and arguments to run
   * @return the result with exit code and combined output
   */
  public static Result run(Path workingDirectory, String... command)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
      if (workingDirectory != null)
        pb.directory(workingDirectory.toFile());
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
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      return failureResult(e);
    }
    catch (IOException e)
    {
      return failureResult(e);
    }
  }

  /**
   * Runs a command with additional environment variables.
   *
   * @param workingDirectory the working directory for the process, or {@code null} to inherit the JVM's
   *                         working directory
   * @param environment the environment variables to add or override
   * @param command the command and arguments to run
   * @return the result with exit code and combined output
   */
  public static Result runWithEnvironment(Path workingDirectory, Map<String, String> environment,
    String... command)
  {
    requireThat(environment, "environment").isNotNull();
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
      if (workingDirectory != null)
        pb.directory(workingDirectory.toFile());
      pb.environment().putAll(environment);
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
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      return failureResult(e);
    }
    catch (IOException e)
    {
      return failureResult(e);
    }
  }

  private static Result failureResult(Exception exception)
  {
    String message = exception.getClass().getSimpleName();
    if (exception.getMessage() != null && !exception.getMessage().isBlank())
      message += ": " + exception.getMessage();
    return new Result(1, message);
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
    return runAndCaptureFirstLine(null, command);
  }

  /**
   * Runs a command in a specific working directory and returns the first line of output, or
   * {@code null} on error or non-zero exit code.
   * <p>
   * Use this when only the first line of output is needed, to avoid reading unnecessary data.
   *
   * @param workingDirectory the working directory for the process, or {@code null} to inherit the JVM's
   *                         working directory
   * @param command the command and arguments to run
   * @return the first line of output, or {@code null} if the command fails, exits non-zero, or produces no
   *   output
   */
  public static String runAndCaptureFirstLine(Path workingDirectory, List<String> command)
  {
    Process process = null;
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
      if (workingDirectory != null)
        pb.directory(workingDirectory.toFile());
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
